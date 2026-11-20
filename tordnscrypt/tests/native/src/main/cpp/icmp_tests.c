#include "test_support.h"

#ifndef INVIZIBLE_BASELINE_TESTS
static void assert_icmp_checksum(const uint8_t *packet, size_t length, size_t off, int protocol) {
    // Recalculate with the independent byte-oriented reference, not production SIMD code.
    uint8_t expected[2048];
    assert(length <= sizeof(expected));
    memcpy(expected, packet, length);
    packet_checksums(expected, length, off, protocol);
    assert(memcmp(expected + off, packet + off, length - off) == 0);
}
#endif

void test_icmp_short_replies(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        for (int reuse = 0; reuse < 2; reuse++) {
            int upstream[2], tunnel[2];
            assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
            assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            struct packet_buffers buffers = {0};
            struct arguments running = *args;
            running.tun = tunnel[1];
            running.buffers = reuse ? &buffers : NULL;
            struct ng_session s = {.protocol = IPPROTO_ICMP, .socket = upstream[0]};
            s.icmp.version = version; s.icmp.id = htons(12345);
            struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
            uint8_t reply[ICMP_MINLEN] = {0};
            reply[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY;
            store16(reply + 4, 54321);
            for (size_t short_length = 1; short_length < ICMP_MINLEN; short_length++) {
                assert(send(upstream[1], reply, short_length, 0) == short_length);
                check_icmp_socket(&running, &event);
                assert(!s.icmp.stop && !is_readable(tunnel[0]));
                // A valid reply must still work after every dropped datagram,
                // including when the scratch buffer contains an earlier reply.
                assert(send(upstream[1], reply, sizeof(reply), 0) == sizeof(reply));
                check_icmp_socket(&running, &event);
                _Alignas(8) uint8_t output[2048];
                int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                size_t length = tun_packet(tunnel[0], output, version, protocol);
                size_t off = version == 4 ? 20 : 40;
                assert(length == off + sizeof(reply) && !s.icmp.stop);
                assert(load16(output + off + 4) == 12345);
                assert_icmp_checksum(output, length, off, protocol);
                assert(allocations == (reuse ? 2 : 0));
            }
            // Preserve the existing zero-length receive stop behavior.
            assert(send(upstream[1], reply, 0, 0) == 0);
            check_icmp_socket(&running, &event);
            assert(s.icmp.stop && !is_readable(tunnel[0]));
            clear_packet_buffers(&buffers);
            close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
            assert(allocations == 0 && sockets == 0);
        }
    }
#endif
}

void test_icmp6_forward_checksums(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    const size_t lengths[] = {8, 19, 40, 41, 1400};
    for (int extensions = 0; extensions < 2; extensions++) {
        for (size_t i = 0; i < sizeof(lengths) / sizeof(*lengths); i++) {
            _Alignas(8) uint8_t packet[1500];
            size_t off = ip_header(packet, 6, extensions ? IPPROTO_HOPOPTS : IPPROTO_ICMPV6,
                                   lengths[i] + (extensions ? 8 : 0), "2001:db8::2");
            if (extensions) {
                packet[off] = IPPROTO_ICMPV6;
                packet[off + 1] = 0; // Eight-byte Hop-by-Hop header.
                off += 8;
            }
            packet[off] = ICMP6_ECHO_REQUEST;
            store16(packet + off + 4, 12345);
            store16(packet + off + 6, (uint16_t) i);
            memset(packet + off + 8, 0xa5, lengths[i] - 8);
            expect_destination(6, "2001:db8::2", 0);
            assert(handle_icmp(args, packet, off + lengths[i], packet + off, own_uid, epoll_fd) == 1);
            assert(load16(packet + off + 4) == (uint16_t) ~UINT16_C(12345));
            assert_icmp_checksum(packet, off + lengths[i], off, IPPROTO_ICMPV6);
            assert(destination_calls == 1);
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0);
        }
    }
    close(epoll_fd);
#endif
}

void test_icmp_packet_bounds(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        _Alignas(8) uint8_t packet[64] = {0};
        size_t off = ip_header(packet, version,
                version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6, ICMP_MINLEN,
                version == 4 ? "127.0.0.1" : "::1");
        for (size_t length = 0; length < ICMP_MINLEN; length++) {
            assert(handle_icmp(args, packet, off + length, packet + off, own_uid, -1) == 0);
            assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
        }
        assert(handle_icmp(args, packet, off, packet + off + ICMP_MINLEN, own_uid, -1) == 0);
        assert(handle_icmp(args, packet, 0, packet, own_uid, -1) == 0);
        assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
    }
#endif
}

