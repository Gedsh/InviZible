#include "test_support.h"

ssize_t __real_sendmsg(int, const struct msghdr *, int);
static int fail_relay_send;
static int relay_send_error = EHOSTUNREACH;
static int last_relay_errno;
ssize_t __wrap_sendmsg(int fd, const struct msghdr *message, int flags) {
    if (fail_relay_send > 0 && --fail_relay_send == 0) {
        errno = relay_send_error;
        return -1;
    }
    ssize_t result = __real_sendmsg(fd, message, flags);
    last_relay_errno = result < 0 ? errno : 0;
    return result;
}

#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
#include <linux/errqueue.h>
extern char proxy_socks5_addr[], proxy_socks5_username[], proxy_socks5_password[];
extern int proxy_socks5_port;
int __real_socket(int, int, int);
static struct allowed proxy_route = {.udp_proxy = true};
static struct {
    int fd, version, remaining, calls, mode, error;
    uint32_t mtu;
    uint8_t wire[134];
    size_t length;
} mtu_error;

static ssize_t receive_mtu_error(int fd, struct msghdr *message, int flags) {
    assert(fd == mtu_error.fd && (flags & MSG_DONTWAIT));
    mtu_error.calls++;
    if (mtu_error.remaining == 0) { errno = EAGAIN; return -1; }
    mtu_error.remaining--;
    assert(message->msg_iov[0].iov_len >= mtu_error.length);
    memcpy(message->msg_iov[0].iov_base, mtu_error.wire, mtu_error.length);
    assert(message->msg_controllen >= CMSG_SPACE(sizeof(struct sock_extended_err)));
    message->msg_controllen = CMSG_SPACE(sizeof(struct sock_extended_err));
    struct cmsghdr *cmsg = CMSG_FIRSTHDR(message);
    cmsg->cmsg_level = mtu_error.version == 4 ? IPPROTO_IP : IPPROTO_IPV6;
    cmsg->cmsg_type = mtu_error.version == 4 ? IP_RECVERR : IPV6_RECVERR;
    cmsg->cmsg_len = CMSG_LEN(sizeof(struct sock_extended_err)) - (mtu_error.mode == 2 ? 1 : 0);
    struct sock_extended_err error = {.ee_errno = (uint32_t) mtu_error.error, .ee_info = mtu_error.mtu,
            .ee_origin = mtu_error.mode == 3 ? SO_EE_ORIGIN_TIMESTAMPING :
                         mtu_error.mode == 4 ? SO_EE_ORIGIN_LOCAL :
                         mtu_error.version == 4 ? SO_EE_ORIGIN_ICMP : SO_EE_ORIGIN_ICMP6};
    memcpy(CMSG_DATA(cmsg), &error, sizeof(error));
    message->msg_flags = mtu_error.mode == 1 ? MSG_CTRUNC : MSG_TRUNC;
    return (ssize_t) mtu_error.length;
}

static void queue_mtu(int fd, int version, const uint8_t *wire, size_t length, uint32_t mtu) {
    memset(&mtu_error, 0, sizeof(mtu_error));
    mtu_error.fd = fd; mtu_error.version = version; mtu_error.mtu = mtu; mtu_error.remaining = 1;
    mtu_error.error = EMSGSIZE;
    mtu_error.length = length < sizeof(mtu_error.wire) ? length : sizeof(mtu_error.wire);
    if (mtu_error.length != 0) memcpy(mtu_error.wire, wire, mtu_error.length);
    error_queue_recv = receive_mtu_error;
}
ssize_t __real_recvmsg(int, struct msghdr *, int);
static ssize_t truncated_relay_receive(int fd, struct msghdr *message, int flags) {
    // Exercise a real kernel MSG_TRUNC without allocating oversized UDP packets.
    packet_recv = NULL;
    size_t capacity = message->msg_iov[0].iov_len;
    message->msg_iov[0].iov_len = 8;
    ssize_t bytes = __real_recvmsg(fd, message, flags);
    message->msg_iov[0].iov_len = capacity;
    assert(bytes == 8 && (message->msg_flags & MSG_TRUNC));
    return bytes;
}

static int batch_receives, batch_error_at, batch_error;
static bool batch_truncate_first;
static ssize_t batch_receive(int fd, struct msghdr *message, int flags) {
    assert(message->msg_flags == 0 && message->msg_iovlen == 1);
    batch_receives++;
    if (batch_receives == batch_error_at) { errno = batch_error; return -1; }
    size_t capacity = message->msg_iov[0].iov_len;
    if (batch_truncate_first && batch_receives == 1) message->msg_iov[0].iov_len = 8;
    ssize_t bytes = __real_recvmsg(fd, message, flags);
    message->msg_iov[0].iov_len = capacity;
    return bytes;
}

static void batch_reply(int tunnel, int version, int value, bool empty) {
    uint8_t packet[128], expected[128];
    size_t off = version == 4 ? 20 : 40;
    size_t length = tun_packet(tunnel, packet, version, IPPROTO_UDP);
    assert(length == off + 8 + (empty ? 0 : 1));
    assert(load16(packet + off) == 5000 && load16(packet + off + 2) == 12345);
    if (!empty) assert(packet[off + 8] == value);
    memcpy(expected, packet, length);
    packet_checksums(expected, length, off, IPPROTO_UDP);
    assert(memcmp(expected, packet, length) == 0);
}

static void proxy_batch_checks(const struct arguments *args, struct ng_session *s, int relay,
                               const struct sockaddr_storage *client, socklen_t client_length,
                               int tunnel, int epoll_fd, bool output_failure) {
    int version = s->udp.version;
    size_t framing = version == 4 ? 10 : 22;
    uint8_t wire[32] = {0}; wire[3] = version == 4 ? 1 : 4;
    memcpy(wire + 4, &s->udp.daddr, version == 4 ? 4 : 16);
    memcpy(wire + framing - 2, &s->udp.dest, 2);
    struct epoll_event event = {.events = EPOLLIN, .data.ptr = s};
    uint64_t received = s->udp.received;
    batch_error_at = 0; batch_truncate_first = false; batch_receives = 0;
    packet_recv = batch_receive;
    for (int i = 0; i < 10; i++) {
        wire[framing] = (uint8_t) i;
        size_t length = framing + (i == 3 ? 0 : 1);
        assert(sendto(relay, wire, length, 0, (const struct sockaddr *) client, client_length) == length);
    }
    readable(s->socket); check_udp_socket(args, &event);
    assert(batch_receives == 8 && is_readable(s->socket));
    for (int i = 0; i < 8; i++) batch_reply(tunnel, version, i, i == 3);
    assert(!is_readable(tunnel) && s->udp.received == received + 7);
    assert(epoll_wait(epoll_fd, &event, 1, 1000) == 1);
    assert(event.data.ptr == s && (event.events & EPOLLIN));
    batch_receives = 0; check_udp_socket(args, &event);
    assert(batch_receives == 3 && !is_readable(s->socket)); // Two datagrams, then EAGAIN.
    batch_reply(tunnel, version, 8, false); batch_reply(tunnel, version, 9, false);
    assert(!is_readable(tunnel) && s->udp.received == received + 9);
    // Malformed frames also consume the batch budget, without starving a valid successor.
    wire[0] = 1;
    for (int i = 0; i < 9; i++)
        assert(sendto(relay, wire, framing + 1, 0, (const struct sockaddr *) client, client_length) == framing + 1);
    wire[0] = 0; wire[framing] = 42;
    assert(sendto(relay, wire, framing + 1, 0, (const struct sockaddr *) client, client_length) == framing + 1);
    readable(s->socket); batch_receives = 0; check_udp_socket(args, &event);
    assert(batch_receives == 8 && !is_readable(tunnel) && is_readable(s->socket));
    batch_receives = 0; check_udp_socket(args, &event);
    assert(batch_receives == 3); batch_reply(tunnel, version, 42, false);
    // A truncated receive must not leave MSG_TRUNC set for the following message.
    for (int i = 0; i < 2; i++)
        assert(sendto(relay, wire, framing + 1, 0, (const struct sockaddr *) client, client_length) == framing + 1);
    readable(s->socket); batch_receives = 0; batch_truncate_first = true;
    check_udp_socket(args, &event);
    assert(batch_receives == 3); batch_reply(tunnel, version, 42, false);
    assert(!is_readable(tunnel)); batch_truncate_first = false;
    const int transient[] = {EAGAIN, EINTR, EMSGSIZE};
    for (size_t i = 0; i < sizeof(transient) / sizeof(transient[0]); i++) {
        for (int j = 0; j < 2; j++)
            assert(sendto(relay, wire, framing + 1, 0, (const struct sockaddr *) client, client_length) == framing + 1);
        readable(s->socket); batch_receives = 0; batch_error_at = 2; batch_error = transient[i];
        check_udp_socket(args, &event);
        assert(batch_receives == 2 && s->udp.state == UDP_ACTIVE && is_readable(s->socket));
        batch_reply(tunnel, version, 42, false); assert(!is_readable(tunnel));
        batch_receives = 0; batch_error_at = 0; check_udp_socket(args, &event);
        assert(batch_receives == 2); batch_reply(tunnel, version, 42, false);
        assert(!is_readable(tunnel));
    }
    // A terminal result mid-batch must close both sockets and stop receiving.
    for (int i = 0; i < 3; i++)
        assert(sendto(relay, wire, framing + 1, 0, (const struct sockaddr *) client, client_length) == framing + 1);
    readable(s->socket); batch_receives = 0;
    struct arguments terminal = *args;
    if (output_failure) terminal.tun = -1;
    else { batch_error_at = 2; batch_error = ECONNREFUSED; }
    check_udp_socket(&terminal, &event);
    assert(batch_receives == (output_failure ? 1 : 2));
    assert(s->udp.state == UDP_FINISHING && s->socket == -1);
    assert(args->ctx->udp_proxy_count == 0 && sockets == 0);
    if (!output_failure) batch_reply(tunnel, version, 42, false);
    int calls = batch_receives; check_udp_socket(args, &event);
    assert(batch_receives == calls);
    packet_recv = NULL; batch_error_at = 0;
}

