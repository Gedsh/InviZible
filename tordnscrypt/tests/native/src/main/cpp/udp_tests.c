#include "test_support.h"

void test_udp_buffer_reuse(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    real_udp_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        int upstream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        struct packet_buffers buffers = {0};
        struct arguments running = *args;
        running.tun = tunnel[1]; running.buffers = &buffers;
        struct ng_session s = {.protocol = IPPROTO_UDP, .socket = upstream[0]};
        s.udp.version = version; s.udp.source = htons(12345); s.udp.dest = htons(5000);
        s.udp.state = UDP_ACTIVE; s.udp.mss = UDP6_MAXMSG;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        uint8_t *data = malloc(65536), *output = malloc(65536);
        assert(data != NULL && output != NULL);
        const size_t lengths[] = {17, 1400, 3, 1400};
        for (size_t i = 0; i < sizeof(lengths) / sizeof(*lengths); i++) {
            for (size_t j = 0; j < lengths[i]; j++) data[j] = (uint8_t) (j * 17 + i);
            ssize_t sent = send(upstream[1], data, lengths[i], 0);
            if (sent != (ssize_t) lengths[i]) {
                char reason[160];
                snprintf(reason, sizeof(reason), "UDP fixture send version %d size %zu returned %zd errno %d",
                         version, lengths[i], sent, errno);
                native_test_fail(reason, __FILE__, __LINE__);
            }
            check_udp_socket(&running, &event);
            assert(s.udp.state == UDP_ACTIVE && allocations == 2);
            readable(tunnel[0]);
            ssize_t length = read(tunnel[0], output, 65536);
            size_t off = version == 4 ? 20 : 40;
            assert(length == off + 8 + lengths[i]);
            assert(memcmp(output + off + 8, data, lengths[i]) == 0);
            uint16_t checksum = load16(output + off + 6);
            packet_checksums(output, (size_t) length, off, IPPROTO_UDP);
            assert(load16(output + off + 6) == checksum);
        }
        // Some device kernels return ENOBUFS for a 64 KiB Unix datagram. Verify
        // maximum packet construction without requiring that kernel allocation.
        int sink = open("/dev/null", O_WRONLY);
        assert(sink >= 0);
        struct arguments maximum = running;
        maximum.tun = sink;
        memset(data, 0x5A, UDP6_MAXMSG);
        size_t off = version == 4 ? 20 : 40;
        size_t length = off + 8 + UDP6_MAXMSG;
        assert(write_udp(&maximum, &s.udp, data, UDP6_MAXMSG) == length);
        memcpy(output, buffers.buffer[PACKET_OUTPUT].data, length);
        assert(memcmp(output + off + 8, data, UDP6_MAXMSG) == 0);
        uint16_t checksum = load16(output + off + 6);
        packet_checksums(output, length, off, IPPROTO_UDP);
        assert(load16(output + off + 6) == checksum);
        close(sink);
        assert(buffers.buffer[PACKET_INPUT].data != buffers.buffer[PACKET_OUTPUT].data);
        assert(buffers.buffer[PACKET_INPUT].capacity == UDP6_MAXMSG);
        assert(buffers.buffer[PACKET_OUTPUT].capacity <= 65535);
        // A rejected DNS reply must not invalidate the shared receive buffer.
        uint8_t dns[12] = {0};
        s.udp.dest = htons(53);
        store16(dns + 4, 2);
        assert(send(upstream[1], dns, sizeof(dns), 0) == sizeof(dns));
        check_udp_socket(&running, &event);
        assert(s.udp.state == UDP_FINISHING && !is_readable(tunnel[0]) && allocations == 2);
        s.udp.state = UDP_ACTIVE;
        dns[2] = 0x80; store16(dns + 4, 0);
        assert(send(upstream[1], dns, sizeof(dns), 0) == sizeof(dns));
        check_udp_socket(&running, &event);
        readable(tunnel[0]);
        assert(read(tunnel[0], output, 65536) == off + 8 + sizeof(dns));
        assert(memcmp(output + off + 8, dns, sizeof(dns)) == 0 && allocations == 2);
        clear_packet_buffers(&buffers);
        free(data); free(output);
        close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_lazy_udp_logging(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    real_udp_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        struct ng_session s = {.protocol = IPPROTO_UDP, .socket = -1};
        s.udp.version = version; s.udp.time = time(NULL); s.udp.state = UDP_ACTIVE;
        s.udp.source = htons(12345); s.udp.dest = htons(5000);
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                version == 4 ? "203.0.113.2" : "2001:db8::2", &s.udp.daddr) == 1);
        address_formats = 0;
        assert(check_udp_session(&running, &s, 0, SESSION_MAX) == 0);
        uint8_t data[] = {1, 2, 3};
        assert(write_udp(&running, &s.udp, data, sizeof(data)) >= 0);
        uint8_t output[128];
        tun_packet(tunnel[0], output, version, IPPROTO_UDP);
        assert(address_formats == 0 && allocations == 0);
        // Accounting still needs the destination even with informational logs disabled.
        s.udp.state = UDP_CLOSED; s.udp.sent = 7;
        uint64_t previous = accounted_sent;
        assert(check_udp_session(&running, &s, 0, SESSION_MAX) == 0);
        assert(address_formats == 1 && accounted_sent == previous + 7 && s.udp.sent == 0);
    }
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_udp_dns_counts(int version, int response) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .tun = tunnel[1]};
    real_udp_packets = 1;
    _Alignas(8) uint8_t packet[2048], output[2048];
    const char *dest = version == 4 ? "203.0.113.2" : "2001:db8::2";
    if (!response) {
        const uint16_t counts[] = {2, UINT16_MAX, 0, 1, 2};
        for (size_t i = 0; i < sizeof(counts) / sizeof(*counts); i++) {
            size_t dns_len = i == 3 ? 17 : 12;
            size_t off = ip_header(packet, version, IPPROTO_UDP, 8 + dns_len, dest);
            struct udphdr *udp = (void *) (packet + off);
            udp->source = htons(12345); udp->dest = htons(53);
            udp->len = htons(8 + dns_len);
            uint8_t *dns = packet + off + 8;
            store16(dns, 1234); dns[2] = 1; dns[3] = 0x10; // RD and CD.
            if (i == 4) dns[2] |= 5 << 3; // UPDATE is outside RFC 9619's QUERY rule.
            store16(dns + 4, counts[i]);
            if (i == 3) { store16(dns + 13, 1); store16(dns + 15, 1); }
            expect_destination(version, dest, 53);
            assert(handle_udp(&args, packet, off + 8 + dns_len, packet + off, own_uid, NULL, -1) == 1);
            if (i < 2) {
                assert(ctx.ng_session == NULL && sockets == 0 && allocations == 0 && destination_calls == 0);
                size_t len = tun_packet(tunnel[0], output, version, IPPROTO_UDP);
                assert(len == off + 8 + 12);
                const struct udphdr *reply_udp = (const void *) (output + off);
                assert(reply_udp->source == htons(53) && reply_udp->dest == htons(12345));
                const uint8_t *reply = output + off + 8;
                assert(load16(reply) == 1234 && reply[2] == 0x81 && reply[3] == 0x11);
                for (int field = 4; field < 12; field += 2) assert(load16(reply + field) == 0);
            } else {
                struct ng_session *s = ctx.ng_session;
                assert(s != NULL && destination_calls == 1 && !is_readable(tunnel[0]));
                close(s->socket);
                ng_free(s, __FILE__, __LINE__);
                ctx.ng_session = NULL;
            }
        }
    } else {
        int upstream[2];
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
        real_network = 1;
        struct ng_session s = {.protocol = IPPROTO_UDP, .socket = upstream[0]};
        s.udp.version = version; s.udp.source = htons(12345); s.udp.dest = htons(53); s.udp.mss = 1500;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        const uint16_t counts[] = {0, 2, UINT16_MAX};
        for (size_t i = 0; i < sizeof(counts) / sizeof(*counts); i++) {
            uint8_t dns[12] = {0};
            dns[2] = 0x81; store16(dns + 4, counts[i]);
            s.udp.state = UDP_ACTIVE;
            assert(send(upstream[1], dns, sizeof(dns), 0) == sizeof(dns));
            check_udp_socket(&args, &event);
            if (i == 0) {
                tun_packet(tunnel[0], output, version, IPPROTO_UDP);
                assert(memcmp(output + (version == 4 ? 28 : 48), dns, sizeof(dns)) == 0);
            }
            assert(!is_readable(tunnel[0]) && resolved_calls == 0 && allocations == 0);
            assert(s.udp.state == UDP_FINISHING);
        }
        close(upstream[0]); close(upstream[1]);
    }
    assert(allocations == 0 && sockets == 0);
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_udp_destination(const struct arguments *args) {
    for (int version = 4; version <= 6; version += 2) {
        const char *direct = version == 4 ? "198.51.100.1" : "fe80::1";
        const char *target = version == 4 ? "127.0.0.1" : "::1";
        for (int redirected = 0; redirected <= 1; redirected++) {
            _Alignas(8) uint8_t packet[64];
            size_t off = ip_header(packet, version, IPPROTO_UDP, 9, direct);
            struct udphdr *udp = (void *) (packet + off);
            udp->source = htons(1234);
            udp->dest = htons(5000);
            udp->len = htons(9);
            struct allowed redirect = {.rport = 5354};
            strcpy(redirect.raddr, target);
            expect_destination(version, redirected ? target : direct, redirected ? 5354 : 5000);
            assert(handle_udp(args, packet, off + 9, packet + off, own_uid,
                               redirected ? &redirect : NULL, -1) == 1);
            assert(destination_calls == 1 && args->ctx->ng_session->udp.sent == 1);
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0);
        }
    }
}

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
    packet_checksums(packet, len, off, IPPROTO_UDP);
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