void test_icmp_buffer_reuse(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        int upstream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        struct packet_buffers buffers = {0};
        struct arguments running = *args;
        running.tun = tunnel[1]; running.buffers = &buffers;
        struct ng_session s = {.protocol = IPPROTO_ICMP, .socket = upstream[0]};
        s.icmp.version = version; s.icmp.id = htons(12345);
        if (version == 6) {
            assert(inet_pton(AF_INET6, "2001:db8::1", &s.icmp.saddr.ip6) == 1);
            assert(inet_pton(AF_INET6, "2001:db8::2", &s.icmp.daddr.ip6) == 1);
        }
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        const size_t lengths[] = {8, 19, 39, 40, 41, 1400, 11};
        for (size_t i = 0; i < sizeof(lengths) / sizeof(*lengths); i++) {
            _Alignas(8) uint8_t data[1400] = {0}, output[2048];
            data[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY;
            store16(data + 4, 54321); store16(data + 6, (uint16_t) i);
            memset(data + 8, (int) i + 1, lengths[i] - 8);
            assert(send(upstream[1], data, lengths[i], 0) == lengths[i]);
            check_icmp_socket(&running, &event);
            size_t length = tun_packet(tunnel[0], output, version,
                                      version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
            size_t off = version == 4 ? 20 : 40;
            assert_icmp_checksum(output, length, off,
                                 version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
            assert(length == off + lengths[i] && !s.icmp.stop && allocations == 2);
            assert(load16(output + off + 4) == 12345 && load16(output + off + 6) == i);
            assert(memcmp(output + off + 8, data + 8, lengths[i] - 8) == 0);
        }
        clear_packet_buffers(&buffers);
        close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_icmp_destination(const struct arguments *args) {
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    for (int version = 4; version <= 6; version += 2) {
        const char *dest = version == 4 ? "198.51.100.1" : "fe80::1";
        _Alignas(8) uint8_t packet[64];
        size_t off = ip_header(packet, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6,
                               8, dest);
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        expect_destination(version, dest, 0);
        assert(handle_icmp(args, packet, off + 8, packet + off, own_uid, epoll_fd) == 1);
        assert(destination_calls == 1);
        clear(args->ctx);
        assert(allocations == 0 && sockets == 0);
    }
    close(epoll_fd);
}

void test_icmp_allocation_failure(const struct arguments *args) {
    uint8_t packet[64] = {0};
    for (int version = 4; version <= 6; version += 2) {
        size_t off = ip_header(packet, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6,
                               8, version == 4 ? "198.51.100.1" : "2001:db8::2");
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        fail_alloc_tag = "icmp session";
        fail_alloc_match = 1;
        assert(handle_icmp(args, packet, off + 8, packet + off, own_uid, -1) == 0);
        assert(fail_alloc_match == 0);
        assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
        int upstream[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
        struct ng_session s = {.socket = upstream[0]};
        s.icmp.version = version;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        fail_alloc_tag = "icmp socket";
        fail_alloc_match = 1;
        check_icmp_socket(args, &event);
        assert(fail_alloc_match == 0);
        assert(s.icmp.stop);
        close(upstream[0]); close(upstream[1]);
        fail_alloc_tag = version == 4 ? "icmp write4" : "icmp write6";
        fail_alloc_match = 1;
        assert(write_icmp(args, &s.icmp, packet + off, 8) == -1);
        assert(fail_alloc_match == 0);
        assert(allocations == 0);
    }
    fail_alloc_tag = NULL;
}

void test_icmp_socket_cleanup(const struct arguments *args) {
    struct icmp_session session = {.version = 4, .uid = own_uid};
    for (failure = PROTECT; failure <= SETFL; failure++) {
        assert(open_icmp_socket(args, &session) == -1);
        assert(sockets == 0);
    }
    failure = NONE;
    int fd = open_icmp_socket(args, &session);
    assert(fd >= 0 && sockets == 1);
    close(fd);
    assert(sockets == 0);
}

void icmp_packets(int version) {
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx};
    struct icmp_session s = {.version = version};
    if (version == 4) {
        inet_pton(AF_INET, "10.0.0.2", &s.saddr.ip4);
        inet_pton(AF_INET, "192.0.2.1", &s.daddr.ip4);
    } else {
        inet_pton(AF_INET6, "2001:db8::1", &s.saddr.ip6);
        inet_pton(AF_INET6, "2001:db8::2", &s.daddr.ip6);
    }
    int tunnel[2]; assert(pipe(tunnel) == 0); args.tun = tunnel[1];
    uint8_t echo[] = {version == 4 ? 0 : 129, 0, 0, 0, 0x12, 0x34, 0, 1, 0, 255};
    assert(write_icmp(&args, &s, echo, sizeof(echo)) > 0);
    _Alignas(8) uint8_t output[2048];
    size_t len = tun_packet(tunnel[0], output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    size_t off = (version == 4 ? 20 : 40);
    assert(len == off + sizeof(echo) && memcmp(output + off, echo, sizeof(echo)) == 0);
    printf("ICMP%d response=%08x\n", version, fingerprint(output, len));
    close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0);
    cases++;
}

void test_icmp_uid_update(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        for (int kind = 0; kind < 7; kind++) {
            _Alignas(8) uint8_t packet[128];
            const char *destination = version == 4 ? "203.0.113.2" : "2001:db8::2";
            int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
            size_t off = ip_header(packet, version, protocol, ICMP_MINLEN, destination);
            packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
            store16(packet + off + 4, 12345);
            struct ng_session session = {.protocol = (uint8_t) protocol, .socket = 42};
            session.icmp.version = version;
            session.icmp.uid = kind == 2 || kind == 3 ? 12345 : kind == 5 ? 0 : -1;
            session.icmp.id = htons(12345);
            memcpy(&session.icmp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
            memcpy(&session.icmp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
            if (kind == 4) session.socket = -1;
            int uid = kind == 1 || kind == 2 || kind == 5 ? -1 : kind == 3 ? 23456 : kind == 6 ? 0 : 12345;
            set_test_sessions(args->ctx, &session);
            expect_destination(version, destination, 0);
            assert(handle_icmp(args, packet, off + ICMP_MINLEN, packet + off, uid, -1) == 1);
            int expected = kind == 0 || kind == 2 || kind == 3 ? 12345 : kind >= 5 ? 0 : -1;
            assert(session.icmp.uid == expected);
            set_test_sessions(args->ctx, NULL);
            assert(allocations == 0 && sockets == 0);
        }
    }
#endif
}

void test_icmp_blocked_unreachable(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        int tunnel[2]; assert(pipe(tunnel) == 0);
        struct arguments running = *args; running.tun = tunnel[1];
        _Alignas(8) uint8_t packet[1600], output[2048];
        size_t off = ip_header(packet, version, IPPROTO_UDP, 25,
                               version == 4 ? "192.0.2.1" : "2001:db8::2");
        store16(packet + off, 12345); store16(packet + off + 2, 54321);
        store16(packet + off + 4, 25);
        memset(packet + off + 8, 0xa5, 17);
        packet_checksums(packet, off + 25, off, IPPROTO_UDP);
        block_udp(&running, packet, off + 25, packet + off, test_owner(own_uid));
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        size_t length = tun_packet(tunnel[0], output, version, protocol);
        assert(length == off + 8 + off + 25);
        assert(output[off] == (version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH));
        assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_HOST : ICMP6_DST_UNREACH_NOROUTE));
        assert(memcmp(output + off + 4, "\0\0\0\0", 4) == 0);
        assert(memcmp(output + off + 8, packet, off + 25) == 0);
        assert(memcmp(output + (version == 4 ? 12 : 8), packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16) == 0);
        assert(memcmp(output + (version == 4 ? 16 : 24), packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16) == 0);
        assert_icmp_checksum(output, length, off, protocol);
        clear(args->ctx);
        close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_icmp_upstream_unreachable(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int extension = 0; extension < 3; extension++)
            for (int redirected = 0; redirected <= 1; redirected++) {
                int tunnel[2]; assert(pipe(tunnel) == 0);
                struct arguments running = *args; running.tun = tunnel[1];
                _Alignas(8) uint8_t packet[512], original[512], output[2048];
                size_t extra = extension == 0 ? 0 : version == 4 ? (extension == 1 ? 4 : 40)
                                                                 : (extension == 1 ? 8 : 96);
                size_t base = ip_header(packet, version, version == 6 && extra ? IPPROTO_HOPOPTS : IPPROTO_UDP,
                                        extra + 208, version == 4 ? "192.0.2.1" : "2001:db8::2");
                if (extra) {
                    if (version == 4) {
                        packet[0] = (uint8_t) (0x45 + extra / 4);
                        memset(packet + base, 1, extra); // IPv4 NOP options.
                    } else {
                        memset(packet + base, 0, extra);
                        packet[base] = IPPROTO_UDP;
                        packet[base + 1] = (uint8_t) (extra / 8 - 1);
                    }
                }
                size_t off = base + extra, length = off + 208;
                store16(packet + off, 12345); store16(packet + off + 2, 5000);
                store16(packet + off + 4, 208);
                memset(packet + off + 8, 0xa5, 200);
                packet_checksums(packet, length, off, IPPROTO_UDP);
                memcpy(original, packet, length);
                struct allowed redirect = {.rport = 6000};
                strcpy(redirect.raddr, "192.0.2.99");
                expect_destination(redirected ? 4 : version,
                                   redirected ? "192.0.2.99" : version == 4 ? "192.0.2.1" : "2001:db8::2",
                                   redirected ? 6000 : 5000);
                assert(handle_udp(&running, packet, length, packet + off, test_owner(own_uid),
                                  redirected ? &redirect : NULL, -1) == 1);
                struct ng_session *session = args->ctx->ng_session;
                assert(session != NULL && allocations == 1 && sockets == 1);
                memset(packet, 0xcc, sizeof(packet)); // The shared input is no longer available.
                const int errors[] = {ECONNREFUSED, EHOSTUNREACH};
                for (size_t e = 0; e < sizeof(errors) / sizeof(*errors); e++) {
                    socket_error_fd = session->socket; socket_error_value = errors[e];
                    session->udp.state = UDP_ACTIVE;
                    struct epoll_event event = {.events = EPOLLERR, .data.ptr = session};
                    check_udp_socket(&running, &event);
                    assert(session->udp.state == UDP_FINISHING);
                    if (off + 8 > UDP_ERROR_QUOTE) {
                        assert(!is_readable(tunnel[0]));
                        continue;
                    }
                    int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                    size_t received = tun_packet(tunnel[0], output, version, protocol);
                    assert(received == base + 8 + UDP_ERROR_QUOTE);
                    assert(output[base] == (version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH));
                    assert(output[base + 1] == (version == 4
                           ? (e == 0 ? ICMP_UNREACH_PORT : ICMP_UNREACH_HOST)
                           : (e == 0 ? ICMP6_DST_UNREACH_NOPORT : ICMP6_DST_UNREACH_NOROUTE)));
                    assert(memcmp(output + base + 8, original, UDP_ERROR_QUOTE) == 0);
                    assert_icmp_checksum(output, received, base, protocol);
                }
                socket_error_fd = -1;
                clear(args->ctx);
                close(tunnel[0]); close(tunnel[1]);
                assert(allocations == 0 && sockets == 0);
            }
#endif
}

void test_icmp_unreachable_bounds(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        int tunnel[2]; assert(pipe(tunnel) == 0);
        struct arguments running = *args; running.tun = tunnel[1];
        _Alignas(8) uint8_t packet[1600], output[2048];
        size_t off = ip_header(packet, version, IPPROTO_UDP, 1500,
                               version == 4 ? "192.0.2.1" : "2001:db8::2");
        store16(packet + off, 12345); store16(packet + off + 2, 54321);
        store16(packet + off + 4, 1500);
        memset(packet + off + 8, 0xa5, 1492);
        packet_checksums(packet, off + 1500, off, IPPROTO_UDP);
        for (size_t length = 0; length < off + 8; length++)
            write_connection_unreach(&running, packet, length, ECONNREFUSED);
        write_connection_unreach(&running, NULL, 0, ECONNREFUSED);
        assert(!is_readable(tunnel[0]) && allocations == 0);
        write_connection_unreach(&running, packet, off + 1500, ECONNREFUSED);
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        size_t length = tun_packet(tunnel[0], output, version, protocol);
        size_t quoted = version == 4 ? 548 : 1232;
        assert(length == off + 8 + quoted);
        assert(memcmp(output + off + 8, packet, quoted) == 0);
        assert_icmp_checksum(output, length, off, protocol);
        fail_alloc_tag = version == 4 ? "icmp write4" : "icmp write6";
        fail_alloc_match = 1;
        write_connection_unreach(&running, packet, off + 1500, ECONNREFUSED);
        assert(fail_alloc_match == 0 && allocations == 0 && !is_readable(tunnel[0]));
        fail_alloc_tag = NULL;
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                         version == 4 ? "224.0.0.1" : "ff02::1", packet + (version == 4 ? 16 : 24)) == 1);
        write_connection_unreach(&running, packet, off + 1500, ECONNREFUSED);
        assert(!is_readable(tunnel[0]) && allocations == 0);
        close(tunnel[0]); close(tunnel[1]);
    }
#endif
}