static int local_socket(int family, int type, struct sockaddr_storage *address, socklen_t *length) {
    int fd = __real_socket(family, type | SOCK_CLOEXEC, 0); assert(fd >= 0);
    memset(address, 0, sizeof(*address));
    if (family == AF_INET) {
        struct sockaddr_in *a = (void *) address;
        a->sin_family = family; a->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        *length = sizeof(*a);
    } else {
        struct sockaddr_in6 *a = (void *) address;
        a->sin6_family = family; a->sin6_addr = in6addr_loopback;
        *length = sizeof(*a);
    }
    assert(bind(fd, (struct sockaddr *) address, *length) == 0);
    assert(getsockname(fd, (struct sockaddr *) address, length) == 0);
    return fd;
}

static void pump_proxy(const struct arguments *args, int epoll_fd) {
    struct epoll_event events[8];
    int count = epoll_wait(epoll_fd, events, 8, 1000); assert(count > 0);
    for (int i = 0; i < count; i++) check_udp_socket(args, &events[i]);
}

static void read_control(int fd, uint8_t *buffer, size_t length) {
    size_t used = 0;
    while (used < length) {
        readable(fd);
        ssize_t bytes = recv(fd, buffer + used, length - used, 0);
        assert(bytes > 0); used += (size_t) bytes;
    }
}

static size_t proxy_packet(uint8_t *packet, int version, uint16_t port) {
    size_t off = ip_header(packet, version, IPPROTO_UDP, 11,
                           version == 4 ? "192.0.2.5" : "2001:db8::5");
    store16(packet + off, port); store16(packet + off + 2, 5000);
    store16(packet + off + 4, 11);
    memcpy(packet + off + 8, "abc", 3);
    packet_checksums(packet, off + 11, off, IPPROTO_UDP);
    return off;
}

static void setup_proxy(int family, bool auth, const struct sockaddr_storage *address) {
    real_network = real_udp_packets = 1;
    mock_uid_routes = uid_route_proxy = true;
    strcpy(proxy_socks5_addr, family == AF_INET ? "127.0.0.1" : "::1");
    proxy_socks5_port = ntohs(family == AF_INET ? ((const struct sockaddr_in *) address)->sin_port :
                                                ((const struct sockaddr_in6 *) address)->sin6_port);
    strcpy(proxy_socks5_username, auth ? "user" : "");
    strcpy(proxy_socks5_password, auth ? "pass" : "");
}

static const char *invalid_proxy_setting(int kind) {
    const char *addresses[] = {"not-an-ip", "999.0.0.1", "::gg", ""};
    const int ports[] = {0, -1, 65536, INT_MAX};
    if (kind < 4) { strcpy(proxy_socks5_addr, addresses[kind]); return "invalid proxy address"; }
    if (kind < 8) { proxy_socks5_port = ports[kind - 4]; return "invalid proxy port"; }
    if (kind == 8) strcpy(proxy_socks5_username, "user");
    else if (kind == 9) strcpy(proxy_socks5_password, "pass");
    else if (kind == 10) memset(proxy_socks5_username, 'u', 128);
    else memset(proxy_socks5_password, 'p', 128);
    return "invalid username/password lengths";
}
#endif

void test_udp_proxy_configuration(const struct arguments *args, int version) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    int family = version == 4 ? AF_INET : AF_INET6;
    struct sockaddr_storage server, relay_address; socklen_t server_length, relay_length;
    int listener = local_socket(family, SOCK_STREAM, &server, &server_length);
    assert(listen(listener, 4) == 0);
    int relay = local_socket(family, SOCK_DGRAM, &relay_address, &relay_length);
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    struct arguments running = *args; running.tun = tunnel[1];
    uint8_t packet[128], buffer[300]; size_t off = proxy_packet(packet, version, 12345);
    for (int bad = 0; bad < 12; bad++) {
        test_monotonic_ms += 1000; // Inspect each independent validation diagnostic.
        setup_proxy(family, false, &server);
        const char *reason = invalid_proxy_setting(bad);
        fail_alloc_tag = "udp proxy"; fail_alloc_match = 1;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(fail_alloc_match == 1); // No association allocation was attempted.
        fail_alloc_tag = NULL;
        assert(strstr(last_proxy_log, reason) != NULL);
        assert(args->ctx->ng_session == NULL && args->ctx->udp_proxy_count == 0);
        assert(allocations == 0 && sockets == 0 && !is_readable(listener));
        while (is_readable(tunnel[0])) assert(recv(tunnel[0], buffer, sizeof(buffer), 0) > 0);
    }
    // Both no-auth and maximum supported credentials still negotiate and send.
    for (int auth = 0; auth < 2; auth++) {
        setup_proxy(family, false, &server);
        if (auth) {
            memset(proxy_socks5_username, 'u', 127); proxy_socks5_username[127] = 0;
            memset(proxy_socks5_password, 'p', 127); proxy_socks5_password[127] = 0;
        }
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        struct ng_session *s = args->ctx->ng_session;
        readable(listener); int peer = accept(listener, NULL, NULL); assert(peer >= 0);
        pump_proxy(&running, epoll_fd); read_control(peer, buffer, 3);
        assert(buffer[0] == 5 && buffer[1] == 1 && buffer[2] == (auth ? 2 : 0));
        uint8_t hello[] = {5, auth ? 2 : 0};
        assert(send(peer, hello, sizeof(hello), MSG_NOSIGNAL) == sizeof(hello));
        pump_proxy(&running, epoll_fd); pump_proxy(&running, epoll_fd);
        if (auth) {
            read_control(peer, buffer, 257);
            assert(buffer[0] == 1 && buffer[1] == 127 && buffer[129] == 127);
            for (int i = 0; i < 127; i++) assert(buffer[2 + i] == 'u' && buffer[130 + i] == 'p');
            const uint8_t accepted[] = {1, 0};
            assert(send(peer, accepted, sizeof(accepted), MSG_NOSIGNAL) == sizeof(accepted));
            pump_proxy(&running, epoll_fd); pump_proxy(&running, epoll_fd);
        }
        read_control(peer, buffer, 10);
        assert(buffer[0] == 5 && buffer[1] == 3);
        uint8_t accepted[22] = {5, 0, 0, version == 4 ? 1 : 4};
        size_t length = version == 4 ? 10 : 22;
        uint16_t port = version == 4 ? ((struct sockaddr_in *) &relay_address)->sin_port :
                                      ((struct sockaddr_in6 *) &relay_address)->sin6_port;
        memcpy(accepted + length - 2, &port, 2);
        assert(send(peer, accepted, length, MSG_NOSIGNAL) == length);
        while (!udp_proxy_ready(&s->udp)) pump_proxy(&running, epoll_fd);
        readable(relay); assert(recv(relay, buffer, sizeof(buffer), 0) == length + 3);
        assert(memcmp(buffer + length, "abc", 3) == 0 && s->udp.sent == 3);
        clear(args->ctx); close(peer);
        assert(allocations == 0 && sockets == 0 && args->ctx->udp_proxy_count == 0);
    }
    close(listener); close(relay); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    test_monotonic_enabled = false;
