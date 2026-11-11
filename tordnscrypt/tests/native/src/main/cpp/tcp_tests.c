#include "test_support.h"

void test_tcp(const struct arguments *args) {
    for (int doff = 0; doff <= 15; doff++) {
        uint8_t *pkt = calloc(1, 40);
        pkt[0] = 0x45;
        struct tcphdr *tcp = (void *) (pkt + 20);
        tcp->syn = 1; tcp->doff = doff;
        handle_tcp(args, pkt, 40, pkt + 20, own_uid, 0, NULL, -1);
        free(pkt);
        assert(allocations == 0);
    }
    uint8_t *pkt = calloc(1, 44);
    pkt[0] = 0x45;
    struct tcphdr *tcp = (void *) (pkt + 20);
    tcp->syn = 1; tcp->doff = 5;
    // Payload on a rejected SYN must release both segment allocations.
    handle_tcp(args, pkt, 44, pkt + 20, own_uid, 0, NULL, -1);
    assert(allocations == 0);
    failure = PROTECT;
    handle_tcp(args, pkt, 44, pkt + 20, own_uid, 1, NULL, -1);
    assert(allocations == 0 && sockets == 0);
    failure = NONE;
    tcp->doff = 6;
    const uint8_t options[][4] = {
        {1, 1, 1, 1}, {0, 0, 0, 0}, {2, 0, 0, 0}, {2, 1, 0, 0},
        {2, 5, 0, 0}, {1, 1, 1, 2}, {2, 4, 5, 180}, {3, 3, 255, 0}
    };
    for (size_t i = 0; i < sizeof(options) / sizeof(*options); i++) {
        memcpy(pkt + 40, options[i], 4);
        handle_tcp(args, pkt, 44, pkt + 20, own_uid, 0, NULL, -1);
        assert(allocations == 0);
    }
    free(pkt);
}

void test_tcp_socket_cleanup(const struct arguments *args) {
    struct tcp_session session = {.version = 4, .uid = own_uid};
    for (failure = PROTECT; failure <= CONNECT; failure++) {
        assert(open_tcp_socket(args, &session, NULL) == -1);
        assert(sockets == 0);
    }
    failure = NONE;
    int fd = open_tcp_socket(args, &session, NULL);
    assert(fd >= 0 && sockets == 1);
    close(fd);
    assert(sockets == 0);
}

void test_tcp_data_cleanup(void) {
    struct tcp_session tcp = {0};
    tcp.forward = ng_calloc(1, sizeof(*tcp.forward), "test segment");
    tcp.forward->data = ng_malloc(4, "test payload");
    clear_tcp_data(&tcp);
    clear_tcp_data(&tcp);
    assert(tcp.forward == NULL && allocations == 0);

}

void test_tcp_closed_data_cleanup(const struct arguments *args) {
    for (int closing = 0; closing <= 1; closing++) {
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = -1};
        s.tcp.version = 4;
        s.tcp.time = time(NULL);
        s.tcp.state = (closing ? TCP_CLOSING : TCP_CLOSE);
        s.tcp.sent = 10;
        s.tcp.received = 20;
        if (closing)
            s.socket = socket(PF_INET, SOCK_STREAM, 0);
        s.tcp.forward = ng_calloc(1, sizeof(*s.tcp.forward), "test segment");
        s.tcp.forward->data = ng_malloc(65535, "test queued payload");
        s.tcp.forward->len = 65535;
        assert(check_tcp_session(args, &s, 1, 100) == 0);
        assert(s.tcp.state == TCP_CLOSE && s.socket == -1);
        assert(s.tcp.forward == NULL && allocations == 0 && sockets == 0);
        assert(s.tcp.sent == 0 && s.tcp.received == 0);
        assert(check_tcp_session(args, &s, 1, 100) == 0); // Metadata still handles late packets.
        clear_tcp_data(&s.tcp); // Later session destruction remains safe.
    }
    assert(accounted_sent == 20 && accounted_received == 40);
}

