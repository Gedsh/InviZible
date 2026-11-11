#include "test_support.h"

void test_udp_allocation_failure(const struct arguments *args) {
    uint8_t packet[48];
    for (int version = 4; version <= 6; version += 2) {
        size_t off = ip_header(packet, version, IPPROTO_UDP, sizeof(struct udphdr),
                               version == 4 ? "198.51.100.1" : "2001:db8::2");
        struct udphdr *header = (void *) (packet + off);
        header->source = htons(1234);
        header->dest = htons(5000);
        header->len = htons(sizeof(*header));
        fail_alloc_tag = "udp session";
        fail_alloc_match = 1;
        assert(handle_udp(args, packet, off + sizeof(*header), packet + off, own_uid, NULL, -1) == 0);
        assert(fail_alloc_match == 0);
        assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
        struct ng_session s = {.protocol = IPPROTO_UDP, .socket = -1};
        s.udp.version = version;
        s.udp.state = UDP_ACTIVE;
        s.udp.mss = 1500;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        fail_alloc_tag = "udp recv";
        fail_alloc_match = 1;
        check_udp_socket(args, &event);
        assert(fail_alloc_match == 0);
        assert(s.udp.state == UDP_FINISHING);
        fail_alloc_tag = version == 4 ? "udp write4" : "udp write6";
        fail_alloc_match = 1;
        real_udp_packets = 1;
        assert(write_udp(args, &s.udp, packet, 1) == -1);
        assert(fail_alloc_match == 0);
        real_udp_packets = 0;
        assert(allocations == 0);
    }
    fail_alloc_tag = NULL;
}

void test_udp_short_dns(struct arguments *args) {
    uint8_t *pkt = calloc(1, 28);
    pkt[0] = 0x45;
    struct udphdr *udp = (void *) (pkt + 20);
    udp->source = htons(1234); udp->dest = htons(53);
    assert(handle_udp(args, pkt, 28, pkt + 20, own_uid, NULL, -1) == 0);
    assert(args->ctx->ng_session == NULL && allocations == 0);
    struct ng_session s = {.protocol = IPPROTO_UDP, .socket = 0};
    s.udp.version = 4; s.udp.source = udp->source; s.udp.dest = udp->dest;
    args->ctx->ng_session = &s;
    assert(handle_udp(args, pkt, 28, pkt + 20, own_uid, NULL, -1) == 0);
    args->ctx->ng_session = NULL;
    free(pkt);
}

void test_udp_socket_cleanup(const struct arguments *args) {
    struct udp_session session = {.version = 4, .uid = own_uid};
    for (failure = PROTECT; failure <= SETFL; failure++) {
        assert(open_udp_socket(args, &session, NULL) == -1);
        assert(sockets == 0);
    }
    failure = NONE;
    int fd = open_udp_socket(args, &session, NULL);
    assert(fd >= 0 && sockets == 1);
    close(fd);
    assert(sockets == 0);
}

static size_t blocked_packet(uint8_t *packet, int version, uint16_t source, uint16_t dest) {
    size_t off = ip_header(packet, version, IPPROTO_UDP, sizeof(struct udphdr),
                           version == 4 ? "198.51.100.1" : "2001:db8::2");
    struct udphdr *udp = (void *) (packet + off);
    udp->source = htons(source);
    udp->dest = htons(dest);
    udp->len = htons(sizeof(*udp));
    return off;
}