#endif
}

#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
static void proxy_feedback(int fd, int version, int code, const uint8_t *packet, size_t length) {
    uint8_t reply[2048];
    size_t header = version == 4 ? 20 : 40;
    assert(tun_packet(fd, reply, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6) == header + 8 + length);
    assert(reply[header] == (version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH));
    assert(reply[header + 1] == code);
    assert(memcmp(reply + header + 8, packet, length) == 0);
    assert(!is_readable(fd)); // One failure must produce only one report.
}

static void mtu_feedback(int fd, int version, const uint8_t *packet, uint32_t mtu) {
    uint8_t reply[2048], expected[2048];
    size_t header = version == 4 ? 20 : 40;
    size_t size = tun_packet(fd, reply, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    assert(size == header + 8 + 112);
    assert(reply[header] == (version == 4 ? ICMP_UNREACH : ICMP6_PACKET_TOO_BIG));
    assert(reply[header + 1] == (version == 4 ? ICMP_UNREACH_NEEDFRAG : 0));
    uint32_t reported = load16(reply + header + 6);
    if (version == 6) reported |= (uint32_t) load16(reply + header + 4) << 16;
    assert(reported == mtu);
    assert(memcmp(reply + header + 8, packet, 112) == 0);
    memcpy(expected, reply, size);
    packet_checksums(expected, size, header, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    assert(memcmp(reply, expected, size) == 0 && !is_readable(fd));
}

static void proxy_mtu_checks(const struct arguments *args, struct ng_session *s, int epoll_fd,
                             int relay, int tunnel, int family) {
    int version = s->udp.version;
    uint8_t packet[1600], wire[2048];
    size_t base = version == 4 ? 20 : 40, extra = version == 4 ? 4 : 8;
    size_t off = base + extra, payload = 1500 - off - 8;
    ip_header(packet, version, IPPROTO_UDP, 1500 - base, version == 4 ? "192.0.2.5" : "2001:db8::5");
    memset(packet + base, 0, extra);
    if (version == 4) packet[0] = 0x46;
    else { packet[6] = IPPROTO_HOPOPTS; packet[base] = IPPROTO_UDP; }
    store16(packet + off, 12345); store16(packet + off + 2, 5000); store16(packet + off + 4, (uint16_t) (payload + 8));
    memset(packet + off + 8, 0x5a, payload);
    packet_checksums(packet, 1500, off, IPPROTO_UDP);
    uint32_t expected_mtu = 1400 - (family == AF_INET ? 28u : 48u) - (version == 4 ? 10u : 22u) + (uint32_t) off + 8;
    socket_mtu_fd = socket_error_fd = s->socket;
    socket_mtu_value = 1400; socket_error_value = EMSGSIZE;
    uint64_t sent = s->udp.sent;
    queue_mtu(s->socket, family == AF_INET ? 4 : 6, NULL, 0, 1400);
    mtu_error.mode = 4; // Local synchronous errors may quote no UDP payload.
    fail_relay_send = 1; relay_send_error = EMSGSIZE;
    assert(handle_udp(args, packet, 1500, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
    assert(s->udp.state == UDP_ACTIVE && s->udp.sent == sent && sockets == 2);
    mtu_feedback(tunnel, version, packet, expected_mtu);
    assert(handle_udp(args, packet, 1500, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
    readable(relay); ssize_t wire_length = recv(relay, wire, sizeof(wire), 0); assert(wire_length > 134);
    struct epoll_event event = {.events = EPOLLERR, .data.ptr = s};
    queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, 1400);
    check_udp_socket(args, &event);
    mtu_feedback(tunnel, version, packet, expected_mtu);
    queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, 1400);
    recv_error_fd = s->socket; recv_error_value = EMSGSIZE; event.events = EPOLLIN;
    check_udp_socket(args, &event);
    mtu_feedback(tunnel, version, packet, expected_mtu);
    for (int mode = 0; mode < 10; mode++) {
        queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, mode == 0 ? 0 : mode == 9 ? 60 : 1400);
        if (mode >= 1 && mode <= 3) mtu_error.mode = mode;
        if (mode == 4) mtu_error.wire[2] = 1; // SOCKS fragmentation cannot identify this datagram.
        if (mode == 5) mtu_error.wire[version == 4 ? 10 : 22] ^= 1;
        if (mode == 6) mtu_error.length = 3;
        if (mode == 7) mtu_error.mtu = 9000; // The original packet fits already.
        uint16_t quote = s->udp.error_quote_length;
        if (mode == 8) s->udp.error_quote_length = (uint16_t) (off + 7);
        event.events = EPOLLERR;
        check_udp_socket(args, &event);
        s->udp.error_quote_length = quote;
        assert(!is_readable(tunnel) && s->udp.state == UDP_ACTIVE && sockets == 2);
    }
    // Recover without usable MTU metadata, including older-kernel option failures.
    for (int value = -1; value <= 0; value++) {
        queue_mtu(s->socket, family == AF_INET ? 4 : 6, NULL, 0, 0); mtu_error.remaining = 0;
        socket_mtu_value = value; fail_relay_send = 1;
        assert(handle_udp(args, packet, 1500, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(!is_readable(tunnel) && s->udp.state == UDP_ACTIVE);
    }
    // Fallback to the connected socket's known route MTU after a synchronous error.
    socket_mtu_value = 1400; fail_relay_send = 1;
    assert(handle_udp(args, packet, 1500, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
    mtu_feedback(tunnel, version, packet, expected_mtu);
    queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, 1400);
    mtu_error.remaining = 9; event.events = EPOLLERR;
    check_udp_socket(args, &event);
    assert(mtu_error.calls == 8 && mtu_error.remaining == 1);
    int reports = 0;
    while (is_readable(tunnel)) { uint8_t reply[2048]; tun_packet(tunnel, reply, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6); reports++; }
    assert(reports == 8);
    check_udp_socket(args, &event);
    mtu_feedback(tunnel, version, packet, expected_mtu);
    if (version == 6) {
        uint32_t outside = 1280 + (family == AF_INET ? 28u : 48u) + 22u - (uint32_t) off - 8;
        queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, outside - 1);
        check_udp_socket(args, &event);
        assert(!is_readable(tunnel) && s->udp.state == UDP_ACTIVE);
        queue_mtu(s->socket, family == AF_INET ? 4 : 6, wire, (size_t) wire_length, outside);
        check_udp_socket(args, &event);
        mtu_feedback(tunnel, version, packet, 1280);
    }
    error_queue_recv = NULL; socket_error_fd = socket_mtu_fd = -1;
    relay_send_error = EHOSTUNREACH;
    if (version == 4 && family == AF_INET6) {
        int mtu = 1280, discovery = IPV6_PMTUDISC_DO, dont_fragment = 1;
        assert(setsockopt(s->socket, IPPROTO_IPV6, IPV6_MTU, &mtu, sizeof(mtu)) == 0);
        assert(setsockopt(s->socket, IPPROTO_IPV6, IPV6_MTU_DISCOVER, &discovery, sizeof(discovery)) == 0);
        assert(setsockopt(s->socket, IPPROTO_IPV6, IPV6_DONTFRAG, &dont_fragment, sizeof(dont_fragment)) == 0);
        assert(handle_udp(args, packet, 1500, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(last_relay_errno == EMSGSIZE && s->udp.state == UDP_ACTIVE && sockets == 2);
        // Some older kernels return no error-queue entry and expose only the
        // route MTU, not this per-socket limit. Feedback then must be omitted.
        if (is_readable(tunnel)) mtu_feedback(tunnel, version, packet, expected_mtu - 120);
    }
    // A smaller datagram must still pass through the same association.
    size_t small = off + 8 + 1200;
    store16(packet + (version == 4 ? 2 : 4), (uint16_t) (small - (version == 4 ? 0 : 40)));
    store16(packet + off + 4, 1208); packet_checksums(packet, small, off, IPPROTO_UDP);
    assert(handle_udp(args, packet, small, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
    readable(relay); assert(recv(relay, wire, sizeof(wire), 0) == 1200 + (version == 4 ? 10 : 22));
    assert(s->udp.state == UDP_ACTIVE && sockets == 2 && args->ctx->udp_proxy_count == 1);
}
#endif

void test_udp_proxy_round_trip(const struct arguments *args, int kind) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    int version = kind == 0 || kind == 3 || kind == 7 || kind == 10 || kind == 11 ? 4 : 6;
    int family = kind == 1 || kind == 8 || kind == 12 ? AF_INET6 : AF_INET;
    int relay_family = kind == 3 || kind == 10 ? AF_INET6 : family;
    bool auth = kind != 0;
    struct sockaddr_storage server, relay_address; socklen_t server_length, relay_length;
    int listener = local_socket(family, SOCK_STREAM, &server, &server_length);
    assert(listen(listener, 4) == 0);
    int relay = local_socket(relay_family, SOCK_DGRAM, &relay_address, &relay_length);
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    setup_proxy(family, auth, &server);
    struct packet_buffers buffers = {0};
    struct arguments running = *args; running.tun = tunnel[1];
    running.buffers = kind == 12 || (kind > 0 && kind < 4) ? &buffers : NULL;
    uint8_t packet[128], buffer[2048];
    size_t off = proxy_packet(packet, version, 12345);
    assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
    struct ng_session *s = args->ctx->ng_session;
    assert(s != NULL && s->udp.proxy != NULL && args->ctx->udp_proxy_count == 1 && sockets == 2);
    assert(allocations == 3); // Session, association and the first queued datagram.
    assert(send_udp_proxy(&running, s, NULL, 0) == 1);
    assert(send_udp_proxy(&running, s, (const uint8_t *) "def", 3) == 1);
    assert(send_udp_proxy(&running, s, (const uint8_t *) "ghi", 3) == 1);
    assert(allocations == 6); // Four datagrams, including an empty one.
    for (int i = 0; i < 20; i++)
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
    assert(allocations == 6); // Overflow drops new packets without replacing queued data.
    readable(listener); int control = accept(listener, NULL, NULL); assert(control >= 0);
    pump_proxy(&running, epoll_fd); read_control(control, buffer, 3);
    assert(buffer[0] == 5 && buffer[1] == 1 && buffer[2] == (auth ? 2 : 0));
    uint8_t hello[] = {5, auth ? 2 : 0};
    assert(send(control, hello, 1, MSG_NOSIGNAL) == 1);
    pump_proxy(&running, epoll_fd); // Partial reply must not advance the handshake.
    assert(!udp_proxy_ready(&s->udp));
    assert(send(control, hello + 1, 1, MSG_NOSIGNAL) == 1);
    pump_proxy(&running, epoll_fd);
    if (auth) {
        pump_proxy(&running, epoll_fd); read_control(control, buffer, 11);
        const uint8_t expected[] = {1, 4, 'u', 's', 'e', 'r', 4, 'p', 'a', 's', 's'};
        assert(memcmp(buffer, expected, sizeof(expected)) == 0);
        const uint8_t accepted[] = {1, 0};
        assert(send(control, accepted, 2, MSG_NOSIGNAL) == 2);
        pump_proxy(&running, epoll_fd);
    }
    pump_proxy(&running, epoll_fd); read_control(control, buffer, 10);
    const uint8_t associate[] = {5, 3, 0, 1, 0, 0, 0, 0, 0, 0};
    assert(memcmp(buffer, associate, sizeof(associate)) == 0);
    bool mapped = kind == 9;
    uint8_t accepted[22] = {5, 0, 0, relay_family == AF_INET && !mapped ? 1 : 4};
    size_t header = relay_family == AF_INET && !mapped ? 10 : 22;
    if (mapped) {
        accepted[14] = accepted[15] = 0xff;
        memcpy(accepted + 16, &((struct sockaddr_in *) &relay_address)->sin_addr, 4);
    } else if (relay_family != family)
        memcpy(accepted + 4, &((struct sockaddr_in6 *) &relay_address)->sin6_addr, 16);
    // Wildcard relay IP must use the control peer's IP, never INADDR_ANY as an egress route.
    uint16_t port = relay_family == AF_INET ? ((struct sockaddr_in *) &relay_address)->sin_port :
                                      ((struct sockaddr_in6 *) &relay_address)->sin6_port;
    memcpy(accepted + header - 2, &port, 2);
    assert(send(control, accepted, header, MSG_NOSIGNAL) == header);
    if (kind == 4) fail_relay_send = 2; // Fail after the first queued datagram was sent.
    for (int i = 0; i < 4 && !udp_proxy_ready(&s->udp); i++) pump_proxy(&running, epoll_fd);
    if (kind == 4) {
        assert(fail_relay_send == 0 && s->udp.state == UDP_FINISHING && s->udp.sent == 3);
        assert(allocations == 2 && sockets == 0 && args->ctx->udp_proxy_count == 0);
        readable(relay);
        assert(recv(relay, buffer, sizeof(buffer), 0) == 25); // IPv6 destination header plus "abc".
        assert(!is_readable(relay));
        assert(strstr(last_proxy_log, "relay send error") != NULL);
        assert(proxy_logs == 1);
        proxy_feedback(tunnel[0], version, 0, packet, off + 11);
        clear(args->ctx); clear_packet_buffers(&buffers);
        close(control); close(listener); close(relay); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
        return;
    }
    assert(udp_proxy_ready(&s->udp));
    struct sockaddr_storage client; socklen_t client_length = sizeof(client);
    readable(relay);
    ssize_t bytes = recvfrom(relay, buffer, sizeof(buffer), 0, (struct sockaddr *) &client, &client_length);
    size_t udp_header = version == 4 ? 10 : 22;
    assert(bytes == (ssize_t) (udp_header + 3));
    assert(buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0 && buffer[3] == (version == 4 ? 1 : 4));
    assert(memcmp(buffer + 4, &s->udp.daddr, version == 4 ? 4 : 16) == 0);
    assert(load16(buffer + udp_header - 2) == 5000 && memcmp(buffer + udp_header, "abc", 3) == 0);
    uint8_t queued[128];
    for (int i = 0; i < 3; i++) {
        readable(relay);
        size_t payload_length = i == 0 ? 0 : 3;
        assert(recv(relay, queued, sizeof(queued), 0) == (ssize_t) (udp_header + payload_length));
        assert(memcmp(queued, buffer, udp_header) == 0);
        if (payload_length != 0)
            assert(memcmp(queued + udp_header, i == 1 ? "def" : "ghi", 3) == 0);
    }
    assert(!is_readable(relay) && s->udp.sent == 9 && allocations == 2);
    // Reject SOCKS fragmentation, reserved bytes and mismatched flow addresses/ports.
    for (int bad = 0; bad < 4; bad++) {
        buffer[bad == 0 ? 2 : bad == 1 ? 0 : bad == 2 ? 4 : udp_header - 1] ^= 1;
        assert(sendto(relay, buffer, (size_t) bytes, 0, (struct sockaddr *) &client, client_length) == bytes);
        pump_proxy(&running, epoll_fd);
        assert(!is_readable(tunnel[0]) && s->udp.state == UDP_ACTIVE);
        buffer[bad == 0 ? 2 : bad == 1 ? 0 : bad == 2 ? 4 : udp_header - 1] ^= 1;
    }
    assert(sendto(relay, buffer, 3, 0, (struct sockaddr *) &client, client_length) == 3);
    pump_proxy(&running, epoll_fd);
    assert(!is_readable(tunnel[0]) && s->udp.state == UDP_ACTIVE);
    packet_recv = truncated_relay_receive;
    assert(sendto(relay, buffer, (size_t) bytes, 0, (struct sockaddr *) &client, client_length) == bytes);
    pump_proxy(&running, epoll_fd);
    packet_recv = NULL;
    assert(!is_readable(tunnel[0]) && s->udp.state == UDP_ACTIVE);
    assert(sendto(relay, buffer, (size_t) bytes, 0, (struct sockaddr *) &client, client_length) == bytes);
    pump_proxy(&running, epoll_fd);
    size_t length = tun_packet(tunnel[0], buffer, version, IPPROTO_UDP);
    assert(length == off + 11 && load16(buffer + off) == 5000 && load16(buffer + off + 2) == 12345);
    assert(memcmp(buffer + off + 8, "abc", 3) == 0 && s->udp.received == 3);
    uint8_t expected[2048]; memcpy(expected, buffer, length);
    packet_checksums(expected, length, off, IPPROTO_UDP);
    assert(memcmp(expected, buffer, length) == 0);
    // A zero-length UDP payload is a valid datagram, not stream EOF.
    assert(send_udp_proxy(&running, s, NULL, 0) == 1);
    readable(relay); bytes = recv(relay, buffer, sizeof(buffer), 0);
    assert(bytes == (ssize_t) udp_header);
    assert(sendto(relay, buffer, (size_t) bytes, 0, (struct sockaddr *) &client, client_length) == bytes);
    pump_proxy(&running, epoll_fd);
    assert(tun_packet(tunnel[0], buffer, version, IPPROTO_UDP) == off + 8);
    if (kind >= 7 && kind <= 10) proxy_mtu_checks(&running, s, epoll_fd, relay, tunnel[0], relay_family);
    if (kind >= 11) proxy_batch_checks(&running, s, relay, &client, client_length,
                                      tunnel[0], epoll_fd, kind == 12);
    if (kind == 5 || kind == 6) {
        struct epoll_event event = {.events = kind == 5 ? EPOLLERR : EPOLLIN, .data.ptr = s};
        if (kind == 5) {
            socket_error_fd = s->socket; socket_error_value = ENETUNREACH;
            queue_mtu(s->socket, 4, NULL, 0, 0); mtu_error.error = ENETUNREACH;
        } else {
            recv_error_fd = s->socket; recv_error_value = ECONNREFUSED;
        }
        check_udp_socket(&running, &event);
        error_queue_recv = NULL;
        assert(strstr(last_proxy_log, kind == 5 ? "relay socket error 101" : "relay receive error 111") != NULL);
        assert(s->udp.state == UDP_FINISHING && sockets == 0 && args->ctx->udp_proxy_count == 0);
        proxy_feedback(tunnel[0], version, kind == 5 ? 0 : 4, packet, off + 11);
        check_udp_socket(&running, &event); // Stale events must not report twice.
        assert(!is_readable(tunnel[0]));
        socket_error_fd = -1;
    }
    close(control); // Closing the TCP association must also close the UDP relay socket.
    if (s->udp.state == UDP_ACTIVE) pump_proxy(&running, epoll_fd);
    assert(s->udp.state == UDP_FINISHING && s->socket == -1 && args->ctx->udp_proxy_count == 0);
    assert(sockets == 0);
    clear(args->ctx); clear_packet_buffers(&buffers);
    close(listener); close(relay); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_udp_proxy_failure_logging(const struct arguments *args, int version) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    int family = version == 4 ? AF_INET : AF_INET6;
    struct sockaddr_storage server; socklen_t server_length;
    int listener = local_socket(family, SOCK_STREAM, &server, &server_length);
    assert(listen(listener, 4) == 0);
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    struct arguments running = *args; running.tun = tunnel[1];
    struct ng_session candidate = {.socket = -1, .udp = {.uid = own_uid + 1}};
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    setup_proxy(family, false, &server); proxy_socks5_port = 0;
    log_errno = EIO;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == 1 && strstr(last_proxy_log, "invalid proxy port error 22") != NULL);
    for (int i = 0; i < 100; i++) {
        assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
        assert(proxy_logs == 1);
    }
    log_errno = 0;
    // A suppressed handshake rejection still reports policy feedback and cleans up.
    setup_proxy(family, false, &server);
    uint8_t packet[128], buffer[32]; size_t off = proxy_packet(packet, version, 12345);
    assert(handle_udp(&running, packet, off + 11, packet + off,
                      test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
    struct ng_session *s = args->ctx->ng_session;
    readable(listener); int peer = accept(listener, NULL, NULL); assert(peer >= 0);
    pump_proxy(&running, epoll_fd); read_control(peer, buffer, 3);
    const uint8_t rejected[] = {5, 0xff};
    assert(send(peer, rejected, sizeof(rejected), MSG_NOSIGNAL) == sizeof(rejected));
    pump_proxy(&running, epoll_fd);
    assert(proxy_logs == 1 && s->udp.state == UDP_FINISHING && s->socket == -1);
    assert(sockets == 0 && args->ctx->udp_proxy_count == 0);
    proxy_feedback(tunnel[0], version, version == 4 ? ICMP_UNREACH_FILTER_PROHIB : ICMP6_DST_UNREACH_ADMIN,
                   packet, off + 11);
    clear(args->ctx); close(peer);
    assert(allocations == 0 && sockets == 0);
    proxy_socks5_port = 0; test_monotonic_ms = 10999;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == 1);
    test_monotonic_ms = 11000;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == 2 && strstr(last_proxy_log, "102 further failures suppressed") != NULL);
    // Different owners and causes share the budget; the next log shows its current cause.
    candidate.udp.uid++;
    setup_proxy(family, false, &server); strcpy(proxy_socks5_addr, "invalid");
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == 2);
    test_monotonic_ms = 12000;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == 3 && strstr(last_proxy_log, "invalid proxy address error 22") != NULL);
    assert(strstr(last_proxy_log, "1 further failures suppressed") != NULL);
    // Zero, failed and backward clocks cannot leave diagnostics permanently suppressed.
    const int64_t clocks[] = {-1, 0, 10000, 9999, INT64_MAX - 500, INT64_MAX - 500};
    for (size_t i = 0; i < sizeof(clocks) / sizeof(clocks[0]); i++) {
        test_monotonic_ms = clocks[i]; int before = proxy_logs;
        assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
        assert(proxy_logs == before + 1);
    }
    test_monotonic_ms = 1000;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    args->ctx->udp_proxy_failures_suppressed = UINT_MAX - 1;
    int before = proxy_logs;
    for (int i = 0; i < 2; i++)
        assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == before && args->ctx->udp_proxy_failures_suppressed == UINT_MAX);
    test_monotonic_ms = 2000;
    assert(open_udp_proxy(&running, &candidate, epoll_fd) == -1 && errno == EINVAL);
    assert(proxy_logs == before + 1 && args->ctx->udp_proxy_failures_suppressed == 0);
    assert(strstr(last_proxy_log, "at least ") != NULL);
    test_monotonic_enabled = false;
    close(listener); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_udp_proxy_bad_handshakes(const struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    for (int bad = 0; bad < 17; bad++) {
        test_monotonic_ms += 1000;
        struct sockaddr_storage address; socklen_t address_length;
        int listener = local_socket(AF_INET, SOCK_STREAM, &address, &address_length);
        assert(listen(listener, 4) == 0);
        int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
        struct arguments running = *args; running.tun = tunnel[1];
        setup_proxy(AF_INET, bad == 2, &address);
        uint8_t packet[128], buffer[32]; size_t off = proxy_packet(packet, 4, 12345);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        struct ng_session *s = args->ctx->ng_session;
        readable(listener); int control = accept(listener, NULL, NULL); assert(control >= 0);
        if (bad == 7) {
            struct epoll_event event;
            assert(epoll_wait(epoll_fd, &event, 1, 1000) == 1);
            close(epoll_fd); epoll_fd = -1;
            check_udp_socket(&running, &event); // EPOLL_CTL_MOD failure closes both sockets.
            check_udp_socket(&running, &event); // A queued stale control event stays safe.
        } else {
            pump_proxy(&running, epoll_fd); read_control(control, buffer, 3);
            uint8_t hello[] = {bad == 0 ? 4 : 5, bad == 1 ? 2 : bad == 2 ? 2 : 0};
            assert(send(control, hello, sizeof(hello), MSG_NOSIGNAL) == sizeof(hello));
            pump_proxy(&running, epoll_fd);
            if (bad == 2) {
                pump_proxy(&running, epoll_fd); read_control(control, buffer, 11);
                const uint8_t rejected[] = {1, 1};
                assert(send(control, rejected, 2, MSG_NOSIGNAL) == 2);
                pump_proxy(&running, epoll_fd);
            } else if (bad >= 3) {
                pump_proxy(&running, epoll_fd); read_control(control, buffer, 10);
                uint8_t reply[] = {5, bad == 3 ? 7 : bad >= 8 ? bad - 7 : 0, bad == 4 ? 1 : 0,
                                   bad == 5 ? 3 : 1, 127, 0, 0, 1, 0, bad == 6 ? 0 : 53};
                size_t reply_length = bad >= 8 ? 4 : sizeof(reply);
                assert(send(control, reply, reply_length, MSG_NOSIGNAL) == reply_length);
                if (bad >= 8) assert(shutdown(control, SHUT_WR) == 0);
                for (int i = 0; i < 3 && s->udp.state == UDP_ACTIVE; i++) pump_proxy(&running, epoll_fd);
            }
        }
        assert(s->udp.state == UDP_FINISHING && s->socket == -1 && s->udp.sent == 0);
        assert(args->ctx->udp_proxy_count == 0 && sockets == 0);
        const char *reason = bad == 0 ? "invalid method reply version" : bad == 1 ? "method was not offered" :
                bad == 2 ? "authentication status 0x01" : bad == 3 ? "UDP ASSOCIATE unsupported" :
                bad == 4 ? "invalid UDP ASSOCIATE reply header" : bad == 5 ? "relay address type 0x03" :
                bad == 6 ? "zero relay port" : bad == 7 ? "monitor control reply" : "UDP ASSOCIATE reply";
        assert(strstr(last_proxy_log, reason) != NULL);
        if (bad >= 8) {
            char code[8]; snprintf(code, sizeof(code), "0x%02x", bad - 7);
            assert(strstr(last_proxy_log, code) != NULL);
        }
        int code = bad == 2 || bad == 9 ? ICMP_UNREACH_FILTER_PROHIB : bad == 10 ? ICMP_UNREACH_NET :
                   bad == 12 ? ICMP_UNREACH_PORT : ICMP_UNREACH_HOST;
        proxy_feedback(tunnel[0], 4, code, packet, off + 11);
        close(control); close(listener);
        clear(args->ctx);
        if (epoll_fd >= 0) close(epoll_fd);
        close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
    test_monotonic_enabled = false;
#endif
}

void test_udp_proxy_reset_replies(const struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    for (int version = 4; version <= 6; version += 2) {
        for (int kind = 0; kind < 10; kind++) {
            test_monotonic_ms += 1000;
            int family = version == 4 ? AF_INET : AF_INET6;
            struct sockaddr_storage address; socklen_t address_length;
            int listener = local_socket(family, SOCK_STREAM, &address, &address_length);
            assert(listen(listener, 4) == 0);
            int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
            bool auth = kind == 6 || kind == 7;
            setup_proxy(family, auth, &address);
            struct arguments running = *args; running.tun = tunnel[1];
            uint8_t packet[128], buffer[32]; size_t off = proxy_packet(packet, version, 12345);
            assert(handle_udp(&running, packet, off + 11, packet + off,
                              test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
            struct ng_session *s = args->ctx->ng_session;
            readable(listener); int control = accept(listener, NULL, NULL); assert(control >= 0);
            struct epoll_event event;
            assert(epoll_wait(epoll_fd, &event, 1, 1000) == 1);
            struct ng_session *endpoint = event.data.ptr;
            check_udp_socket(&running, &event); read_control(control, buffer, 3);
            uint8_t reply[10] = {5, 7, 0, 1, 127, 0, 0, 1, 0x14, 0xe9};
            size_t offset = 0, length = 4;
            if (kind != 4 && kind != 5) {
                uint8_t hello[] = {5, auth ? 2 : 0};
                assert(send(control, hello, 2, MSG_NOSIGNAL) == 2);
                pump_proxy(&running, epoll_fd); pump_proxy(&running, epoll_fd);
                read_control(control, buffer, auth ? 11 : 10);
            }
            if (kind == 1) {
                assert(send(control, reply, 2, MSG_NOSIGNAL) == 2);
                pump_proxy(&running, epoll_fd); // Prefix partly read before reset readiness.
                offset = 2; length = 2;
            } else if (kind == 2) length = 3; // Incomplete reply: retain the socket cause.
            else if (kind == 3 || kind == 8 || kind == 9) {
                reply[1] = 0;
                if (kind == 8) length = 10;
                if (kind == 9) {
                    assert(send(control, reply, 4, MSG_NOSIGNAL) == 4);
                    pump_proxy(&running, epoll_fd); // Success prefix read before reset.
                    offset = 4; length = 6;
                }
            } else if (kind == 4 || kind == 5) {
                reply[1] = kind == 4 ? 0xff : 0;
                length = 2;
            } else if (auth) {
                reply[0] = 1; reply[1] = kind == 6 ? 1 : 0;
                length = 2;
            }
            assert(send(control, reply + offset, length, MSG_NOSIGNAL) == length);
            readable(endpoint->socket);
            socket_error_fd = endpoint->socket; socket_error_value = ECONNRESET;
            event.events = EPOLLIN | EPOLLERR | EPOLLHUP | EPOLLRDHUP;
            int before = proxy_logs;
            check_udp_socket(&running, &event);
            socket_error_fd = -1;
            const char *expected = kind <= 1 ? "UDP ASSOCIATE reply 0x07" :
                    kind == 4 ? "authentication method 0xff" :
                    kind == 6 ? "authentication status 0x01" : "control socket error 104";
            assert(strstr(last_proxy_log, expected) != NULL);
            assert(proxy_logs == before + 1 && s->udp.state == UDP_FINISHING && s->udp.sent == 0);
            assert(sockets == 0 && args->ctx->udp_proxy_count == 0);
            int code = kind == 4 || kind == 6 ?
                       (version == 4 ? ICMP_UNREACH_FILTER_PROHIB : ICMP6_DST_UNREACH_ADMIN) :
                       version == 4 ? ICMP_UNREACH_HOST : ICMP6_DST_UNREACH_NOROUTE;
            proxy_feedback(tunnel[0], version, code, packet, off + 11);
            check_udp_socket(&running, &event);
            assert(proxy_logs == before + 1 && !is_readable(tunnel[0]));
            clear(args->ctx);
            close(control); close(listener); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
            assert(allocations == 0 && sockets == 0);
        }
    }
    test_monotonic_enabled = false;
#endif
}

void test_udp_proxy_capacity(const struct arguments *args, int kind) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    struct sockaddr_storage server, relay_address; socklen_t server_length, relay_length;
    int listener = local_socket(AF_INET, SOCK_STREAM, &server, &server_length);
    assert(listen(listener, 32) == 0);
    int relay = local_socket(AF_INET, SOCK_DGRAM, &relay_address, &relay_length);
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    setup_proxy(AF_INET, false, &server);
    struct arguments running = *args; running.tun = tunnel[1];
    test_wall_time_enabled = true; test_wall_time = 1000;
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    struct ng_session *sessions[UDP_PROXY_MAX];
    struct epoll_event controls[3];
    int peers[3];
    uint8_t packet[128], buffer[128]; size_t off = proxy_packet(packet, 4, 12345);
    for (int i = 0; i < UDP_PROXY_MAX; i++) {
        store16(packet + off, (uint16_t) (12345 + i));
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        sessions[i] = args->ctx->ng_session;
        if (i < 3) {
            readable(listener); peers[i] = accept(listener, NULL, NULL); assert(peers[i] >= 0);
            assert(epoll_wait(epoll_fd, &controls[i], 1, 1000) == 1);
            check_udp_socket(&running, &controls[i]); read_control(peers[i], buffer, 3);
            const uint8_t hello[] = {5, 0};
            assert(send(peers[i], hello, sizeof(hello), MSG_NOSIGNAL) == sizeof(hello));
            pump_proxy(&running, epoll_fd); pump_proxy(&running, epoll_fd);
            read_control(peers[i], buffer, 10);
            uint8_t accepted[10] = {5, 0, 0, 1};
            memcpy(accepted + 8, &((struct sockaddr_in *) &relay_address)->sin_port, 2);
            assert(send(peers[i], accepted, sizeof(accepted), MSG_NOSIGNAL) == sizeof(accepted));
            while (!udp_proxy_ready(&sessions[i]->udp)) pump_proxy(&running, epoll_fd);
            readable(relay); assert(recv(relay, buffer, sizeof(buffer), 0) == 13);
        }
        // Spare capacity must never trigger even ordinary timeout reclamation.
        if (i == 2) sessions[0]->udp.time = 100;
    }
    assert(sessions[0]->udp.state == UDP_ACTIVE);
    sessions[0]->udp.time = 1000;
    store16(packet + off, 13000);
    if (kind == 0) {
        sessions[0]->udp.time = 900; // Eligible idle flow must survive when expiry makes room.
        sessions[3]->udp.time = sessions[4]->udp.time = 979;
        sessions[5]->udp.time = 980; // Exact handshake deadline is not expired yet.
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX - 1);
        assert(sockets == 2 * (UDP_PROXY_MAX - 1));
        assert(!is_readable(tunnel[0]));
        assert(sessions[0]->udp.state == UDP_ACTIVE && sessions[5]->udp.state == UDP_ACTIVE);
        for (int i = 3; i <= 4; i++) {
            assert(sessions[i]->udp.state == UDP_FINISHING && sessions[i]->socket == -1);
            struct epoll_event stale = {.events = EPOLLIN | EPOLLERR, .data.ptr = sessions[i]};
            check_udp_socket(&running, &stale);
        }
        // Reuse an expired flow's exact tuple without waiting for linger cleanup.
        store16(packet + off, 12348);
        assert(find_udp_session(&running, packet, packet + off) == NULL);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(find_udp_session(&running, packet, packet + off) == args->ctx->ng_session);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX);
    } else if (kind == 1) {
        sessions[0]->udp.time = 940; sessions[1]->udp.time = 910; sessions[2]->udp.time = 941;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[1]->udp.state == UDP_FINISHING && sessions[1]->socket == -1);
        store16(packet + off, 12346);
        assert(find_udp_session(&running, packet, packet + off) == NULL);
        store16(packet + off, 13000);
        assert(sessions[0]->udp.state == UDP_ACTIVE && sessions[2]->udp.state == UDP_ACTIVE);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
        assert(!is_readable(tunnel[0]));
        // Already queued events may still reference both endpoints of the victim.
        controls[1].events = EPOLLIN | EPOLLERR;
        check_udp_socket(&running, &controls[1]);
        check_udp_socket(&running, &sessions[1]->ev);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
        store16(packet + off, 13001);
        fail_alloc_tag = "udp proxy"; fail_alloc_match = 1;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        fail_alloc_tag = NULL;
        assert(sessions[0]->udp.state == UDP_FINISHING && sessions[2]->udp.state == UDP_ACTIVE);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX - 1);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        store16(packet + off, 13002);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(sessions[2]->udp.state == UDP_ACTIVE); // 59 seconds: retain it.
        // A surviving association can still forward through its existing relay.
        assert(send_udp_proxy(&running, sessions[2], (const uint8_t *) "def", 3) == 1);
        readable(relay); assert(recv(relay, buffer, sizeof(buffer), 0) == 13);
        assert(memcmp(buffer + 10, "def", 3) == 0);
        test_wall_time = 1001;
        test_monotonic_ms += 1000;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[2]->udp.state == UDP_FINISHING); // 60 seconds: reclaim it.
    } else if (kind == 2) {
        test_wall_time = -1;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        test_wall_time = 900;
        test_monotonic_ms += 100;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        test_wall_time = 1000;
        test_monotonic_ms += 100;
        sessions[0]->udp.time = -1; sessions[1]->udp.time = 1100; sessions[2]->udp.time = 941;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        for (int i = 0; i < UDP_PROXY_MAX; i++) assert(sessions[i]->udp.state == UDP_ACTIVE);
    } else if (kind == 3) {
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        sessions[0]->udp.time = 940;
        // Newly eligible flows wait until the cooldown expires, even in a burst.
        for (int i = 0; i < 100; i++)
            assert(handle_udp(&running, packet, off + 11, packet + off,
                              test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        test_monotonic_ms = 10099;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        test_monotonic_ms = 10100;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[0]->udp.state == UDP_FINISHING);
        // Successful scans do not arm the cooldown.
        sessions[1]->udp.time = 940; store16(packet + off, 13001);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[1]->udp.state == UDP_FINISHING);
        store16(packet + off, 13002);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        // A genuinely freed slot bypasses the cooldown, without waiting 100 ms.
        sessions[3]->udp.state = UDP_FINISHING;
        close_udp_proxy(sessions[3]); unindex_session(args->ctx, sessions[3]);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        // Closing the association also invalidates the previous full-table scan.
        sessions[2]->udp.time = 940; store16(packet + off, 13003);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[2]->udp.state == UDP_FINISHING);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
    } else if (kind == 4) {
        test_monotonic_ms = 0; // Zero is valid and must still start a cooldown.
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        sessions[0]->udp.time = 940;
        test_monotonic_ms = 99;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        // A backward monotonic reading must not prolong an old cooldown.
        test_monotonic_ms = 10000;
        sessions[0]->udp.time = 1000;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        sessions[0]->udp.time = 940; test_monotonic_ms = 9999;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        store16(packet + off, 13001); test_monotonic_ms = 11000;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        sessions[1]->udp.time = 940; test_monotonic_ms = -1;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        // Near INT64_MAX, omit an unrepresentable deadline rather than overflow.
        store16(packet + off, 13002); test_monotonic_ms = INT64_MAX - 50;
        args->ctx->udp_proxy_limit_log_at = INT64_MAX; // Keep this boundary test on the scan deadline.
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        sessions[2]->udp.time = 940;
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
    } else {
        sessions[0]->udp.time = 940; // Eligible for eviction if validation happens too late.
        int before = allocations;
        for (int bad = 0; bad < 12; bad++) {
            test_monotonic_ms += 1000;
            setup_proxy(AF_INET, false, &server);
            const char *reason = invalid_proxy_setting(bad);
            fail_alloc_tag = "udp proxy"; fail_alloc_match = 1;
            assert(handle_udp(&running, packet, off + 11, packet + off,
                              test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
            assert(fail_alloc_match == 1 && strstr(last_proxy_log, reason) != NULL);
            fail_alloc_tag = NULL;
            assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
            assert(args->ctx->udp_proxy_scan_at == 0 && args->ctx->udp_proxy_limit_log_at == 0);
            assert(allocations == before);
            for (int i = 0; i < UDP_PROXY_MAX; i++) assert(sessions[i]->udp.state == UDP_ACTIVE);
        }
        setup_proxy(AF_INET, false, &server);
        assert(handle_udp(&running, packet, off + 11, packet + off,
                          test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        assert(sessions[0]->udp.state == UDP_FINISHING && args->ctx->udp_proxy_count == UDP_PROXY_MAX);
    }
    // Capacity reclamation itself is silent: it must not send errors quoting old traffic.
    clear(args->ctx);
    assert(args->ctx->udp_proxy_count == 0 && allocations == 0 && sockets == 0);
    test_wall_time_enabled = false;
    test_monotonic_enabled = false;
    for (int i = 0; i < 3; i++) close(peers[i]);
    close(listener); close(relay); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_udp_proxy_failures(const struct arguments *args, int kind) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    struct sockaddr_storage server; socklen_t server_length;
    int listener = local_socket(AF_INET, SOCK_STREAM, &server, &server_length);
    assert(listen(listener, 32) == 0);
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    struct arguments running = *args; running.tun = tunnel[1];
    setup_proxy(AF_INET, false, &server);
    uint8_t packet[128], buffer[2048]; size_t off = proxy_packet(packet, 4, 12345);
    if (kind == 0) {
        test_monotonic_enabled = true; test_monotonic_ms = 10000;
        for (int i = 0; i <= UDP_PROXY_MAX; i++) {
            store16(packet + off, (uint16_t) (12345 + i));
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd)
                   == (i < UDP_PROXY_MAX));
        }
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
        assert(allocations == 3 * UDP_PROXY_MAX);
        assert(strstr(last_proxy_log, "association limit reached") != NULL);
        assert(proxy_logs == 1);
        assert(args->ctx->udp_proxy_limit_log_at == 11000);
        for (int i = 0; i < 100; i++) {
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
            assert(args->ctx->udp_proxy_limit_log_at == 11000);
            assert(proxy_logs == 1);
        }
        int reports = 0;
        while (is_readable(tunnel[0])) {
            assert(tun_packet(tunnel[0], buffer, 4, IPPROTO_ICMP) > 28);
            assert(buffer[20] == ICMP_UNREACH && buffer[21] == ICMP_UNREACH_HOST);
            reports++;
        }
        assert(reports == 20); // Shared feedback budget: burst 20, then one per 100 ms.
        test_monotonic_ms = 11000;
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(args->ctx->udp_proxy_limit_log_at == 12000);
        assert(proxy_logs == 2);
        proxy_feedback(tunnel[0], 4, ICMP_UNREACH_HOST, packet, off + 11);
        test_monotonic_enabled = false;
        clear(args->ctx);
        assert(allocations == 0 && sockets == 0);
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        struct ng_session *s = args->ctx->ng_session;
        uint8_t large[32768] = {0};
        assert(send_udp_proxy(&running, s, large, sizeof(large)) == 1);
        assert(send_udp_proxy(&running, s, large, sizeof(large) - 3) == 1);
        assert(allocations == 5); // Exactly 64 KiB across three datagrams.
        assert(send_udp_proxy(&running, s, large, 1) == 0);
        assert(allocations == 5 && s->udp.state == UDP_ACTIVE);
        assert(send_udp_proxy(&running, s, NULL, 0) == 1); // Byte limit allows an empty fourth packet.
        assert(allocations == 6);
        assert(send_udp_proxy(&running, s, NULL, 0) == 0); // Count limit includes empty packets.
        assert(allocations == 6);
    } else if (kind == 1) {
        const char *tags[] = {"udp proxy", "udp proxy pending"};
        for (int i = 0; i < 2; i++) {
            fail_alloc_tag = tags[i]; fail_alloc_match = 1;
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
            assert(fail_alloc_match == 0);
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0 && args->ctx->udp_proxy_count == 0);
        }
        fail_alloc_tag = NULL;
        for (int queued = 1; queued < 4; queued++) {
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
            struct ng_session *s = args->ctx->ng_session;
            for (int i = 1; i < queued; i++)
                assert(send_udp_proxy(&running, s, (const uint8_t *) "abc", 3) == 1);
            fail_alloc_tag = "udp proxy pending"; fail_alloc_match = 1;
            assert(send_udp_proxy(&running, s, (const uint8_t *) "abc", 3) == 0);
            assert(fail_alloc_match == 0 && s->udp.state == UDP_FINISHING);
            assert(allocations == 2 && sockets == 0 && args->ctx->udp_proxy_count == 0);
            fail_alloc_tag = NULL;
            clear(args->ctx);
            assert(allocations == 0);
        }
        failure = PROTECT;
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 0);
        assert(allocations == 0 && sockets == 0);
        failure = NONE;
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, -1) == 0);
        assert(allocations == 0 && sockets == 0);
    } else {
        assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
        struct ng_session *s = args->ctx->ng_session;
        if (kind == 2) {
            // Ongoing app traffic does not extend the fixed handshake deadline.
            time_t started = s->udp.time;
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd) == 1);
            for (int i = 0; i < 2; i++)
                assert(send_udp_proxy(&running, s, (const uint8_t *) "abc", 3) == 1);
            assert(s->udp.time == started);
            s->udp.time = time(NULL) - TCP_INIT_TIMEOUT - 1;
            assert(check_udp_session(&running, s, 1, SESSION_MAX) == 0);
            assert(s->udp.state == UDP_CLOSED && sockets == 0 && args->ctx->udp_proxy_count == 0);
        } else {
            readable(listener); int control = accept(listener, NULL, NULL); assert(control >= 0);
            pump_proxy(&running, epoll_fd); read_control(control, buffer, 3);
            const uint8_t rejected[] = {5, 0xff};
            assert(send(control, rejected, 2, MSG_NOSIGNAL) == 2);
            pump_proxy(&running, epoll_fd);
            assert(s->udp.state == UDP_FINISHING && s->socket == -1 && sockets == 0);
            assert(strstr(last_proxy_log, "authentication method 0xff") != NULL);
            proxy_feedback(tunnel[0], 4, ICMP_UNREACH_FILTER_PROHIB, packet, off + 11);
            close(control);
        }
    }
    clear(args->ctx);
    close(listener); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0 && args->ctx->udp_proxy_count == 0);