static size_t tcp_packet(uint8_t *packet, int version, uint32_t seq, uint32_t ack,
                          int syn, int fin, const uint8_t *data, size_t len) {
    size_t optlen = (syn ? 8 : 0);
    size_t off = ip_header(packet, version, IPPROTO_TCP, 20 + optlen + len,
                           version == 4 ? "203.0.113.2" : "2001:db8::2");
    struct tcphdr *tcp = (void *) (packet + off);
    tcp->source = htons(12345); tcp->dest = htons(443);
    tcp->seq = htonl(seq); tcp->ack_seq = htonl(ack);
    tcp->syn = syn; tcp->fin = fin; tcp->ack = !syn; tcp->psh = len != 0;
    tcp->window = htons(16000); tcp->doff = (20 + optlen) / 4;
    if (syn) {
        const uint8_t options[] = {2, 4, 4, 176, 3, 3, 2, 0}; // MSS 1200, scale 2.
        memcpy(packet + off + 20, options, sizeof(options));
    }
    if (len != 0)
        memcpy(packet + off + 20 + optlen, data, len);
    return off + 20 + optlen + len;
}

static void tcp_input(const struct arguments *args, uint8_t *packet, size_t len, int version) {
    assert(handle_tcp(args, packet, len, packet + (version == 4 ? 20 : 40),
                      own_uid, 1, NULL, -1) == 1);
}

void tcp_flow(int version) {
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx};
    int tunnel[2], stream[2];
    assert(pipe(tunnel) == 0);
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    args.tun = tunnel[1];
    _Alignas(8) uint8_t packet[2048], output[2048], received[64];
    size_t off = (version == 4 ? 20 : 40);
    size_t len = tcp_packet(packet, version, 1000, 0, 1, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    struct ng_session *s = ctx.ng_session;
    assert(s != NULL && s->tcp.state == TCP_LISTEN && s->tcp.mss == 1200);
    assert(s->tcp.send_scale == 2 && s->tcp.send_window == 64000);
    tcp_input(&args, packet, len, version); // Repeated SYN must reuse the session.
    assert(ctx.ng_session == s && s->next == NULL);
    close(s->socket); s->socket = stream[0];
    struct epoll_event ev = {.events = EPOLLOUT, .data.ptr = s};
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    const struct tcphdr *reply = (const void *) (output + off);
    assert(reply->syn && reply->ack && ntohl(reply->ack_seq) == 1001);
    assert(reply->source == htons(443) && reply->dest == htons(12345));
    assert(s->tcp.state == TCP_SYN_RECV);
    printf("TCP%d SYNACK %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    assert(s->tcp.state == TCP_ESTABLISHED);
    const uint8_t request[] = {'a', 0, 'b', 255, 'c', 'd'};
    uint32_t seq = s->tcp.remote_seq;
    // Queue the later half first, then deliver the missing earlier half.
    len = tcp_packet(packet, version, seq + 3, s->tcp.local_seq, 0, 0, request + 3, 3);
    tcp_input(&args, packet, len, version);
    len = tcp_packet(packet, version, seq, s->tcp.local_seq, 0, 0, request, 3);
    tcp_input(&args, packet, len, version);
    check_tcp_socket(&args, &ev, -1);
    readable(stream[1]);
    assert(recv(stream[1], received, sizeof(received), 0) == sizeof(request));
    assert(memcmp(received, request, sizeof(request)) == 0);
    assert(s->tcp.forward == NULL && s->tcp.remote_seq == seq + sizeof(request));
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->ack && ntohl(reply->ack_seq) == seq + sizeof(request));
    printf("TCP%d ACK %08x\n", version, fingerprint(output, len));
    const uint8_t response[] = {5, 4, 0, 3, 2, 1};
    assert(send(stream[1], response, sizeof(response), 0) == sizeof(response));
    ev.events = EPOLLIN;
    readable(s->socket);
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->ack && len == off + 20 + sizeof(response));
    assert(memcmp(output + off + 20, response, sizeof(response)) == 0);
    assert(s->tcp.sent == sizeof(request) && s->tcp.received == sizeof(response));
    printf("TCP%d DATA %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    close(stream[1]);
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->fin && reply->ack && s->tcp.state == TCP_FIN_WAIT1);
    printf("TCP%d FIN %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 1, NULL, 0);
    tcp_input(&args, packet, len, version);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s->tcp.state == TCP_CLOSE);
    printf("TCP%d CLOSED %08x\n", version, fingerprint(output, len));
    clear(&ctx);
    close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
    cases++;
}