void test_udp_blocked_limit(struct arguments *args, int version) {
    struct ng_session *active = ng_calloc(1, sizeof(*active), "test active UDP");
    active->protocol = IPPROTO_UDP;
    active->udp.state = UDP_ACTIVE;
    active->udp.uid = 12345;
    active->socket = socket(AF_INET, SOCK_DGRAM, 0);
    struct ng_session *closed = ng_calloc(1, sizeof(*closed), "test closed UDP");
    closed->protocol = IPPROTO_UDP;
    closed->udp.state = UDP_CLOSED;
    closed->socket = -1;
    active->next = closed;
    args->ctx->ng_session = active;

    uint8_t packet[48];
    for (int i = 0; i < UDP_BLOCKED_MAX + 50; i++) {
        size_t off = blocked_packet(packet, version, 1000 + i, 5000);
        block_udp(args, packet, off + sizeof(struct udphdr), packet + off, own_uid);
        assert(allocations <= UDP_BLOCKED_MAX + 2 && sockets == 1);
    }

    int blocked = 0, active_seen = 0, closed_seen = 0;
    for (struct ng_session *s = args->ctx->ng_session; s != NULL; s = s->next) {
        if (s == active)
            active_seen++;
        else if (s == closed)
            closed_seen++;
        else {
            assert(s->protocol == IPPROTO_UDP && s->udp.state == UDP_BLOCKED && s->socket == -1);
            blocked++;
        }
    }
    assert(blocked == UDP_BLOCKED_MAX && active_seen == 1 && closed_seen == 1);
    assert(active->udp.state == UDP_ACTIVE && active->udp.uid == 12345);
    assert(active->socket == last_socket && closed->udp.state == UDP_CLOSED);
    size_t off = blocked_packet(packet, version, 1000, 5000);
    assert(!has_udp_session(args, packet, packet + off)); // Oldest blocked records are evicted.
    off = blocked_packet(packet, version, 1000 + UDP_BLOCKED_MAX + 49, 5000);
    assert(has_udp_session(args, packet, packet + off));
    int before = allocations;
    block_udp(args, packet, off + sizeof(struct udphdr), packet + off, 54321);
    assert(allocations == before && args->ctx->ng_session->udp.uid == 54321);
    clear(args->ctx);
    assert(allocations == 0 && sockets == 0);
}

void test_udp_blocked_duplicates(struct arguments *args) {
    uint8_t packet[48];
    size_t off = blocked_packet(packet, 4, 1234, 53);
    // Forwarded DNS is rechecked against policy on each packet, even for an existing flow.
    for (int i = 0; i < 100; i++) {
        block_udp(args, packet, off + sizeof(struct udphdr), packet + off, i);
        assert(allocations == 1 && args->ctx->ng_session->next == NULL);
        assert(args->ctx->ng_session->udp.uid == i);
    }
    clear(args->ctx);
    assert(allocations == 0);
}

static int udp_server(int version, uint16_t *port) {
    int family = (version == 4 ? AF_INET : AF_INET6);
    int fd = __real_socket(family, SOCK_DGRAM | SOCK_NONBLOCK, IPPROTO_UDP);
    assert(fd >= 0);
    struct sockaddr_in addr4 = {.sin_family = AF_INET};
    struct sockaddr_in6 addr6 = {.sin6_family = AF_INET6};
    assert(inet_pton(AF_INET, "127.0.0.1", &addr4.sin_addr) == 1);
    assert(inet_pton(AF_INET6, "::1", &addr6.sin6_addr) == 1);
    struct sockaddr *addr = (version == 4 ? (void *) &addr4 : (void *) &addr6);
    socklen_t len = (version == 4 ? sizeof(addr4) : sizeof(addr6));
    assert(bind(fd, addr, len) == 0);
    assert(getsockname(fd, addr, &len) == 0);
    *port = ntohs(version == 4 ? addr4.sin_port : addr6.sin6_port);
    return fd;
}