#endif
}

void test_udp_proxy_socket_errors(const struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    test_monotonic_enabled = true; test_monotonic_ms = 10000;
    for (int version = 4; version <= 6; version += 2) {
        for (int kind = 0; kind < 3; kind++) {
            test_monotonic_ms += 1000;
            int family = version == 4 ? AF_INET : AF_INET6;
            struct sockaddr_storage server; socklen_t server_length;
            int listener = local_socket(family, SOCK_STREAM, &server, &server_length);
            assert(listen(listener, 4) == 0);
            int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
            setup_proxy(family, false, &server);
            struct arguments running = *args; running.tun = tunnel[1];
            uint8_t packet[128], buffer[32]; size_t off = proxy_packet(packet, version, 12345);
            int before = proxy_logs;
            if (kind == 2) { real_network = 0; connect_errno = ENETUNREACH; }
            assert(handle_udp(&running, packet, off + 11, packet + off,
                              test_owner(own_uid + 1), &proxy_route, epoll_fd) == (kind != 2));
            if (kind != 2) {
                struct epoll_event event;
                assert(epoll_wait(epoll_fd, &event, 1, 1000) == 1);
                struct ng_session *endpoint = event.data.ptr;
                if (kind == 0) {
                    socket_error_fd = endpoint->socket; socket_error_value = ECONNREFUSED;
                    event.events = EPOLLOUT | EPOLLERR | EPOLLHUP;
                } else {
                    check_udp_socket(&running, &event); // Send HELLO, then inject a receive failure.
                    readable(listener); int control = accept(listener, NULL, NULL); assert(control >= 0);
                    read_control(control, buffer, 3);
                    recv_error_fd = endpoint->socket; recv_error_value = ECONNRESET;
                    event.events = EPOLLIN;
                    check_udp_socket(&running, &event);
                    close(control);
                }
                check_udp_socket(&running, &event);
                assert(args->ctx->ng_session->udp.state == UDP_FINISHING);
                socket_error_fd = -1;
            }
            assert(strstr(last_proxy_log, kind == 0 ? "control socket error 111" :
                          kind == 1 ? "control receive error 104" : "control connect error 101") != NULL);
            assert(proxy_logs == before + 1 && sockets == 0 && args->ctx->udp_proxy_count == 0);
            int code = kind == 0 ? (version == 4 ? ICMP_UNREACH_PORT : ICMP6_DST_UNREACH_NOPORT) :
                       kind == 2 && version == 4 ? ICMP_UNREACH_NET :
                       version == 4 ? ICMP_UNREACH_HOST : ICMP6_DST_UNREACH_NOROUTE;
            proxy_feedback(tunnel[0], version, code, packet, off + 11);
            clear(args->ctx);
            assert(allocations == 0);
            close(listener); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
            connect_errno = 0;
        }
    }
    test_monotonic_enabled = false;
#endif
}