void test_icmp_echo_validation(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    for (int version = 4; version <= 6; version += 2) {
        for (int kind = 0; kind < 7; kind++) {
            _Alignas(8) uint8_t packet[128];
            size_t off = ip_header(packet, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6,
                                   ICMP_MINLEN, version == 4 ? "192.0.2.1" : "2001:db8::2");
            const uint8_t types[] = {version == 4 ? ICMP6_ECHO_REQUEST : ICMP_ECHO,
                                    version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                                    version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                                    version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY,
                                    133, version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH, 255};
            packet[off] = types[kind];
            packet[off + 1] = kind == 1 ? 1 : kind == 2 ? 255 : 0;
            assert(handle_icmp(args, packet, off + ICMP_MINLEN, packet + off, own_uid, -1) == 0);
            assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
        }
        _Alignas(8) uint8_t packet[128];
        size_t off = ip_header(packet, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6,
                               ICMP_MINLEN, version == 4 ? "192.0.2.1" : "2001:db8::2");
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        expect_destination(version, version == 4 ? "192.0.2.1" : "2001:db8::2", 0);
        assert(handle_icmp(args, packet, off + ICMP_MINLEN, packet + off, own_uid, epoll_fd) == 1);
        clear(args->ctx);
        assert(allocations == 0 && sockets == 0);
    }
    close(epoll_fd);
#endif
}