void udp_flow(int version, int redirect, int extension) {
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .fwd53 = 1};
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    args.tun = tunnel[1];
    int epoll_fd = epoll_create1(0);
    assert(epoll_fd >= 0);
    uint16_t port;
    int server = udp_server(version, &port);
    // Establish that the host can exchange datagrams before testing app code.
    struct sockaddr_storage loopback;
    socklen_t loopback_len = sizeof(loopback);
    assert(getsockname(server, (void *) &loopback, &loopback_len) == 0);
    const uint8_t probe[] = {42};
    int probe_ready = 0;
    for (int attempt = 0; attempt < 120 && !probe_ready; attempt++) {
        if (attempt == 0)
            fprintf(stderr, "Waiting up to 120 seconds for firewall allowance: UDP%d probe\n", version);
        assert(sendto(server, probe, sizeof(probe), 0, (void *) &loopback, loopback_len) == sizeof(probe));
        struct pollfd probe_fd = {.fd = server, .events = POLLIN};
        probe_ready = poll(&probe_fd, 1, 1000);
        assert(probe_ready >= 0);
    }
    assert(probe_ready == 1);
    readable(server);
    uint8_t probe_reply;
    assert(recv(server, &probe_reply, 1, 0) == 1 && probe_reply == 42);
    real_network = real_udp_packets = 1;
    const uint8_t request[] = {0, 1, 2, 3, 255, 128, 42, 0};
    const uint8_t response[] = {9, 0, 8, 7, 6, 255};
    _Alignas(8) uint8_t packet[2048], output[2048], received[64];
    const char *destination = (version == 4 ?
                              (redirect ? "203.0.113.2" : "127.0.0.1") :
                              (redirect ? "2001:db8::2" : "::1"));
    size_t off = ip_header(packet, version, IPPROTO_UDP, 8 + sizeof(request), destination);
    struct udphdr *udp = (void *) (packet + off);
    udp->source = htons(12345); udp->dest = htons(redirect ? 443 : port);
    udp->len = htons(8 + sizeof(request));
    memcpy(packet + off + 8, request, sizeof(request));
    size_t len = off + 8 + sizeof(request);
    if (extension) {
        size_t extra = (version == 4 ? 4 : 8);
        memmove(packet + off + extra, packet + off, len - off);
        memset(packet + off, 0, extra);
        if (version == 4) {
            struct iphdr *ip = (void *) packet;
            ip->ihl = 6; ip->tot_len = htons(len + extra);
            packet[off] = 1; // NOP, then EOL/padding.
        } else {
            struct ip6_hdr *ip = (void *) packet;
            ip->ip6_nxt = IPPROTO_HOPOPTS; ip->ip6_plen = htons(len + extra - 40);
            packet[off] = IPPROTO_UDP;
        }
        off += extra; len += extra;
    }
    struct allowed target = {.rport = port};
    strcpy(target.raddr, "127.0.0.1"); // IPv6 sessions must map this to ::1.
    struct ng_session *session = NULL;
    for (int i = 0; i < 2; i++) {
        if (redirect)
            assert(handle_udp(&args, packet, len, packet + off, own_uid, &target, epoll_fd) == 1);
        else
            handle_ip(&args, packet, len, epoll_fd, 0, 100);
        assert(ctx.ng_session != NULL);
        if (i == 0)
            session = ctx.ng_session;
        assert(ctx.ng_session == session && session->next == NULL);
        if (session->udp.sent != (i + 1) * sizeof(request))
            fprintf(stderr, "UDP%d redirect=%d extension=%d iteration=%d state=%d socket=%d sent=%llu dest=%u\n",
                    version, redirect, extension, i, session->udp.state, session->socket,
                    (unsigned long long) session->udp.sent, ntohs(session->udp.dest));
        assert(session->udp.sent == (i + 1) * sizeof(request));
        struct sockaddr_storage sender;
        socklen_t sender_len = sizeof(sender);
        readable(server);
        assert(recvfrom(server, received, sizeof(received), 0,
                        (void *) &sender, &sender_len) == sizeof(request));
        assert(memcmp(received, request, sizeof(request)) == 0);
        if (i == 1) {
            assert(sendto(server, response, sizeof(response), 0,
                          (void *) &sender, sender_len) == sizeof(response));
        }
    }
    readable(session->socket);
    struct epoll_event ev = {.events = EPOLLIN, .data.ptr = session};
    check_udp_socket(&args, &ev);
    size_t out_len = tun_packet(tunnel[0], output, version, IPPROTO_UDP);
    size_t out_off = (version == 4 ? 20 : 40);
    const struct udphdr *reply = (const void *) (output + out_off);
    assert(out_len == out_off + 8 + sizeof(response));
    assert(reply->source == htons(redirect ? 443 : port) && reply->dest == htons(12345));
    assert(ntohs(reply->len) == 8 + sizeof(response));
    assert(memcmp(output + out_off + 8, response, sizeof(response)) == 0);
    assert(session->udp.sent == 2 * sizeof(request) && session->udp.received == sizeof(response));
    assert(check_udp_session(&args, session, 1, 100) == 0 && session->udp.state == UDP_ACTIVE);
    uint64_t before = accounted_sent;
    session->udp.state = UDP_FINISHING;
    assert(check_udp_session(&args, session, 1, 100) == 0);
    assert(session->udp.state == UDP_CLOSED && session->socket == -1);
    assert(accounted_sent - before == 2 * sizeof(request));
    session->udp.time = time(NULL) - UDP_KEEP_TIMEOUT - 1;
    assert(check_udp_session(&args, session, 1, 100) == 1);
    printf("UDP%d redirect=%d extension=%d tx=%zu rx=%zu payload=%08x\n", version,
           redirect, extension, 2 * sizeof(request), sizeof(response), fingerprint(response, sizeof(response)));
    clear(&ctx);
    close(server); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    real_network = real_udp_packets = 0;
    assert(allocations == 0 && sockets == 0);
    cases++;
}
