#include "test_support.h"

ssize_t __real_sendmsg(int, const struct msghdr *, int);
static int fail_relay_send;
ssize_t __wrap_sendmsg(int fd, const struct msghdr *message, int flags) {
    if (fail_relay_send > 0 && --fail_relay_send == 0) {
        errno = EHOSTUNREACH;
        return -1;
    }
    return __real_sendmsg(fd, message, flags);
}

#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
extern char proxy_socks5_addr[], proxy_socks5_username[], proxy_socks5_password[];
extern int proxy_socks5_port;
int __real_socket(int, int, int);
static struct allowed proxy_route = {.udp_proxy = true};
ssize_t __real_recvmsg(int, struct msghdr *, int);
static ssize_t truncated_relay_receive(int fd, struct msghdr *message, int flags) {
    // Exercise a real kernel MSG_TRUNC without allocating oversized UDP packets.
    size_t capacity = message->msg_iov[0].iov_len;
    message->msg_iov[0].iov_len = 8;
    ssize_t bytes = __real_recvmsg(fd, message, flags);
    message->msg_iov[0].iov_len = capacity;
    assert(bytes == 8 && (message->msg_flags & MSG_TRUNC));
    return bytes;
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
#endif

void test_udp_proxy_round_trip(const struct arguments *args, int kind) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    int version = kind == 0 || kind == 3 ? 4 : 6;
    int family = kind == 1 ? AF_INET6 : AF_INET;
    int relay_family = kind == 3 ? AF_INET6 : family;
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
    running.buffers = kind == 0 || kind == 4 ? NULL : &buffers;
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
    uint8_t accepted[22] = {5, 0, 0, relay_family == AF_INET ? 1 : 4};
    size_t header = relay_family == AF_INET ? 10 : 22;
    if (relay_family != family)
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
    close(control); // Closing the TCP association must also close the UDP relay socket.
    pump_proxy(&running, epoll_fd);
    assert(s->udp.state == UDP_FINISHING && s->socket == -1 && args->ctx->udp_proxy_count == 0);
    assert(sockets == 0);
    clear(args->ctx); clear_packet_buffers(&buffers);
    close(listener); close(relay); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_udp_proxy_bad_handshakes(const struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int bad = 0; bad < 8; bad++) {
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
                uint8_t reply[] = {5, bad == 3 ? 7 : 0, bad == 4 ? 1 : 0,
                                   bad == 5 ? 3 : 1, 127, 0, 0, 1, 0, bad == 6 ? 0 : 53};
                assert(send(control, reply, sizeof(reply), MSG_NOSIGNAL) == sizeof(reply));
                for (int i = 0; i < 3 && s->udp.state == UDP_ACTIVE; i++) pump_proxy(&running, epoll_fd);
            }
        }
        assert(s->udp.state == UDP_FINISHING && s->socket == -1 && s->udp.sent == 0);
        assert(args->ctx->udp_proxy_count == 0 && sockets == 0);
        close(control); close(listener);
        clear(args->ctx);
        if (epoll_fd >= 0) close(epoll_fd);
        close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
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
        for (int i = 0; i <= UDP_PROXY_MAX; i++) {
            store16(packet + off, (uint16_t) (12345 + i));
            assert(handle_udp(&running, packet, off + 11, packet + off, test_owner(own_uid + 1), &proxy_route, epoll_fd)
                   == (i < UDP_PROXY_MAX));
        }
        assert(args->ctx->udp_proxy_count == UDP_PROXY_MAX && sockets == 2 * UDP_PROXY_MAX);
        assert(allocations == 3 * UDP_PROXY_MAX);
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
            close(control);
        }
    }
    clear(args->ctx);
    close(listener); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0 && args->ctx->udp_proxy_count == 0);
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
