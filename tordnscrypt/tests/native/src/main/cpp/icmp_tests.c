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
            assert(handle_icmp(args, packet, off + lengths[i], packet + off, own_uid, -1) == 1);
            assert(load16(packet + off + 4) == (uint16_t) ~UINT16_C(12345));
            assert_icmp_checksum(packet, off + lengths[i], off, IPPROTO_ICMPV6);
            assert(destination_calls == 1);
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0);
        }
    }
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
    for (int version = 4; version <= 6; version += 2) {
        const char *dest = version == 4 ? "198.51.100.1" : "fe80::1";
        _Alignas(8) uint8_t packet[64];
        size_t off = ip_header(packet, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6,
                               8, dest);
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
        expect_destination(version, dest, 0);
        assert(handle_icmp(args, packet, off + 8, packet + off, own_uid, -1) == 1);
        assert(destination_calls == 1);
        clear(args->ctx);
        assert(allocations == 0 && sockets == 0);
    }
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
        struct ng_session s = {.socket = -1};
        s.icmp.version = version;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        fail_alloc_tag = "icmp socket";
        fail_alloc_match = 1;
        check_icmp_socket(args, &event);
        assert(fail_alloc_match == 0);
        assert(s.icmp.stop);
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