void test_icmp_epoll_cleanup(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        struct arguments running = *args; running.tun = tunnel[1];
        _Alignas(8) uint8_t packet[128], original[128], output[2048];
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        size_t off = ip_header(packet, version, protocol, ICMP_MINLEN,
                               version == 4 ? "192.0.2.1" : "2001:db8::2");
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        store16(packet + off + 4, 12345); store16(packet + off + 6, 1);
        packet_checksums(packet, off + ICMP_MINLEN, off, protocol);
        memcpy(original, packet, off + ICMP_MINLEN);
        expect_destination(version, version == 4 ? "192.0.2.1" : "2001:db8::2", 0);
        for (int attempt = 0; attempt < 8; attempt++) {
            // A real EBADF from epoll_ctl, without mocking its result.
            assert(handle_icmp(&running, packet, off + ICMP_MINLEN, packet + off, own_uid, -1) == 0);
            assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0 && last_socket == -1);
            assert(destination_calls == 0 && !is_readable(tunnel[0]));
            assert(memcmp(packet, original, off + ICMP_MINLEN) == 0);
            assert(find_session(args->ctx, version, protocol,
                                packet + (version == 4 ? 12 : 8), htons(12345),
                                packet + (version == 4 ? 16 : 24), 0) == NULL);
            assert_test_session_index(args->ctx);
        }
        int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
        assert(handle_icmp(&running, packet, off + ICMP_MINLEN, packet + off, own_uid, epoll_fd) == 1);
        struct ng_session *session = args->ctx->ng_session;
        assert(session != NULL && allocations == 1 && sockets == 1 && destination_calls == 1);
        // Deliver a reply through the pollable local socket and real epoll.
        struct sockaddr_in local = {.sin_family = AF_INET, .sin_addr.s_addr = htonl(INADDR_LOOPBACK)};
        assert(bind(session->socket, (struct sockaddr *) &local, sizeof(local)) == 0);
        socklen_t address_length = sizeof(local);
        assert(getsockname(session->socket, (struct sockaddr *) &local, &address_length) == 0);
        // Use the real socket constructor because ordinary UDP fixtures use /dev/null.
        int sender = __real_socket(AF_INET, SOCK_DGRAM | SOCK_CLOEXEC, 0); assert(sender >= 0);
        uint8_t reply[ICMP_MINLEN] = {version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY};
        store16(reply + 4, 54321); store16(reply + 6, 1);
        extern ssize_t __real_sendto(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
        ssize_t sent = __real_sendto(sender, reply, sizeof(reply), 0, (struct sockaddr *) &local, sizeof(local));
        if (sent != sizeof(reply)) {
            char detail[128];
            snprintf(detail, sizeof(detail), "ICMP local UDP reply send returned %zd errno %d: %s",
                     sent, errno, strerror(errno));
            native_test_fail(detail, __FILE__, __LINE__);
        }
        struct epoll_event event;
        assert(epoll_wait(epoll_fd, &event, 1, 100) == 1 && event.data.ptr == session);
        check_icmp_socket(&running, &event);
        assert(tun_packet(tunnel[0], output, version, protocol) == off + ICMP_MINLEN);
        assert(output[off] == reply[0] && load16(output + off + 4) == 12345 && !session->icmp.stop);
        assert_icmp_checksum(output, off + ICMP_MINLEN, off, protocol);
        clear(args->ctx);
        assert_test_session_index(args->ctx);
        close(sender); close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
#include <linux/errqueue.h>

static struct {
    int fd, version, remaining, calls, mode;
    struct sock_extended_err error;
    uint8_t payload[32];
    size_t length;
} queued_error;

static ssize_t receive_error(int fd, struct msghdr *message, int flags) {
    assert(fd == queued_error.fd && (flags & MSG_DONTWAIT));
    queued_error.calls++;
    if (queued_error.remaining-- <= 0) { errno = EAGAIN; return -1; }
    assert(message->msg_controllen >= CMSG_SPACE(sizeof(struct sock_extended_err)));
    struct cmsghdr *cmsg = CMSG_FIRSTHDR(message);
    cmsg->cmsg_level = queued_error.version == 4 ? IPPROTO_IP : IPPROTO_IPV6;
    cmsg->cmsg_type = queued_error.version == 4 ? IP_RECVERR : IPV6_RECVERR;
    cmsg->cmsg_len = CMSG_LEN(sizeof(struct sock_extended_err));
    memcpy(CMSG_DATA(cmsg), &queued_error.error, sizeof(queued_error.error));
    message->msg_controllen = CMSG_SPACE(sizeof(struct sock_extended_err));
    if (queued_error.mode == 1) message->msg_flags |= MSG_CTRUNC;
    if (queued_error.mode == 2) cmsg->cmsg_len--;
    if (queued_error.mode == 8) cmsg->cmsg_len = message->msg_controllen + 1;
    assert(message->msg_iov[0].iov_len >= queued_error.length);
    memcpy(message->msg_iov[0].iov_base, queued_error.payload, queued_error.length);
    return (ssize_t) queued_error.length;
}

static void queue_error(int fd, int version, int serr, uint32_t mtu) {
    memset(&queued_error, 0, sizeof(queued_error));
    queued_error.fd = fd; queued_error.version = version; queued_error.remaining = 1;
    queued_error.error.ee_errno = (uint32_t) serr;
    queued_error.error.ee_info = mtu;
    queued_error.error.ee_origin = version == 4 ? SO_EE_ORIGIN_ICMP : SO_EE_ORIGIN_ICMP6;
    error_queue_recv = receive_error;
}

static struct ng_session *error_flow(const struct arguments *args, int version, bool ping,
                                    int epoll_fd, uint8_t *original, size_t *length) {
    uint8_t packet[128];
    int protocol = ping ? (version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6) : IPPROTO_UDP;
    size_t off = ip_header(packet, version, protocol, 24,
                           version == 4 ? "192.0.2.1" : "2001:db8::2");
    memset(packet + off, 0, 24);
    if (ping) {
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        store16(packet + off + 4, 12345); store16(packet + off + 6, 7);
    } else {
        store16(packet + off, 12345); store16(packet + off + 2, 5000);
        store16(packet + off + 4, 24);
    }
    memset(packet + off + 8, 0xa5, 16);
    *length = off + 24;
    packet_checksums(packet, *length, off, protocol);
    memcpy(original, packet, *length);
    expect_destination(version, version == 4 ? "192.0.2.1" : "2001:db8::2", ping ? 0 : 5000);
    assert((ping ? handle_icmp(args, packet, *length, packet + off, own_uid, epoll_fd) :
                   handle_udp(args, packet, *length, packet + off, test_owner(own_uid), NULL, epoll_fd)) == 1);
    assert(args->ctx->ng_session != NULL);
    return args->ctx->ng_session;
}

static void check_error_event(const struct arguments *args, struct ng_session *s, bool ping) {
    struct epoll_event event = {.events = EPOLLERR, .data.ptr = s};
    if (ping) check_icmp_socket(args, &event);
    else check_udp_socket(args, &event);
}
#endif

void test_icmp_error_queue(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    for (int version = 4; version <= 6; version += 2)
        for (int ping = 0; ping < 2; ping++) {
            int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            struct arguments running = *args; running.tun = tunnel[1];
            uint8_t original[128], output[2048]; size_t length;
            struct ng_session *s = error_flow(&running, version, ping, epoll_fd, original, &length);
            size_t off = version == 4 ? 20 : 40;
            int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
            const int errors[] = {EMSGSIZE, EHOSTUNREACH, ECONNREFUSED, ENETUNREACH, EACCES};
            for (size_t i = 0; i < sizeof(errors) / sizeof(*errors); i++) {
                if (ping) s->icmp.stop = 0; else s->udp.state = UDP_ACTIVE;
                queue_error(s->socket, version, errors[i], 1400);
                if (i == 0) {
                    queued_error.length = ping ? 24 : 16;
                    memcpy(queued_error.payload, original + off + (ping ? 0 : 8), queued_error.length);
                    if (ping) memset(queued_error.payload, 0xbb, 6); // Kernel-rewritten fields.
                }
                if (i == 1) queued_error.error.ee_type = version == 4 ? ICMP_TIMXCEED : ICMP6_TIME_EXCEEDED;
                check_error_event(&running, s, ping);
                size_t bytes = tun_packet(tunnel[0], output, version, protocol);
                assert(bytes == off + 8 + length);
                assert(memcmp(output + off + 8, original, length) == 0);
                assert_icmp_checksum(output, bytes, off, protocol);
                assert(queued_error.calls == 2);
                if (i == 0) {
                    assert(output[off] == (version == 4 ? ICMP_UNREACH : ICMP6_PACKET_TOO_BIG));
                    assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_NEEDFRAG : 0));
                    assert(load16(output + off + 6) == 1400);
                    if (version == 6) assert(load16(output + off + 4) == 0);
                    assert(ping ? !s->icmp.stop : s->udp.state == UDP_ACTIVE);
                } else {
                    assert(ping ? s->icmp.stop : s->udp.state == UDP_FINISHING);
                    if (i == 1) assert(output[off] == (version == 4 ? ICMP_TIMXCEED : ICMP6_TIME_EXCEEDED));
                    if (i == 2) assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_PORT : ICMP6_DST_UNREACH_NOPORT));
                    if (i == 3) assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_NET : ICMP6_DST_UNREACH_NOROUTE));
                    if (i == 4) assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_FILTER_PROHIB : ICMP6_DST_UNREACH_ADMIN));
                }
            }
            error_queue_recv = NULL;
            clear(args->ctx); close(tunnel[0]); close(tunnel[1]);
            assert(allocations == 0 && sockets == 0);
        }
    close(epoll_fd);