void socks_flow(int version, int authentication, int max_credentials) {
    JNIEnv env = &jni;
    char user[128], password[128];
    if (max_credentials) {
        memset(user, 'u', 127); user[127] = 0;
        memset(password, 'p', 127); password[127] = 0;
    } else {
        strcpy(user, "us\xC3\xA9r"); strcpy(password, "secret");
    }
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "127.0.0.1", 1080, (jstring) user, (jstring) password);
    assert(strcmp(proxy_socks5_username, user) == 0 && strcmp(proxy_socks5_password, password) == 0);
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx};
    struct ng_session s = {.protocol = IPPROTO_TCP};
    s.tcp.version = version; s.tcp.uid = own_uid; s.tcp.state = TCP_LISTEN;
    s.tcp.socks5 = SOCKS5_HELLO; s.tcp.source = htons(12345); s.tcp.dest = htons(443);
    s.tcp.local_seq = 2000; s.tcp.remote_seq = 1000; s.tcp.recv_window = 16000;
    if (version == 4) {
        inet_pton(AF_INET, "10.0.0.2", &s.tcp.saddr.ip4);
        inet_pton(AF_INET, "203.0.113.2", &s.tcp.daddr.ip4);
    } else {
        inet_pton(AF_INET6, "2001:db8::1", &s.tcp.saddr.ip6);
        inet_pton(AF_INET6, "2001:db8::2", &s.tcp.daddr.ip6);
    }
    int tunnel[2], stream[2];
    assert(pipe(tunnel) == 0 && socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    args.tun = tunnel[1]; s.socket = stream[0];
    struct epoll_event ev = {.events = EPOLLIN, .data.ptr = &s};
    uint8_t reply[] = {5, authentication ? 2 : 0};
    assert(send(stream[1], reply, sizeof(reply), 0) == sizeof(reply));
    check_tcp_socket(&args, &ev, -1);
    uint8_t wire[512];
    readable(stream[1]);
    ssize_t len = recv(stream[1], wire, sizeof(wire), 0);
    if (authentication) {
        size_t ulen = strlen(user), plen = strlen(password);
        assert(s.tcp.socks5 == SOCKS5_AUTH && len == 3 + ulen + plen);
        assert(wire[0] == 1 && wire[1] == ulen && memcmp(wire + 2, user, ulen) == 0);
        assert(wire[ulen + 2] == plen && memcmp(wire + ulen + 3, password, plen) == 0);
        printf("SOCKS%d AUTH %08x\n", version, fingerprint(wire, len));
        const uint8_t auth_ok[] = {1, 0};
        assert(send(stream[1], auth_ok, sizeof(auth_ok), 0) == sizeof(auth_ok));
        check_tcp_socket(&args, &ev, -1);
        readable(stream[1]);
        len = recv(stream[1], wire, sizeof(wire), 0);
    }
    assert(s.tcp.socks5 == SOCKS5_CONNECT && len == (version == 4 ? 10 : 22));
    assert(wire[0] == 5 && wire[1] == 1 && wire[3] == (version == 4 ? 1 : 4));
    assert(load16(wire + len - 2) == 443);
    printf("SOCKS%d CONNECT %08x\n", version, fingerprint(wire, len));
    memset(wire, 0, len); wire[0] = 5; wire[3] = (version == 4 ? 1 : 4);
    assert(send(stream[1], wire, len, 0) == len);
    check_tcp_socket(&args, &ev, -1);
    _Alignas(8) uint8_t output[2048];
    size_t out_len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s.tcp.state == TCP_SYN_RECV && s.tcp.socks5 == SOCKS5_CONNECTED);
    printf("SOCKS%d SYNACK %08x\n", version, fingerprint(output, out_len));
    close(stream[0]); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "", 0, (jstring) "", (jstring) "");
    assert(string_refs == 0 && exceptions == 0 && allocations == 0);
    cases++;
}