void test_udp_proxy_uid_routes(struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int route = 0; route < 3; route++) {
        struct sockaddr_storage server, destination; socklen_t server_length, destination_length;
        int listener = local_socket(AF_INET, SOCK_STREAM, &server, &server_length);
        assert(listen(listener, 4) == 0);
        int receiver = local_socket(AF_INET, SOCK_DGRAM, &destination, &destination_length);
        int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
        setup_proxy(AF_INET, false, &server);
        struct arguments running = *args; running.tun = tunnel[1];
        test_monotonic_enabled = true; test_monotonic_ms = 10000;
        uid_lookup_result = -1; policy_redirect.udp_proxy = route != 1;
        uint8_t packet[128], output[2048]; size_t off = proxy_packet(packet, 4, 12345);
        memcpy(packet + 16, &((struct sockaddr_in *) &destination)->sin_addr, 4);
        memcpy(packet + off + 2, &((struct sockaddr_in *) &destination)->sin_port, 2);
        packet_checksums(packet, off + 11, off, IPPROTO_UDP);
        handle_ip(&running, packet, off + 11, epoll_fd, 0, SESSION_MAX);
        struct ng_session *old = args->ctx->ng_session;
        assert(old != NULL && old->udp.uid < 0 && (old->udp.proxy != NULL) == (route != 1));
        struct epoll_event stale = old->ev;
        if (route != 1) assert(epoll_wait(epoll_fd, &stale, 1, 1000) == 1);
        else {
            readable(receiver); assert(recv(receiver, output, sizeof(output), 0) == 3);
        }
        assert(!is_readable(receiver));
        test_monotonic_ms = 11000; uid_lookup_result = 23456;
        policy_redirect.udp_proxy = route == 1; deny_address = route == 2;
        handle_ip(&running, packet, off + 11, epoll_fd, 1, SESSION_MAX);
        struct ng_session *current = args->ctx->ng_session;
        assert(current != old && current != NULL && current->udp.uid == 23456);
        assert(old->udp.state == UDP_FINISHING && (current->udp.proxy != NULL) == (route == 1));
        check_udp_socket(&running, &stale); // Must not touch a replacement's reused descriptor.
        assert(check_udp_session(&running, old, 2, SESSION_MAX) == 0);
        if (route == 0) {
            readable(receiver); assert(recv(receiver, output, sizeof(output), 0) == 3);
            assert(memcmp(output, "abc", 3) == 0 && current->socket >= 0 && sockets == 1);
        } else {
            assert(!is_readable(receiver));
            assert(current->udp.state == (route == 2 ? UDP_BLOCKED : UDP_ACTIVE));
            assert(sockets == (route == 2 ? 0 : 2));
        }
        clear(args->ctx);
        close(listener); close(receiver); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0 && args->ctx->udp_proxy_count == 0);
        deny_address = 0; policy_redirect.udp_proxy = false;
    }
    test_monotonic_enabled = false;
#endif
}