#endif
}

void test_icmp_error_rate_limit(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    struct arguments running = *args; running.tun = tunnel[1];
    test_monotonic_enabled = true; test_monotonic_ms = 0;
    // Exhaust ordinary errors first, then verify MTU feedback has its own budget.
    for (int mtu = 0; mtu < 2; mtu++) {
        for (int stage = 0; stage < 7; stage++) {
            if (stage == 1) test_monotonic_ms += 99;
            if (stage == 2) test_monotonic_ms++;
            if (stage == 3) test_monotonic_ms -= 50; // No refill on clock rollback.
            if (stage == 4) test_monotonic_failures = -1;
            if (stage == 5) {
                test_monotonic_failures = 0;
                test_monotonic_ms += 10000; // Idle refill is capped at the burst size.
            }
            if (stage == 6) clear(args->ctx); // A reused context starts with a fresh budget.
            int expected = stage == 0 || stage >= 5 ? 20 : stage == 2 ? 1 : 0;
            for (int i = 0; i < 25; i++) {
                int version = i % 2 ? 6 : 4;
                uint8_t packet[128], output[2048];
                size_t off = ip_header(packet, version, IPPROTO_UDP, 24,
                                       version == 4 ? "192.0.2.1" : "2001:db8::2");
                store16(packet + off, 12345); store16(packet + off + 2, 5000);
                store16(packet + off + 4, 24);
                packet_checksums(packet, off + 24, off, IPPROTO_UDP);
                if (mtu) {
                    queue_error(77, version, EMSGSIZE, 1400);
                    assert(forward_socket_errors(&running, 77, packet, off + 24) == 0);
                    assert(queued_error.calls == 2); // Suppression still drains the queue.
                } else
                    write_connection_unreach(&running, packet, off + 24, ECONNREFUSED);
                assert(is_readable(tunnel[0]) == (i < expected));
                if (i < expected) {
                    int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                    size_t bytes = tun_packet(tunnel[0], output, version, protocol);
                    assert_icmp_checksum(output, bytes, off, protocol);
                    assert(memcmp(output + off + 8, packet, off + 24) == 0);
                }
                assert(allocations == 0 && sockets == 0);
            }
        }
        // Echo replies use their normal forwarding path even after errors are capped.
        struct icmp_session echo = {.version = 4};
        uint8_t reply[8] = {ICMP_ECHOREPLY}, output[128];
        assert(write_icmp(&running, &echo, reply, sizeof(reply)) > 0);
        tun_packet(tunnel[0], output, 4, IPPROTO_ICMP);
        // Leave the ordinary bucket exhausted when starting the MTU checks.
    }
    error_queue_recv = NULL;
    test_monotonic_enabled = false; test_monotonic_failures = 0;
    clear(args->ctx); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_icmp_error_queue_bounds(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    struct arguments running = *args; running.tun = tunnel[1];
    uint8_t original[128], output[2048]; size_t length;
    struct ng_session *s = error_flow(&running, 6, false, -1, original, &length);
    for (int mode = 0; mode < 9; mode++) {
        queue_error(s->socket, 6, EMSGSIZE, 1400);
        if (mode == 0) queued_error.error.ee_info = 0;
        if (mode == 1 || mode == 2) queued_error.mode = mode;
        if (mode == 3) { queued_error.length = 16; memset(queued_error.payload, 0xbb, 16); }
        if (mode == 4) queued_error.error.ee_origin = SO_EE_ORIGIN_NONE;
        if (mode == 5) queued_error.version = 4; // Cross-family redirect MTU cannot be copied.
        if (mode == 6) s->udp.error_quote_length = 0;
        if (mode == 7) {
            s->udp.error_quote_length = (uint16_t) length;
            s->udp.error_quote[6] = IPPROTO_HOPOPTS;
            s->udp.error_quote[41] = 255; // Truncated extension header.
        }
        if (mode == 8) { memcpy(s->udp.error_quote, original, length); queued_error.mode = 8; }
        check_error_event(&running, s, false);
        assert(!is_readable(tunnel[0]) && s->udp.state == UDP_ACTIVE);
    }
    s->udp.error_quote_length = (uint16_t) length;
    queue_error(s->socket, 6, EMSGSIZE, 1400); queued_error.remaining = 9;
    check_error_event(&running, s, false);
    assert(queued_error.calls == 8 && queued_error.remaining == 1);
    for (int i = 0; i < 8; i++) tun_packet(tunnel[0], output, 6, IPPROTO_ICMPV6);
    check_error_event(&running, s, false);
    tun_packet(tunnel[0], output, 6, IPPROTO_ICMPV6);
    assert(queued_error.calls == 10 && !is_readable(tunnel[0]) && s->udp.state == UDP_ACTIVE);
    // Account for the original IP options/extension header omitted by sendto.
    for (int version = 4; version <= 6; version += 2) {
        uint8_t packet[128];
        size_t base = ip_header(packet, version, version == 4 ? IPPROTO_UDP : IPPROTO_HOPOPTS,
                                32, version == 4 ? "192.0.2.1" : "2001:db8::2");
        if (version == 4) { packet[0] = 0x47; memset(packet + base, 1, 8); }
        else { packet[base] = IPPROTO_UDP; packet[base + 1] = 0; }
        size_t off = base + 8;
        store16(packet + off, 12345); store16(packet + off + 2, 5000);
        store16(packet + off + 4, 24); memset(packet + off + 8, 0xa5, 16);
        packet_checksums(packet, off + 24, off, IPPROTO_UDP);
        queue_error(s->socket, version, EMSGSIZE, 1400);
        assert(forward_socket_errors(&running, s->socket, packet, off + 24) == 0);
        size_t bytes = tun_packet(tunnel[0], output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
        assert(load16(output + base + 6) == 1408);
        assert(memcmp(output + base + 8, packet, off + 24) == 0);
        assert_icmp_checksum(output, bytes, base, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    }
    error_queue_recv = NULL; clear(args->ctx); close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_icmp_send_mtu(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    for (int version = 4; version <= 6; version += 2)
        for (int ping = 0; ping < 2; ping++) {
            int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            struct arguments running = *args; running.tun = tunnel[1];
            uint8_t original[128], packet[128], output[2048]; size_t length;
            struct ng_session *s = error_flow(&running, version, ping, epoll_fd, original, &length);
            size_t off = version == 4 ? 20 : 40;
            queue_error(s->socket, version, EMSGSIZE, 1400);
            queued_error.error.ee_origin = SO_EE_ORIGIN_LOCAL;
            sendto_error_fd = s->socket; sendto_error_value = EMSGSIZE;
            memcpy(packet, original, length);
            assert((ping ? handle_icmp(&running, packet, length, packet + off, own_uid, epoll_fd) :
                    handle_udp(&running, packet, length, packet + off, test_owner(own_uid), NULL, epoll_fd)) == 0);
            int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
            size_t bytes = tun_packet(tunnel[0], output, version, protocol);
            assert(memcmp(output + off + 8, original, length) == 0);
            assert_icmp_checksum(output, bytes, off, protocol);
            assert(ping ? !s->icmp.stop : s->udp.state == UDP_ACTIVE);
            error_queue_recv = NULL;
            // Retry a smaller request using the same indexed session and socket.
            memcpy(packet, original, length); length -= 8;
            if (version == 4) store16(packet + 2, (uint16_t) length);
            else store16(packet + 4, (uint16_t) (length - 40));
            if (!ping) store16(packet + off + 4, (uint16_t) (length - off));
            packet_checksums(packet, length, off, ping ? protocol : IPPROTO_UDP);
            int fd = s->socket;
            assert((ping ? handle_icmp(&running, packet, length, packet + off, own_uid, epoll_fd) :
                    handle_udp(&running, packet, length, packet + off, test_owner(own_uid), NULL, epoll_fd)) == 1);
            assert(args->ctx->ng_session == s && s->socket == fd && sockets == 1 && allocations == 1);
            clear(args->ctx); close(tunnel[0]); close(tunnel[1]);
            assert(allocations == 0 && sockets == 0);
        }
    close(epoll_fd);
#endif
}

void test_icmp_real_error_queue(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    extern int __real_socket(int, int, int);
    extern ssize_t __real_sendto(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
    // Reserve then close a local port, so the kernel produces a real port-unreachable.
    for (int version = 4; version <= 6; version += 2) {
        int family = version == 4 ? AF_INET : AF_INET6;
        int reserved = __real_socket(family, SOCK_DGRAM | SOCK_CLOEXEC, 0); assert(reserved >= 0);
        struct sockaddr_storage storage = {0};
        struct sockaddr_in *a4 = (struct sockaddr_in *) &storage;
        struct sockaddr_in6 *a6 = (struct sockaddr_in6 *) &storage;
        socklen_t addrlen = version == 4 ? sizeof(*a4) : sizeof(*a6);
        if (version == 4) { a4->sin_family = AF_INET; a4->sin_addr.s_addr = htonl(INADDR_LOOPBACK); }
        else { a6->sin6_family = AF_INET6; a6->sin6_addr = in6addr_loopback; }
        assert(bind(reserved, (struct sockaddr *) &storage, addrlen) == 0);
        assert(getsockname(reserved, (struct sockaddr *) &storage, &addrlen) == 0);
        close(reserved);
        int upstream = __real_socket(family, SOCK_DGRAM | SOCK_CLOEXEC, 0); assert(upstream >= 0);
        enable_socket_errors(upstream, version);
        int tunnel[2]; assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        struct arguments running = *args; running.tun = tunnel[1];
        uint8_t packet[128], output[2048];
        size_t off = ip_header(packet, version, IPPROTO_UDP, 24, version == 4 ? "127.0.0.1" : "::1");
        store16(packet + off, 12345);
        memcpy(packet + off + 2, version == 4 ? (void *) &a4->sin_port : (void *) &a6->sin6_port, 2);
        store16(packet + off + 4, 24); memset(packet + off + 8, 0xa5, 16);
        packet_checksums(packet, off + 24, off, IPPROTO_UDP);
        ssize_t sent = __real_sendto(upstream, packet + off + 8, 16, 0, (struct sockaddr *) &storage, addrlen);
        if (sent != 16) {
            char detail[128];
            snprintf(detail, sizeof(detail), "UDP%d error-queue probe send returned %zd errno %d: %s",
                     version, sent, errno, strerror(errno));
            native_test_fail(detail, __FILE__, __LINE__);
        }
        struct pollfd ready = {.fd = upstream, .events = POLLERR};
        assert(poll(&ready, 1, 1000) == 1 && (ready.revents & POLLERR));
        assert(forward_socket_errors(&running, upstream, packet, off + 24) == 1);
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        size_t bytes = tun_packet(tunnel[0], output, version, protocol);
        assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_PORT : ICMP6_DST_UNREACH_NOPORT));
        assert(memcmp(output + off + 8, packet, off + 24) == 0);
        assert_icmp_checksum(output, bytes, off, protocol);
        close(upstream); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_icmp_recv_mtu(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    extern ssize_t __real_sendto(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    real_udp_packets = 1;
    for (int version = 4; version <= 6; version += 2)
        for (int ping = 0; ping < 2; ping++)
            for (int kind = 0; kind < 5; kind++) {
                int tunnel[2], upstream[2];
                assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
                assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
                struct arguments running = *args; running.tun = tunnel[1];
                uint8_t original[128], output[2048], reply[24]; size_t length;
                struct ng_session *s = error_flow(&running, version, ping, epoll_fd, original, &length);
                // Local socket pair keeps a real reply readable while the first
                // recv reports an error arriving after the EPOLLIN snapshot.
                close(s->socket); s->socket = upstream[0];
                memset(reply, 0xa5, sizeof(reply));
                if (ping) {
                    reply[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY;
                    reply[1] = 0; store16(reply + 2, 0);
                    store16(reply + 4, 54321); store16(reply + 6, 7);
                }
                assert(__real_sendto(upstream[1], reply, sizeof(reply), 0, NULL, 0) == sizeof(reply));
                assert(is_readable(s->socket));
                int fd = s->socket;
                queue_error(fd, version, kind == 2 ? ECONNREFUSED : EMSGSIZE, 1400);
                if (kind == 1 || kind == 4) queued_error.remaining = 0;
                recv_error_fd = fd;
                recv_error_value = kind == 3 ? EAGAIN : kind == 4 ? ENETUNREACH : EMSGSIZE;
                struct epoll_event event = {.events = EPOLLIN, .data.ptr = s};
                if (kind == 0) log_errno = EAGAIN; // Logging must not change the error decision.
                if (ping) check_icmp_socket(&running, &event);
                else check_udp_socket(&running, &event);
                log_errno = 0;
                assert(recv_error_fd == -1);
                int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                size_t off = version == 4 ? 20 : 40;
                if (kind == 0 || kind == 2 || kind == 4) {
                    assert(queued_error.calls == (kind == 4 ? 1 : 2));
                    size_t bytes = tun_packet(tunnel[0], output, version, protocol);
                    assert(bytes == off + 8 + length);
                    assert(memcmp(output + off + 8, original, length) == 0);
                    assert_icmp_checksum(output, bytes, off, protocol);
                    if (kind == 0) {
                        assert(output[off] == (version == 4 ? ICMP_UNREACH : ICMP6_PACKET_TOO_BIG));
                        assert(load16(output + off + 6) == 1400);
                    } else if (kind == 2)
                        assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_PORT : ICMP6_DST_UNREACH_NOPORT));
                    else
                        assert(output[off + 1] == (version == 4 ? ICMP_UNREACH_NET : ICMP6_DST_UNREACH_NOROUTE));
                } else {
                    assert(!is_readable(tunnel[0]));
                    assert(queued_error.calls == (kind == 1 ? 1 : 0));
                }
                bool fatal = kind == 2 || kind == 4;
                assert(ping ? (s->icmp.stop != 0) == fatal : s->udp.state == (fatal ? UDP_FINISHING : UDP_ACTIVE));
                assert(s->socket == fd && args->ctx->ng_session == s);
                error_queue_recv = NULL;
                if (!fatal) {
                    // The reply remains queued and must reach the app on the
                    // next read, using the same session and descriptor.
                    if (ping) check_icmp_socket(&running, &event);
                    else check_udp_socket(&running, &event);
                    size_t bytes = tun_packet(tunnel[0], output, version, ping ? protocol : IPPROTO_UDP);
                    assert(bytes == off + sizeof(reply) + (ping ? 0 : 8));
                    if (ping) {
                        assert(load16(output + off + 4) == 12345);
                        assert(memcmp(output + off + 6, reply + 6, sizeof(reply) - 6) == 0);
                    } else assert(memcmp(output + off + 8, reply, sizeof(reply)) == 0);
                    assert_icmp_checksum(output, bytes, off, ping ? protocol : IPPROTO_UDP);
                    assert(ping ? !s->icmp.stop : s->udp.state == UDP_ACTIVE);
                    assert(s->socket == fd && allocations == 1 && sockets == 0);
                }
                assert(!is_readable(tunnel[0]));
                clear(args->ctx); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
                assert(allocations == 0 && sockets == 0);
            }
    real_udp_packets = 0;
    close(epoll_fd);
#endif
}

void test_icmp_reply_validation(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int reuse = 0; reuse < 2; reuse++) {
            int upstream[2], tunnel[2];
            assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
            assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
            struct packet_buffers buffers = {0};
            struct arguments running = *args;
            running.tun = tunnel[1]; running.buffers = reuse ? &buffers : NULL;
            struct ng_session s = {.socket = upstream[0]};
            s.icmp.version = version; s.icmp.id = htons(12345);
            struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
            const uint8_t types[] = {version == 4 ? ICMP6_ECHO_REPLY : ICMP_ECHOREPLY,
                    version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                    version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH, 133, 255,
                    version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY,
                    version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY};
            for (size_t i = 0; i < sizeof(types); i++) {
                uint8_t reply[13] = {0}, output[2048];
                reply[0] = types[i]; reply[1] = i == 5 ? 1 : i == 6 ? 255 : 0;
                store16(reply + 4, 54321); store16(reply + 6, (uint16_t) i);
                memset(reply + 8, 0xa5, sizeof(reply) - 8);
                assert(send(upstream[1], reply, sizeof(reply), 0) == sizeof(reply));
                check_icmp_socket(&running, &event);
                assert(!s.icmp.stop && !is_readable(tunnel[0]));
                // Invalid traffic must not poison reuse of either scratch buffer.
                reply[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY; reply[1] = 0;
                assert(send(upstream[1], reply, sizeof(reply), 0) == sizeof(reply));
                check_icmp_socket(&running, &event);
                int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                size_t length = tun_packet(tunnel[0], output, version, protocol);
                size_t off = version == 4 ? 20 : 40;
                assert(length == off + sizeof(reply) && !s.icmp.stop);
                assert(output[off] == reply[0] && output[off + 1] == 0);
                assert(load16(output + off + 4) == 12345);
                assert(memcmp(output + off + 6, reply + 6, sizeof(reply) - 6) == 0);
                assert_icmp_checksum(output, length, off, protocol);
                assert(allocations == (reuse ? 2 : 0));
            }
            clear_packet_buffers(&buffers);
            close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
            assert(allocations == 0 && sockets == 0);
        }
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static const uint8_t *large_reply;
static size_t large_reply_length;

static ssize_t receive_large_reply(int fd, struct msghdr *message, int flags) {
    assert(flags == 0 && message->msg_iovlen == 1);
    size_t copied = large_reply_length;
    if (copied > message->msg_iov[0].iov_len) {
        copied = message->msg_iov[0].iov_len;
        message->msg_flags |= MSG_TRUNC;
    }
    memcpy(message->msg_iov[0].iov_base, large_reply, copied);
    return (ssize_t) copied;
}

static ssize_t receive_truncated_reply(int fd, struct msghdr *message, int flags) {
    extern ssize_t __real_recvmsg(int, struct msghdr *, int);
    // Exercise a real kernel MSG_TRUNC without requiring a huge contiguous skb
    // allocation on old phones. The ordinary receive still sees its full buffer.
    message->msg_iov[0].iov_len = 8;
    return __real_recvmsg(fd, message, flags);
}
#endif

void test_icmp_reply_bounds(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int reuse = 0; reuse < 2; reuse++) {
            // Independent wire bounds: IPv4 includes its header in Total Length;
            // IPv6's 16-bit Payload Length excludes its 40-byte header.
            size_t off = version == 4 ? 20 : 40;
            size_t limit = version == 4 ? UINT16_MAX - off : UINT16_MAX;
            size_t capacity = off + limit + 1;
            uint8_t *reply = malloc(limit + 1), *output = malloc(capacity), *expected = malloc(capacity);
            assert(reply != NULL && output != NULL && expected != NULL);
            int upstream[2], tunnel[2];
            assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
            assert(socketpair(AF_UNIX, SOCK_STREAM, 0, tunnel) == 0);
            int send_buffer = 256 * 1024;
            assert(setsockopt(tunnel[1], SOL_SOCKET, SO_SNDBUF, &send_buffer, sizeof(send_buffer)) == 0);
            struct packet_buffers buffers = {0};
            struct arguments running = *args;
            running.tun = tunnel[1]; running.buffers = reuse ? &buffers : NULL;
            struct ng_session s = {.socket = upstream[0]};
            s.icmp.version = version; s.icmp.id = htons(12345);
            int family = version == 4 ? AF_INET : AF_INET6;
            assert(inet_pton(family, version == 4 ? "10.0.0.2" : "2001:db8::1", &s.icmp.saddr) == 1);
            assert(inet_pton(family, version == 4 ? "192.0.2.1" : "2001:db8::2", &s.icmp.daddr) == 1);
            struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
            memset(reply, 0xa5, limit + 1);
            reply[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY; reply[1] = 0;
            store16(reply + 2, 0); store16(reply + 4, 54321); store16(reply + 6, 7);
            const size_t lengths[] = {limit - 1, limit, limit + 1, 9};
            for (size_t i = 0; i < sizeof(lengths) / sizeof(*lengths); i++) {
                size_t length = lengths[i];
                if (length > 9) {
                    large_reply = reply; large_reply_length = length;
                    packet_recv = receive_large_reply;
                } else assert(send(upstream[1], reply, length, 0) == length);
                check_icmp_socket(&running, &event);
                packet_recv = NULL;
                assert(!s.icmp.stop && s.socket == upstream[0]);
                if (length > limit) {
                    assert(!is_readable(tunnel[0]));
                    continue;
                }
                assert(is_readable(tunnel[0]));
                size_t received = 0;
                while (received < off + length && is_readable(tunnel[0])) {
                    ssize_t bytes = recv(tunnel[0], output + received, capacity - received, MSG_DONTWAIT);
                    assert(bytes > 0);
                    received += (size_t) bytes;
                }
                assert(received == off + length);
                assert(output[0] >> 4 == version);
                assert(load16(output + (version == 4 ? 2 : 4)) == (version == 4 ? off + length : length));
                assert(output[off] == reply[0] && output[off + 1] == 0);
                assert(load16(output + off + 4) == 12345);
                assert(memcmp(output + off + 6, reply + 6, length - 6) == 0);
                memcpy(expected, output, off + length);
                packet_checksums(expected, off + length, off, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
                assert(memcmp(expected, output, off + length) == 0);
                assert(allocations == (reuse ? 2 : 0));
            }
            assert(send(upstream[1], reply, 32, 0) == 32);
            packet_recv = receive_truncated_reply;
            check_icmp_socket(&running, &event);
            packet_recv = NULL;
            assert(!is_readable(tunnel[0]) && !s.icmp.stop);
            assert(send(upstream[1], reply, 9, 0) == 9);
            check_icmp_socket(&running, &event);
            assert(is_readable(tunnel[0]));
            assert(recv(tunnel[0], output, capacity, 0) == off + 9);
            assert(load16(output + off + 4) == 12345 && !s.icmp.stop);
            assert(!is_readable(tunnel[0]) && !is_readable(upstream[0]));
            clear_packet_buffers(&buffers);
            close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
            free(reply); free(output); free(expected);
            assert(allocations == 0 && sockets == 0);
        }
#endif
}
