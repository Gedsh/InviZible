#include "test_support.h"

void test_restore_uid_addresses(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    _Alignas(4) uint8_t source[5], dest[5];
    assert(inet_pton(AF_INET, "198.51.100.201", source + 1) == 1);
    assert(inet_pton(AF_INET, "203.0.113.202", dest + 1) == 1);
    const int protocols[] = {IPPROTO_TCP, IPPROTO_UDP, IPPROTO_ICMP};
    struct icmp echo = {.icmp_type = ICMP_ECHO, .icmp_id = htons(1234)};
    for (size_t i = 0; i < sizeof(protocols) / sizeof(*protocols); i++) {
        struct ng_session session = {.protocol = protocols[i], .socket = 42};
        if (protocols[i] == IPPROTO_TCP) {
            session.tcp.version = 4; session.tcp.uid = 12345; session.tcp.dest = htons(443);
            session.tcp.source = htons(4321); session.tcp.state = TCP_ESTABLISHED;
            memcpy(&session.tcp.saddr.ip4, source + 1, 4);
            memcpy(&session.tcp.daddr.ip4, dest + 1, 4);
        } else if (protocols[i] == IPPROTO_UDP) {
            session.udp.version = 4; session.udp.uid = 12345; session.udp.dest = htons(443);
            session.udp.source = htons(4321); session.udp.state = UDP_ACTIVE;
            memcpy(&session.udp.saddr.ip4, source + 1, 4);
            memcpy(&session.udp.daddr.ip4, dest + 1, 4);
        } else {
            session.icmp.version = 4; session.icmp.uid = 12345; session.icmp.id = echo.icmp_id;
            memcpy(&session.icmp.saddr.ip4, source + 1, 4);
            memcpy(&session.icmp.daddr.ip4, dest + 1, 4);
        }
        set_test_sessions(args->ctx, &session);
        struct tcphdr tcp = {0};
        const uint8_t *payload = protocols[i] == IPPROTO_TCP
                                 ? (const uint8_t *) &tcp : (const uint8_t *) &echo;
        // Unaligned addresses with the high bit set must compare as raw IPv4 bits.
        assert(restore_uid(args, 4, protocols[i], source + 1, 4321, dest + 1, 443,
                           "198.51.100.201", "203.0.113.202", "", payload) == 12345);
        for (int address = 0; address < 2; address++) {
            uint8_t *changed = address == 0 ? source + 1 : dest + 1;
            *changed ^= 1;
            assert(restore_uid(args, 4, protocols[i], source + 1, 4321, dest + 1, 443,
                               "source", "dest", "", payload) == -1);
            *changed ^= 1;
        }
        set_test_sessions(args->ctx, NULL);
    }
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static struct ng_session uid_session(int version, int protocol,
                                      const uint8_t *source, const uint8_t *dest) {
    struct ng_session session = {.protocol = (uint8_t) protocol, .socket = 42};
    size_t size = version == 4 ? 4 : 16;
    if (protocol == IPPROTO_TCP) {
        session.tcp.version = version;
        session.tcp.uid = 12345;
        session.tcp.source = htons(4321);
        session.tcp.dest = htons(443);
        session.tcp.state = TCP_ESTABLISHED;
        session.tcp.remote_start = 99;
        memcpy(&session.tcp.saddr, source, size);
        memcpy(&session.tcp.daddr, dest, size);
    } else {
        session.udp.version = version;
        session.udp.uid = 12345;
        session.udp.source = htons(4321);
        session.udp.dest = htons(443);
        session.udp.state = UDP_ACTIVE;
        memcpy(&session.udp.saddr, source, size);
        memcpy(&session.udp.daddr, dest, size);
    }
    return session;
}

static jint restore_flow_uid(const struct arguments *args, int version, int protocol,
                             const uint8_t *source, const uint8_t *dest) {
    struct tcphdr syn = {.syn = 1, .seq = htonl(99)};
    return restore_uid(args, version, protocol, source, 4321, dest, 443,
                       "source", "dest", "S", (const uint8_t *) &syn);
}
#endif

void test_restore_uid_flow(const struct arguments *args, int version, int protocol) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint8_t source[16] = {0}, dest[16] = {0};
    int family = version == 4 ? AF_INET : AF_INET6;
    assert(inet_pton(family, version == 4 ? "198.51.100.201" : "2001:db8::1", source) == 1);
    assert(inet_pton(family, version == 4 ? "203.0.113.202" : "2001:db8::2", dest) == 1);
    struct ng_session target = uid_session(version, protocol, source, dest);
    for (int mismatch = 0; mismatch < 6; mismatch++) {
        struct ng_session decoy = target;
        if (protocol == IPPROTO_TCP) {
            decoy.tcp.uid = 23456;
            if (mismatch == 0) decoy.tcp.source = htons(4322);
            if (mismatch == 1) decoy.tcp.dest = htons(444);
            if (mismatch == 2) ((uint8_t *) &decoy.tcp.saddr)[0] ^= 1;
            if (mismatch == 3) ((uint8_t *) &decoy.tcp.daddr)[0] ^= 1;
            if (mismatch == 4) decoy.tcp.version = version == 4 ? 6 : 4;
        } else {
            decoy.udp.uid = 23456;
            if (mismatch == 0) decoy.udp.source = htons(4322);
            if (mismatch == 1) decoy.udp.dest = htons(444);
            if (mismatch == 2) ((uint8_t *) &decoy.udp.saddr)[0] ^= 1;
            if (mismatch == 3) ((uint8_t *) &decoy.udp.daddr)[0] ^= 1;
            if (mismatch == 4) decoy.udp.version = version == 4 ? 6 : 4;
        }
        if (mismatch == 5) decoy.protocol = 255;
        set_test_sessions(args->ctx, &decoy);
        assert(restore_flow_uid(args, version, protocol, source, dest) == -1);

        // A newer connection belonging to another app must not hide the exact flow.
        target.next = NULL;
        decoy.next = &target;
        set_test_sessions(args->ctx, &decoy);
        assert(restore_flow_uid(args, version, protocol, source, dest) == 12345);
        set_test_sessions(args->ctx, NULL);
    }
    assert(restore_flow_uid(args, version, protocol, source, dest) == -1);
#endif
}

void test_restore_uid_states(const struct arguments *args, int version, int protocol) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint8_t source[16] = {1}, dest[16] = {2};
    struct ng_session target = uid_session(version, protocol, source, dest);
    const int tcp_states[] = {TCP_LISTEN, TCP_SYN_RECV, TCP_ESTABLISHED, TCP_CLOSE_WAIT,
                              TCP_FIN_WAIT1, TCP_FIN_WAIT2, TCP_LAST_ACK, TCP_CLOSING, TCP_CLOSE};
    const int udp_states[] = {UDP_ACTIVE, UDP_FINISHING, UDP_CLOSED, UDP_BLOCKED};
    size_t count = protocol == IPPROTO_TCP ? sizeof(tcp_states) / sizeof(*tcp_states)
                                         : sizeof(udp_states) / sizeof(*udp_states);
    for (size_t i = 0; i < count; i++) {
        bool live;
        if (protocol == IPPROTO_TCP) {
            target.tcp.state = (uint8_t) tcp_states[i];
            live = tcp_states[i] != TCP_CLOSING && tcp_states[i] != TCP_CLOSE;
        } else {
            target.udp.state = (uint8_t) udp_states[i];
            live = udp_states[i] == UDP_ACTIVE;
        }
        set_test_sessions(args->ctx, &target);
        assert(restore_flow_uid(args, version, protocol, source, dest) == (live ? 12345 : -1));
    }
    target = uid_session(version, protocol, source, dest);
    target.socket = -1;
    set_test_sessions(args->ctx, &target);
    assert(restore_flow_uid(args, version, protocol, source, dest) == -1);

    // Never fall back past the newest exact record to an old owner of the same tuple.
    struct ng_session newest = uid_session(version, protocol, source, dest);
    target = newest;
    if (protocol == IPPROTO_TCP) newest.tcp.uid = -1;
    else newest.udp.uid = -1;
    newest.next = &target;
    set_test_sessions(args->ctx, &newest);
    assert(restore_flow_uid(args, version, protocol, source, dest) == -1);
    if (protocol == IPPROTO_TCP) {
        newest.tcp.uid = 23456;
        newest.tcp.state = TCP_CLOSE;
    } else {
        newest.udp.uid = 23456;
        newest.udp.state = UDP_CLOSED;
    }
    assert(restore_flow_uid(args, version, protocol, source, dest) == -1);
    if (protocol == IPPROTO_TCP) {
        newest.tcp.state = TCP_ESTABLISHED;
        newest.tcp.remote_start++;
        assert(restore_flow_uid(args, version, protocol, source, dest) == -1);
        newest.tcp.remote_start--;
    } else newest.udp.state = UDP_ACTIVE;
    assert(restore_flow_uid(args, version, protocol, source, dest) == 23456);
    set_test_sessions(args->ctx, NULL);
#endif
}

void test_udp_packet_lookup(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    capture_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        _Alignas(8) uint8_t packet[128], output[2048];
        size_t off = ip_header(packet, version, IPPROTO_UDP, 20,
                               version == 4 ? "203.0.113.2" : "2001:db8::2");
        struct udphdr *udp = (void *) (packet + off);
        udp->source = htons(12345); udp->dest = htons(kind == 0 ? 5000 : 53); udp->len = htons(20);
        packet_checksums(packet, off + 20, off, IPPROTO_UDP);
        struct ng_session target = {.protocol = IPPROTO_UDP, .socket = -1};
        target.udp.version = version; target.udp.source = udp->source; target.udp.dest = udp->dest;
        target.udp.uid = 12345; target.udp.state = UDP_ACTIVE;
        memcpy(&target.udp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
        memcpy(&target.udp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
        struct ng_session decoys[6];
        for (size_t i = 0; i < 6; i++) {
            decoys[i] = target;
            decoys[i].next = i == 5 ? NULL : &decoys[i + 1];
        }
        decoys[0].protocol = IPPROTO_TCP;
        decoys[1].udp.version = version == 4 ? 6 : 4;
        decoys[2].udp.source = htons(12346);
        decoys[3].udp.dest = htons(kind == 0 ? 5001 : 5353);
        decoys[4].udp.saddr.ip6.s6_addr[version == 4 ? 0 : 15] ^= 1;
        decoys[5].udp.daddr.ip6.s6_addr[version == 4 ? 0 : 15] ^= 1;
        for (int present = 0; present <= 1; present++) for (int full = 0; full <= 1; full++) {
            for (int option = 0; option < (kind == 0 ? 4 : 2); option++) {
                int states[] = {UDP_ACTIVE, UDP_FINISHING, UDP_CLOSED, UDP_BLOCKED};
                target.udp.state = kind == 0 ? states[option] : UDP_ACTIVE;
                running.fwd53 = kind == 0 ? 1 : option;
                decoys[5].next = present ? &target : NULL;
                set_test_sessions(args->ctx, decoys);
                udp_lookups = uid_lookups = policy_checks = udp_calls = udp_lookup_supplied = 0;
                udp_lookup_session = NULL;
                handle_ip(&running, packet, off + 20, -1, full ? SESSION_MAX : 0, SESSION_MAX);
                bool existing = present;
                bool dropped = full && !existing;
                assert(udp_lookups == 1);
                assert(uid_lookups == (!dropped && (kind == 1 || !existing)));
                assert(policy_checks == (!dropped && (kind == 1 || !existing)));
                assert(udp_calls == !dropped);
                assert(udp_lookup_supplied == !dropped);
                assert(udp_lookup_session == (!dropped && present ? &target : NULL));
                assert(allocations == 0 && args->ctx->ng_session == decoys);
            }
        }
        if (kind == 1) {
            // Forwarded DNS must still consult policy for an existing active flow.
            running.fwd53 = 1; decoys[5].next = &target;
            target.udp.state = UDP_ACTIVE;
            set_test_sessions(args->ctx, decoys);
            deny_address = 1;
            udp_lookups = uid_lookups = policy_checks = udp_calls = 0;
            handle_ip(&running, packet, off + 20, -1, 0, SESSION_MAX);
            assert(uid_lookups == 1 && policy_checks == 1 && udp_calls == 0 && udp_lookups == 1);
            struct ng_session *blocked = args->ctx->ng_session;
            assert(blocked != decoys && blocked->udp.state == UDP_BLOCKED && blocked->next == decoys);
            tun_packet(tunnel[0], output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
            ng_free(blocked, __FILE__, __LINE__);
            deny_address = 0;
            assert(allocations == 0);
        }
        set_test_sessions(args->ctx, NULL);
    }
    capture_packets = 0;
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_tun_buffer_reuse(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    real_network = 1;
    assert(fcntl(tunnel[0], F_SETFL, O_NONBLOCK) == 0);
    struct packet_buffers buffers = {0};
    struct arguments running = *args;
    running.tun = tunnel[0]; running.buffers = &buffers;
    struct epoll_event event = {.events = EPOLLIN};
    fail_alloc_tag = "tun read"; fail_alloc_match = 1;
    assert(check_tun(&running, &event, -1, 0, SESSION_MAX) == -1);
    assert(allocations == 0 && buffers.buffer[PACKET_INPUT].data == NULL);
    fail_alloc_tag = NULL;
    capture_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        _Alignas(8) uint8_t packet[128];
        size_t off = ip_header(packet, version, IPPROTO_UDP, 9,
                               version == 4 ? "203.0.113.2" : "2001:db8::2");
        struct udphdr *udp = (void *) (packet + off);
        udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(9);
        packet[off + 8] = (uint8_t) version;
        packet_checksums(packet, off + 9, off, IPPROTO_UDP);
        int before = udp_calls;
        assert(write(tunnel[1], packet, off + 9) == off + 9);
        assert(check_tun(&running, &event, -1, 0, SESSION_MAX) == 0);
        assert(udp_calls == before + 1 && allocations == 1);
        assert(memcmp(buffers.buffer[PACKET_INPUT].data, packet, off + 9) == 0);
    }
    uint8_t *input = buffers.buffer[PACKET_INPUT].data;
    assert(check_tun(&running, &event, -1, 0, SESSION_MAX) == 0); // EAGAIN.
    close(tunnel[1]);
    assert(check_tun(&running, &event, -1, 0, SESSION_MAX) == -1); // EOF.
    assert(buffers.buffer[PACKET_INPUT].data == input && allocations == 1);
    clear_packet_buffers(&buffers);
    close(tunnel[0]);
    assert(allocations == 0);
    capture_packets = 0;
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static int protocol_calls(int protocol) {
    return protocol == IPPROTO_UDP ? udp_calls : protocol == IPPROTO_TCP ? tcp_calls : icmp_calls;
}

static size_t checksum_packet(uint8_t *packet, int version, int protocol, size_t data_len) {
    size_t header = protocol == IPPROTO_TCP ? 20 : 8;
    size_t off = ip_header(packet, version, protocol, header + data_len,
                           version == 4 ? "203.0.113.2" : "2001:db8::2");
    if (protocol == IPPROTO_TCP) {
        struct tcphdr *tcp = (void *) (packet + off);
        tcp->source = htons(12345); tcp->dest = htons(443); tcp->syn = 1; tcp->doff = 5;
    } else if (protocol == IPPROTO_UDP) {
        struct udphdr *udp = (void *) (packet + off);
        udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(header + data_len);
    } else
        packet[off] = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
    for (size_t i = 0; i < data_len; i++) packet[off + header + i] = (uint8_t) (i * 17 + 3);
    packet_checksums(packet, off + header + data_len, off, protocol);
    return off + header + data_len;
}

static void expect_checksum_packet(const struct arguments *args, uint8_t *packet,
                                   size_t length, int protocol, int expected) {
    int before = protocol_calls(protocol);
    handle_ip(args, packet, length, -1, 0, 100);
    assert(protocol_calls(protocol) - before == expected);
    assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
}
#endif

void test_ip_checksums(const struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    capture_packets = 1;
    uint8_t *packet = malloc(65536);
    assert(packet != NULL);
    if (kind == 0) {
        size_t len = checksum_packet(packet, 4, IPPROTO_UDP, 1);
        memmove(packet + 24, packet + 20, len - 20);
        memset(packet + 20, 0, 4); packet[20] = 1; // NOP followed by EOL/padding.
        len += 4; packet[0] = 0x46; store16(packet + 2, len);
        packet_checksums(packet, len, 24, IPPROTO_UDP);
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 1);
        packet[23] ^= 1; // Padding must be included in the IPv4 header checksum.
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
        packet[23] ^= 1;
        packet[8] ^= 1; // Corrupt TTL without changing packet or transport lengths.
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
    } else if (kind <= 6) {
        int version = kind % 2 == 1 ? 4 : 6;
        int protocol = kind <= 2 ? IPPROTO_UDP : kind <= 4 ? IPPROTO_TCP :
                       version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        size_t off = version == 4 ? 20 : 40;
        size_t header = protocol == IPPROTO_TCP ? 20 : 8;
        const size_t sizes[] = {0, 1, 2, 7, 8, 15, 16, 17, 31, 32, 33, 1401, 32761};
        for (size_t i = 0; i < sizeof(sizes) / sizeof(*sizes); i++) {
            size_t len = checksum_packet(packet, version, protocol, sizes[i]);
            expect_checksum_packet(args, packet, len, protocol, 1);
            size_t corrupt = sizes[i] ? len - 1 : off + 4;
            packet[corrupt] ^= 0x40;
            expect_checksum_packet(args, packet, len, protocol, 0);
            packet[corrupt] ^= 0x40;
            if (protocol != IPPROTO_ICMP) {
                packet[version == 4 ? 12 : 8] ^= 1; // Pseudo-header source.
                if (version == 4) {
                    uint8_t saved[2]; memcpy(saved, packet + off + (protocol == IPPROTO_UDP ? 6 : 16), 2);
                    packet_checksums(packet, len, off, protocol);
                    memcpy(packet + off + (protocol == IPPROTO_UDP ? 6 : 16), saved, 2);
                }
                expect_checksum_packet(args, packet, len, protocol, 0);
            }
        }
        if (protocol == IPPROTO_UDP) {
            size_t len = checksum_packet(packet, version, protocol, 3);
            store16(packet + off + 6, 0);
            expect_checksum_packet(args, packet, len, protocol, version == 4);
        }
    } else if (kind == 7) {
        // IPv6 extension bytes are excluded from the pseudo-header upper-layer length.
        const int extensions[] = {IPPROTO_HOPOPTS, IPPROTO_DSTOPTS, IPPROTO_AH, IPPROTO_FRAGMENT};
        const int protocols[] = {IPPROTO_TCP, IPPROTO_UDP, IPPROTO_ICMPV6};
        for (size_t p = 0; p < 3; p++) for (size_t e = 0; e < 4; e++) {
            size_t len = checksum_packet(packet, 6, protocols[p], 3);
            size_t extra = extensions[e] == IPPROTO_AH ? 12 : 8;
            memmove(packet + 40 + extra, packet + 40, len - 40);
            memset(packet + 40, 0, extra);
            packet[6] = extensions[e]; packet[40] = protocols[p];
            packet[41] = extensions[e] == IPPROTO_AH ? 1 : 0;
            len += extra; store16(packet + 4, len - 40);
            packet_checksums(packet, len, 40 + extra, protocols[p]);
            expect_checksum_packet(args, packet, len, protocols[p], 1);
            packet[len - 1] ^= 1;
            expect_checksum_packet(args, packet, len, protocols[p], 0);
        }
    } else if (kind == 8) {
        const uint8_t source_routes[] = {131, 137};
        for (size_t i = 0; i < 2; i++) {
            size_t len = checksum_packet(packet, 4, IPPROTO_UDP, 3);
            memmove(packet + 28, packet + 20, len - 20);
            memset(packet + 20, 0, 8); packet[0] = 0x47;
            packet[20] = source_routes[i]; packet[21] = 7; packet[22] = 4;
            memcpy(packet + 23, packet + 16, 4);
            len += 8; store16(packet + 2, len);
            packet_checksums(packet, len, 28, IPPROTO_UDP);
            uint8_t saved[2]; memcpy(saved, packet + 34, 2);
            packet[19] ^= 1;
            packet_checksums(packet, len, 28, IPPROTO_UDP);
            memcpy(packet + 34, saved, 2);
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 1);
            packet[26] ^= 1;
            packet_checksums(packet, len, 28, IPPROTO_UDP);
            memcpy(packet + 34, saved, 2);
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
            packet[21] = 2; // A source-route option cannot omit its pointer and address.
            packet_checksums(packet, len, 28, IPPROTO_UDP);
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
        }
        // Pseudo-header addresses can differ from the current IPv6 hop addresses.
        const uint8_t routing_types[] = {0, 2, 4};
        for (size_t i = 0; i < 3; i++) {
            size_t len = checksum_packet(packet, 6, IPPROTO_UDP, 3);
            memmove(packet + 64, packet + 40, len - 40);
            memset(packet + 40, 0, 24); packet[40] = IPPROTO_UDP;
            packet[6] = IPPROTO_ROUTING; packet[41] = 2; packet[42] = routing_types[i]; packet[43] = 1;
            memcpy(packet + 48, packet + 24, 16);
            len += 24; store16(packet + 4, len - 40);
            packet_checksums(packet, len, 64, IPPROTO_UDP);
            packet[39] ^= 1; // Current hop changes, final destination remains in routing header.
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 1);
            packet[63] ^= 1;
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
            packet[43] = 2; // More segments left than addresses in this routing header.
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
            store16(packet + 4, 23); // Truncated routing header, with consistent IP length.
            expect_checksum_packet(args, packet, 63, IPPROTO_UDP, 0);
        }
        size_t len = checksum_packet(packet, 6, IPPROTO_UDP, 3);
        memmove(packet + 64, packet + 40, len - 40);
        memset(packet + 40, 0, 24); packet[6] = IPPROTO_DSTOPTS;
        packet[40] = IPPROTO_UDP; packet[41] = 2; packet[46] = 201; packet[47] = 16;
        memcpy(packet + 48, packet + 8, 16);
        len += 24; store16(packet + 4, len - 40);
        packet_checksums(packet, len, 64, IPPROTO_UDP);
        packet[23] ^= 1;
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 1);
        packet[63] ^= 1;
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
        packet[47] = 255; // Option data must stay within its extension header.
        expect_checksum_packet(args, packet, len, IPPROTO_UDP, 0);
    } else {
        // A generated zero UDP checksum is encoded as all ones, including on IPv6.
        for (int version = 4; version <= 6; version += 2) {
            size_t len = checksum_packet(packet, version, IPPROTO_UDP, 2);
            size_t off = version == 4 ? 20 : 40;
            int found = 0;
            for (unsigned int value = 0; value <= UINT16_MAX && !found; value++) {
                store16(packet + len - 2, (uint16_t) value);
                packet_checksums(packet, len, off, IPPROTO_UDP);
                found = load16(packet + off + 6) == UINT16_MAX;
            }
            assert(found);
            expect_checksum_packet(args, packet, len, IPPROTO_UDP, 1);
        }
    }
    capture_packets = 0;
    free(packet);
#endif
}

void test_tun_allocation_failure(const struct arguments *args) {
    struct epoll_event event = {.events = EPOLLIN};
    fail_alloc_tag = "tun read";
    fail_alloc_match = 1;
    assert(check_tun(args, &event, -1, 0, 100) == -1);
    assert(fail_alloc_match == 0);
    assert(allocations == 0);
    fail_alloc_tag = NULL;
}

static void ip_packet(const struct arguments *args, size_t len, int version,
                       int extension, int extension_len, int expected) {
    uint8_t *pkt = calloc(1, len);
    if (version == 4) {
        struct iphdr *ip = (void *) pkt;
        ip->version = 4; ip->ihl = extension;
        ip->tot_len = htons(len); ip->protocol = IPPROTO_UDP;
        if (len >= 28) {
            struct udphdr *udp = (void *) (pkt + 20);
            udp->len = htons(len - 20);
        }
    } else {
        struct ip6_hdr *ip = (void *) pkt;
        ip->ip6_vfc = 0x60; ip->ip6_plen = htons(len - 40);
        ip->ip6_nxt = extension;
        size_t off = 40;
        if (extension != IPPROTO_UDP && len >= 42) {
            pkt[40] = IPPROTO_UDP;
            pkt[41] = extension_len;
            off += (extension == IPPROTO_AH ? (extension_len + 2) * 4 :
                    extension == IPPROTO_FRAGMENT ? 8 : (extension_len + 1) * 8);
        }
        if (off + 8 <= len) {
            struct udphdr *udp = (void *) (pkt + off);
            udp->len = htons(len - off);
        }
    }
    if (expected)
        packet_checksums(pkt, len, len - sizeof(struct udphdr), IPPROTO_UDP);
    int before = udp_calls;
    handle_ip(args, pkt, len, -1, 0, 100);
    assert(udp_calls - before == expected);
    free(pkt);
}

void test_packets(struct arguments *args) {
    capture_packets = 1;
    uint8_t empty = 0;
    handle_ip(args, &empty, 0, -1, 0, 100);
    ip_packet(args, 20, 4, 15, 0, 0);
    ip_packet(args, 28, 4, 4, 0, 0);
    ip_packet(args, 28, 4, 5, 0, 1);
    ip_packet(args, 40, 6, IPPROTO_HOPOPTS, 0, 0);
    ip_packet(args, 48, 6, IPPROTO_UDP, 0, 1);
    ip_packet(args, 56, 6, IPPROTO_HOPOPTS, 0, 1);
    ip_packet(args, 64, 6, IPPROTO_DSTOPTS, 1, 1);
    ip_packet(args, 56, 6, IPPROTO_HOPOPTS, 255, 0);
    ip_packet(args, 60, 6, IPPROTO_AH, 1, 1);
    ip_packet(args, 56, 6, IPPROTO_FRAGMENT, 0, 1);
    ip_packet(args, 48, 6, IPPROTO_ESP, 0, 0);
    uint8_t chain[64] = {0x60};
    chain[5] = 24; chain[6] = IPPROTO_HOPOPTS;
    chain[40] = IPPROTO_DSTOPTS; chain[48] = IPPROTO_UDP;
    chain[61] = 8; // UDP length at offset 56 + 4.
    packet_checksums(chain, sizeof(chain), 56, IPPROTO_UDP);
    int before = udp_calls;
    handle_ip(args, chain, sizeof(chain), -1, 0, 100);
    assert(udp_calls == before + 1);
    chain[61] = 9; // Reject a UDP length extending past the IP payload.
    handle_ip(args, chain, sizeof(chain), -1, 0, 100);
    assert(udp_calls == before + 1);
    chain[6] = IPPROTO_FRAGMENT; chain[43] = 1; // Reassembly required.
    handle_ip(args, chain, sizeof(chain), -1, 0, 100);
    assert(udp_calls == before + 1);
    capture_packets = 0;
}

void test_uid_file_cleanup(void) {
    uint32_t addr = 0;
    fake_proc = 1;
    int before = file_closes;
    assert(get_uid_sub(4, IPPROTO_UDP, &addr, 1, &addr, 2, "0.0.0.0", "0.0.0.0", 0) == -2);
    assert(file_closes == before + 1);
    fake_proc = 0;
}

static const char *proc_one =
        "header\n0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 10001 0 0\n";
static const char *proc_two =
        "header\n0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 10001 0 0\n"
        "1: 0100007F:04D3 00000000:0000 07 0:0 0:0 0 10002 0 0\n";

static jint lookup_uid(int version, int protocol, uint16_t sport, int64_t now) {
    uint8_t address[16];
    const char *text = (version == 4 ? "127.0.0.1" : "::1");
    assert(inet_pton(version == 4 ? AF_INET : AF_INET6, text, address) == 1);
    return get_uid_sub(version, protocol, address, sport, address, 53, text, text, now);
}

static void release_uid_cache(void) {
    ng_free(uid_cache, __FILE__, __LINE__);
    uid_cache = NULL;
    uid_cache_size = 0;
#ifndef INVIZIBLE_BASELINE_TESTS
    uid_cache_capacity = 0;
#endif
    fake_proc = fail_cache_alloc = 0;
    proc_contents = NULL;
    proc6_contents = NULL;
    proc6_open_error = 0;
    assert(allocations == 0);
}

void test_uid_live_proc(int version, int protocol, bool mapped) {
    // Reserve local endpoints only: no packets or firewall exceptions are needed.
    assert(!fake_proc && uid_cache == NULL);
    int family = version == 4 ? AF_INET : AF_INET6;
    const char *text = mapped ? "::ffff:127.0.0.1" : version == 4 ? "127.0.0.1" : "::1";
    uint8_t source[16];
    assert(inet_pton(family, text, source) == 1);
    if (version == 6 && protocol == IPPROTO_ICMPV6 && access("/proc/net/icmp6", F_OK) != 0) {
        // Some older kernels expose no ICMPv6 ping table. This must be a clean
        // lookup miss, with no attempted file scan or retained cache allocation.
        assert(errno == ENOENT);
        int before = file_closes;
        assert(get_uid_sub(version, protocol, source, 1234, source, 53, text, text, 0) == -1);
        assert(get_uid(version, protocol, source, 1234, source, 53) == -1);
        assert(file_closes == before && uid_cache == NULL && uid_cache_size == 0);
        assert(allocations == 0 && sockets == 0);
        return;
    }
    int type = protocol == IPPROTO_TCP ? SOCK_STREAM : SOCK_DGRAM;
    int fd = __real_socket(family, type | SOCK_CLOEXEC, protocol);
    assert(fd >= 0);
    struct sockaddr_storage local = {0};
    socklen_t length;
    if (version == 4) {
        struct sockaddr_in *address = (void *) &local;
        address->sin_family = AF_INET;
        memcpy(&address->sin_addr, source, sizeof(address->sin_addr));
        length = sizeof(*address);
    } else {
        struct sockaddr_in6 *address = (void *) &local;
        address->sin6_family = AF_INET6;
        memcpy(&address->sin6_addr, source, sizeof(address->sin6_addr));
        int v6only = !mapped;
        assert(setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &v6only, sizeof(v6only)) == 0);
        length = sizeof(*address);
    }
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6)
        // Some older app policies deny explicit ping bind(), but permit the
        // kernel's connect/autobind path. Connecting sends no ICMP packet.
        assert(__real_connect(fd, (void *) &local, length) == 0);
    else
        assert(bind(fd, (void *) &local, length) == 0);
    if (protocol == IPPROTO_TCP)
        assert(listen(fd, 1) == 0); // Bound TCP sockets appear in /proc after listen.
    assert(getsockname(fd, (void *) &local, &length) == 0);
    uint16_t port = ntohs(version == 4 ? ((struct sockaddr_in *) &local)->sin_port
                                     : ((struct sockaddr_in6 *) &local)->sin6_port);
    assert(port != 0);
    int before = file_closes;
    assert(get_uid_sub(version, protocol, source, port, source, 53, text, text, 0) == (jint) getuid());
    assert(file_closes == before + 1 && uid_cache_size > 0);
    // A second query must use the cache, without reopening a proc table.
    assert(get_uid_sub(version, protocol, source, port, source, 53, text, text, 0) == (jint) getuid());
    assert(file_closes == before + 1);
    release_uid_cache();
    // Exercise IPv4's IPv6-first search and fallback through the public entry.
    const uint8_t *address = mapped ? source + 12 : source;
    assert(get_uid(mapped ? 4 : version, protocol, address, port, address, 53) == (jint) getuid());
    assert(__real_close(fd) == 0);
    release_uid_cache();
    assert(sockets == 0);
}

#ifndef INVIZIBLE_BASELINE_TESTS
static bool uid_clock_enabled, uid_clock_failure;
static int64_t uid_test_clock;

int __real_clock_gettime(clockid_t clock, struct timespec *ts);
int __wrap_clock_gettime(clockid_t clock, struct timespec *ts) {
    if (clock == CLOCK_MONOTONIC && test_monotonic_enabled) {
        test_monotonic_calls++;
        if (test_monotonic_failures != 0 || test_monotonic_calls == test_monotonic_fail_at) {
            if (test_monotonic_failures > 0)
                test_monotonic_failures--;
            // Failure must discard the timespec even if the API left data in it.
            ts->tv_sec = 1234; ts->tv_nsec = 999999999;
            errno = EIO;
            return -1;
        }
        ts->tv_sec = (time_t) (test_monotonic_ms / 1000);
        ts->tv_nsec = (long) (test_monotonic_ms % 1000) * 1000000;
        return 0;
    }
    if (clock == CLOCK_BOOTTIME && uid_clock_enabled) {
        if (uid_clock_failure) {
            errno = EIO;
            return -1;
        }
        ts->tv_sec = (time_t) (uid_test_clock / 1000);
        ts->tv_nsec = (long) (uid_test_clock % 1000) * 1000000;
        return 0;
    }
    return __real_clock_gettime(clock, ts);
}

static const char *uid_clock_first =
        "header\n0: 00000000000000000000000001000000:04D2 "
        "00000000000000000000000000000000:0000 07 0:0 0:0 0 10001 0 0\n";
static const char *uid_clock_second =
        "header\n0: 00000000000000000000000001000000:04D2 "
        "00000000000000000000000000000000:0000 07 0:0 0:0 0 20001 0 0\n";

static jint public_uid_clock_lookup(void) {
    uint8_t address[16];
    assert(inet_pton(AF_INET6, "::1", address) == 1);
    return get_uid(6, IPPROTO_UDP, address, 1234, address, 53);
}
#endif

void test_uid_cache_clock(int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    if (kind == 0) {
        // Check the real device clock as well as the injected boundary cases.
        struct timespec before, after;
        assert(__real_clock_gettime(CLOCK_BOOTTIME, &before) == 0);
        fake_proc = 1;
        proc_contents = uid_clock_first;
        assert(public_uid_clock_lookup() == 10001);
        assert(__real_clock_gettime(CLOCK_BOOTTIME, &after) == 0);
        assert(uid_cache_size == 1 && uid_cache[0].time / 1000 >= before.tv_sec &&
               uid_cache[0].time / 1000 <= after.tv_sec);
        release_uid_cache();
    }
    uid_clock_enabled = true;
    uid_clock_failure = false;
    const int64_t starts[] = {INT64_C(2147483647) - 10000,
                              INT64_C(4294967295) - 10000, INT64_C(1) << 40};
    for (size_t i = 0; i < sizeof(starts) / sizeof(*starts); i++) {
        fake_proc = 1;
        proc_contents = uid_clock_first;
        uid_test_clock = starts[i];
        int before = file_closes;
        if (kind == 1) {
            uid_clock_failure = true;
            assert(public_uid_clock_lookup() == 10001 && file_closes == before + 1);
            assert(uid_cache == NULL && uid_cache_size == 0 && allocations == 0);
            uid_clock_failure = false;
            before = file_closes;
        }
        assert(public_uid_clock_lookup() == 10001);
        assert(uid_cache_size == 1 && uid_cache[0].time == starts[i] && file_closes == before + 1);
        proc_contents = uid_clock_second;
        if (kind == 0) {
            uid_test_clock += UID_MAX_AGE;
            assert(public_uid_clock_lookup() == 10001 && file_closes == before + 1);
            // One further millisecond, including time asleep, expires the entry.
            uid_test_clock++;
        } else {
            uid_clock_failure = true;
            assert(public_uid_clock_lookup() == 20001 && file_closes == before + 2);
            assert(uid_cache[0].uid == 10001 && uid_cache[0].time == starts[i]);
            assert(public_uid_clock_lookup() == 20001 && file_closes == before + 3);
            assert(uid_cache_size == 1 && allocations == 1);
            uid_clock_failure = false;
            uid_test_clock += UID_MAX_AGE + 1;
        }
        assert(public_uid_clock_lookup() == 20001);
        assert(uid_cache_size == 1 && uid_cache[0].time == uid_test_clock);
        int scanned = file_closes;
        assert(public_uid_clock_lookup() == 20001 && file_closes == scanned);
        release_uid_cache();
    }
    uid_clock_enabled = false;
#endif
}

void test_uid_cache_allocation_failure(void) {
    assert(uid_cache == NULL && uid_cache_size == 0);
    fake_proc = fail_cache_alloc = 1;
    proc_contents = proc_one;
    assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10001);
    assert(uid_cache == NULL && uid_cache_size == 0 && allocations == 0);
    assert(file_closes == 1);

    fail_cache_alloc = 0;
    assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10001);
    assert(uid_cache_size == 1 && allocations == 1);
    struct uid_cache_entry *original = uid_cache;
    fail_cache_alloc = 1;
    proc_contents = proc_two;
    assert(lookup_uid(4, IPPROTO_UDP, 1235, 0) == 10002);
    assert(uid_cache == original && uid_cache_size == 1 && allocations == 1);
    assert(uid_cache[0].uid == 10001 && file_closes == 3);

    fail_cache_alloc = 0;
    assert(lookup_uid(4, IPPROTO_UDP, 1235, 0) == 10002);
    assert(uid_cache_size == 2 && allocations == 1);
    int before = file_closes;
    assert(lookup_uid(4, IPPROTO_UDP, 1235, 0) == 10002);
    assert(file_closes == before); // Successful growth still supports cache hits.
    release_uid_cache();
}

void test_uid_cache_refresh(void) {
    fake_proc = 1;
    proc_contents = proc_one;
    for (int i = 0; i < 100; i++) {
        assert(lookup_uid(4, IPPROTO_UDP, 9999, i) == -1);
        assert(uid_cache_size == 1);
    }
    proc_contents = "header\n0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 20001 0 0\n";
    assert(lookup_uid(4, IPPROTO_UDP, 9999, 100) == -1);
    assert(uid_cache_size == 1);
    assert(lookup_uid(4, IPPROTO_UDP, 1234, 100) == 20001);
    struct uid_cache_entry *original = uid_cache;
    proc_contents = "header\n0: 0100007F:04D3 00000000:0000 07 0:0 0:0 0 20002 0 0\n";
    assert(lookup_uid(4, IPPROTO_UDP, 1235, UID_MAX_AGE + 101) == 20002);
    assert(uid_cache == original && uid_cache_size == 1); // Reuse an expired slot.
    release_uid_cache();
}

void test_uid_cache_limit(void) {
    size_t capacity = (UID_CACHE_MAX + 1) * 128;
    char *table = malloc(capacity);
    assert(table != NULL);
    size_t used = (size_t) snprintf(table, capacity, "header\n");
    for (int i = 0; i <= UID_CACHE_MAX; i++) {
        int length = snprintf(table + used, capacity - used,
                "%d: 0100007F:%04X 00000000:0000 07 0:0 0:0 0 %d 0 0\n",
                i, 1000 + i, 10000 + i);
        assert(length > 0 && (size_t) length < capacity - used);
        used += (size_t) length;
    }
    fake_proc = 1;
    proc_contents = table;
    assert(lookup_uid(4, IPPROTO_UDP, 1000 + UID_CACHE_MAX, 0) == 10000 + UID_CACHE_MAX);
    assert(uid_cache_size == UID_CACHE_MAX && allocations == 1);
#ifndef INVIZIBLE_BASELINE_TESTS
    assert(uid_cache_capacity == UID_CACHE_MAX && uid_cache_allocations <= 80);
#endif
    int before = file_closes;
    assert(lookup_uid(4, IPPROTO_UDP, 1000 + UID_CACHE_MAX, 0) == 10000 + UID_CACHE_MAX);
    assert(file_closes == before); // The last row is cached even when the table exceeds the cap.
    free(table);
    release_uid_cache();
}

void test_uid_cache_keys(void) {
    fake_proc = 1;
    proc_contents = proc_one;
    assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10001);
    proc_contents = "header\n0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 20001 0 0\n";
    assert(lookup_uid(4, IPPROTO_TCP, 1234, 0) == 20001);
    proc_contents = "header\n0: 00000000000000000000000001000000:04D2 "
                    "00000000000000000000000000000000:0000 07 0:0 0:0 0 30001 0 0\n";
    assert(lookup_uid(6, IPPROTO_UDP, 1234, 0) == 30001);
    assert(uid_cache_size == 3);
    int before = file_closes;
    assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10001);
    assert(lookup_uid(4, IPPROTO_TCP, 1234, 0) == 20001);
    assert(lookup_uid(6, IPPROTO_UDP, 1234, 0) == 30001);
    assert(file_closes == before);
    release_uid_cache();
}

#ifndef INVIZIBLE_BASELINE_TESTS
// Preserve the previous linear policy as an independent oracle for indexed updates.
static void reference_uid_update(struct uid_cache_entry *entries, int *count,
                                  const struct uid_cache_entry *value, int64_t now) {
    int slot = -1, oldest = 0;
    size_t length = value->version == 4 ? 4 : 16;
    for (int i = 0; i < *count; i++) {
        struct uid_cache_entry *entry = &entries[i];
        if (entry->version == value->version && entry->protocol == value->protocol &&
            entry->sport == value->sport && entry->dport == value->dport &&
            memcmp(entry->saddr, value->saddr, length) == 0 &&
            memcmp(entry->daddr, value->daddr, length) == 0) {
            slot = i;
            break;
        }
        if (slot < 0 && now - entry->time > UID_MAX_AGE) slot = i;
        if (entry->time < entries[oldest].time) oldest = i;
    }
    if (slot < 0) slot = *count < UID_CACHE_MAX ? (*count)++ : oldest;
    entries[slot] = *value;
    entries[slot].time = now;
}

static void assert_uid_cache_matches(const struct uid_cache_entry *entries, int count) {
    assert(uid_cache_size == count && allocations == 1);
    for (int i = 0; i < count; i++) {
        const struct uid_cache_entry *actual = &uid_cache[i], *expected = &entries[i];
        size_t length = expected->version == 4 ? 4 : 16;
        assert(actual->version == expected->version && actual->protocol == expected->protocol);
        assert(actual->sport == expected->sport && actual->dport == expected->dport);
        assert(actual->uid == expected->uid && actual->time == expected->time);
        assert(memcmp(actual->saddr, expected->saddr, length) == 0);
        assert(memcmp(actual->daddr, expected->daddr, length) == 0);
    }
}
#endif

void test_uid_indexed_updates(int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int version = kind == 1 ? 6 : 4;
    int rows = kind == 2 ? 256 : UID_CACHE_MAX + 128;
    size_t capacity = (size_t) rows * 180 + 64;
    char *table = malloc(capacity);
    struct uid_cache_entry *reference = calloc(UID_CACHE_MAX, sizeof(*reference));
    assert(table != NULL && reference != NULL);
    int count = 0;
    const int64_t base = INT64_C(1) << 40;
    const int64_t times[] = {base, base + 100, base + UID_MAX_AGE + 200, base + 50};
    fake_proc = 1; proc_contents = proc_one;
    assert(lookup_uid(4, IPPROTO_UDP, 1234, times[0]) == 10001);
    reference[count++] = uid_cache[0]; // Keep another protocol/family in the snapshot.
    for (int phase = 0; phase < 4; phase++) {
        size_t used = (size_t) snprintf(table, capacity, "header\n");
        for (int i = 0; i < rows; i++) {
            int id = phase == 1 ? rows - i - 1 : i;
            int port = phase < 2 ? 1000 + (id % 97 == 0 ? id / 97 : id)
                     : phase == 2 ? (i % 2 == 0 ? 1000 + i / 2 : 12000 + i) : 25000 + i;
            struct uid_cache_entry value = {.version = version, .protocol = IPPROTO_TCP,
                    .sport = (uint16_t) port, .dport = port % 13 == 0 ? 53 : 0,
                    .uid = 10000 + phase * 100000 + i};
            const char *loopback = version == 4 ? "127.0.0.1" : "::1";
            if (port % 11 != 0)
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6, loopback, value.saddr) == 1);
            if (port % 7 == 0)
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6, loopback, value.daddr) == 1);
            const char *zero = version == 4 ? "00000000" : "00000000000000000000000000000000";
            const char *local = version == 4 ? "0100007F" : "00000000000000000000000001000000";
            int length = snprintf(table + used, capacity - used,
                    "%d: %s:%04X %s:%04X 07 0:0 0:0 0 %d 0 0\n", i,
                    port % 11 == 0 ? zero : local, port,
                    port % 7 == 0 ? local : zero, value.dport, value.uid);
            assert(length > 0 && (size_t) length < capacity - used);
            used += (size_t) length;
            reference_uid_update(reference, &count, &value, times[phase]);
        }
        if (kind == 2 && phase == 0) {
            fail_alloc_tag = "uid update scratch";
            fail_alloc_match = 1;
        }
        if (kind == 2 && phase == 2) {
            int length = snprintf(table + used, capacity - used, "malformed\n");
            assert(length > 0 && (size_t) length < capacity - used);
        }
        proc_contents = table;
        assert(lookup_uid(version, IPPROTO_TCP, 65535, times[phase]) ==
               (kind == 2 && phase == 2 ? -2 : -1));
        if (kind == 2 && phase == 0) assert(fail_alloc_match == 0);
        fail_alloc_tag = NULL;
        assert_uid_cache_matches(reference, count);
    }
    free(reference); free(table);
    release_uid_cache();
#endif
}

void test_uid_indexed_growth_failure(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    char table[64 * 100];
    size_t used = (size_t) snprintf(table, sizeof(table), "header\n");
    for (int i = 0; i < 64; i++) {
        int length = snprintf(table + used, sizeof(table) - used,
                "%d: 0100007F:%04X 00000000:0000 07 0:0 0:0 0 %d 0 0\n",
                i, 1000 + i, 10000 + i);
        assert(length > 0 && (size_t) length < sizeof(table) - used);
        used += (size_t) length;
    }
    fake_proc = 1; proc_contents = table;
    assert(lookup_uid(4, IPPROTO_UDP, 65535, 0) == -1);
    assert(uid_cache_size == 64 && uid_cache_capacity == 64);
    proc_contents = "header\n0: 0100007F:03E8 00000000:0000 07 0:0 0:0 0 10000 0 0\n";
    fail_alloc_tag = "uid update scratch"; fail_alloc_match = 1;
    assert(lookup_uid(4, IPPROTO_UDP, 65535, 0) == -1);
    assert(fail_alloc_match == 1); // Short scans need no workspace even with a larger cache.
    fail_alloc_tag = NULL;
    used = (size_t) snprintf(table, sizeof(table), "header\n");
    for (int i = 1; i < 64; i++) {
        int length = snprintf(table + used, sizeof(table) - used,
                "%d: 0100007F:%04X 00000000:0000 07 0:0 0:0 0 %d 0 0\n",
                i, 1000 + i, 10000 + i);
        assert(length > 0 && (size_t) length < sizeof(table) - used);
        used += (size_t) length;
    }
    int length = snprintf(table + used, sizeof(table) - used,
        "0: 0100007F:07D0 00000000:0000 07 0:0 0:0 0 20000 0 0\n"
        "1: 0100007F:03E8 00000000:0000 07 0:0 0:0 0 7777 0 0\n"
        "2: 0100007F:07D1 00000000:0000 07 0:0 0:0 0 20001 0 0\n");
    assert(length > 0 && (size_t) length < sizeof(table) - used);
    proc_contents = table;
    fail_alloc_tag = "uid_cache extend"; fail_alloc_match = 1;
    assert(lookup_uid(4, IPPROTO_UDP, 65535, 1) == -1);
    fail_alloc_tag = NULL;
    assert(fail_alloc_match == -1 && uid_cache_size == 65 && allocations == 1);
    assert(uid_cache[0].sport == 1000 && uid_cache[0].uid == 7777);
    for (int i = 0; i < uid_cache_size; i++) assert(uid_cache[i].sport != 2000);
    assert(uid_cache[64].sport == 2001 && uid_cache[64].uid == 20001);
    int before = file_closes;
    assert(lookup_uid(4, IPPROTO_UDP, 1000, 1) == 7777);
    assert(lookup_uid(4, IPPROTO_UDP, 2001, 1) == 20001);
    assert(file_closes == before);
    release_uid_cache();
#endif
}

void test_dns_uid_restore(struct arguments *args, int protocol) {
#ifndef INVIZIBLE_BASELINE_TESTS
    enum { LIVE, ROOT, MISSING, WRONG_PORT, CLOSED, DISCONNECTED, UNKNOWN,
           DIFFERENT_CONNECTION, FRESH, DENIED, WRONG_SOURCE, WRONG_DEST,
           WRONG_FAMILY, WRONG_DPORT, SCENARIOS };
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    running.fwd53 = 1;
    capture_packets = protocol == IPPROTO_TCP;
    policy_requires_known_uid = true;
    for (int version = 4; version <= 6; version += 2) {
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int scenario = 0; scenario < SCENARIOS; scenario++) {
                _Alignas(8) uint8_t packet[128], output[2048];
                const char *dest = version == 4 ? "9.9.9.9" : "2620:fe::fe";
                size_t off = ip_header(packet, version, protocol, 20, dest);
                if (protocol == IPPROTO_UDP) {
                    struct udphdr *udp = (void *) (packet + off);
                    udp->source = htons(4321); udp->dest = htons(53); udp->len = htons(20);
                } else {
                    struct tcphdr *tcp = (void *) (packet + off);
                    tcp->source = htons(4321); tcp->dest = htons(53);
                    tcp->doff = 5; tcp->syn = 1; tcp->seq = htonl(99);
                }
                packet_checksums(packet, off + 20, off, protocol);
                struct ng_session target = uid_session(version, protocol,
                        packet + (version == 4 ? 12 : 8), packet + (version == 4 ? 16 : 24));
                jint stored_uid = scenario == ROOT ? 0 : scenario == UNKNOWN ? -1 : 12345;
                if (protocol == IPPROTO_UDP) {
                    target.udp.uid = stored_uid; target.udp.dest = htons(53);
                    if (scenario == WRONG_PORT) target.udp.source++;
                    if (scenario == CLOSED) target.udp.state = UDP_CLOSED;
                    if (scenario == DIFFERENT_CONNECTION) target.udp.state = UDP_FINISHING;
                    if (scenario == WRONG_SOURCE) target.udp.saddr.ip6.s6_addr[0] ^= 1;
                    if (scenario == WRONG_DEST) target.udp.daddr.ip6.s6_addr[0] ^= 1;
                    if (scenario == WRONG_FAMILY) target.udp.version = version == 4 ? 6 : 4;
                    if (scenario == WRONG_DPORT) target.udp.dest = htons(5353);
                } else {
                    target.tcp.uid = stored_uid; target.tcp.dest = htons(53);
                    if (scenario == WRONG_PORT) target.tcp.source++;
                    if (scenario == CLOSED) target.tcp.state = TCP_CLOSE;
                    if (scenario == DIFFERENT_CONNECTION) target.tcp.remote_start++;
                    if (scenario == WRONG_SOURCE) target.tcp.saddr.ip6.s6_addr[0] ^= 1;
                    if (scenario == WRONG_DEST) target.tcp.daddr.ip6.s6_addr[0] ^= 1;
                    if (scenario == WRONG_FAMILY) target.tcp.version = version == 4 ? 6 : 4;
                    if (scenario == WRONG_DPORT) target.tcp.dest = htons(5353);
                }
                if (scenario == DISCONNECTED) target.socket = -1;
                set_test_sessions(args->ctx, scenario == MISSING ? NULL : &target);
                uid_lookup_result = scenario == FRESH ? 23456 : -1;
                fake_proc = legacy;
                proc_contents = "header\n";
                proc6_contents = NULL;
                char table[256];
                if (scenario == FRESH) {
                    snprintf(table, sizeof(table), "header\n0: %s:10E1 %s:0000 07 0:0 0:0 0 23456 0 0\n",
                             version == 4 ? "0200000A" : "B80D0120000000000000000001000000",
                             version == 4 ? "00000000" : "00000000000000000000000000000000");
                    proc_contents = table;
                    if (version == 4) proc6_contents = "header\n";
                }
                uid_lookups = policy_checks = udp_calls = tcp_calls = 0;
                deny_address = scenario == DENIED;
                const char *redirect = version == 4 ? "127.0.0.1" : "::1";
                strcpy(policy_redirect.raddr, redirect);
                policy_redirect.rport = 5354;
                expect_destination(version, redirect, 5354);
                handle_ip(&running, packet, off + 20, -1, 0, SESSION_MAX);
                jint expected = scenario == FRESH ? 23456 :
                                scenario == ROOT ? 0 :
                                scenario == LIVE || scenario == DENIED ? 12345 : -1;
                bool allowed = expected >= 0 && scenario != DENIED;
                assert(policy_checks == 1 && policy_uid == expected);
                assert(uid_lookups == !legacy);
                assert((protocol == IPPROTO_TCP ? target.tcp.uid : target.udp.uid) == stored_uid);
                if (protocol == IPPROTO_UDP) {
                    if (allowed) {
                        assert(destination_calls == 1 && args->ctx->ng_session == &target);
                    } else {
                        struct ng_session *blocked = args->ctx->ng_session;
                        assert(blocked != NULL && blocked != &target && blocked->udp.state == UDP_BLOCKED);
                        assert(blocked->udp.uid == expected && destination_calls == 0);
                        tun_packet(tunnel[0], output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
                        set_test_sessions(args->ctx, NULL);
                        ng_free(blocked, __FILE__, __LINE__);
                    }
                } else {
                    assert(tcp_calls == 1 && tcp_last_allowed == allowed);
                    if (scenario == ROOT) {
                        // Root-owned TCP DNS must also obey policy without forwarding.
                        running.fwd53 = 0;
                        deny_address = 1;
                        uid_lookups = policy_checks = tcp_calls = 0;
                        handle_ip(&running, packet, off + 20, -1, 0, SESSION_MAX);
                        assert(uid_lookups == !legacy && policy_checks == 1 && policy_uid == 0);
                        assert(tcp_calls == 1 && tcp_last_allowed == 0);
                        deny_address = 0;
                        running.fwd53 = 1;
                    }
                }
                set_test_sessions(args->ctx, NULL);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0 && !is_readable(tunnel[0]));
            }
        }
    }
    args->ctx->sdk = 29;
    capture_packets = deny_address = 0;
    policy_requires_known_uid = false;
    memset(&policy_redirect, 0, sizeof(policy_redirect));
    close(tunnel[0]); close(tunnel[1]);
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static void blocked_uid_lookup(int version, bool legacy, jint uid, char *table, size_t size) {
    uid_lookup_result = uid;
    fake_proc = legacy;
    proc_contents = "header\n";
    proc6_contents = version == 4 ? "header\n" : NULL;
    if (uid >= 0) {
        int length = snprintf(table, size, "header\n0: %s:3039 %s:0000 07 0:0 0:0 0 %d 0 0\n",
                version == 4 ? "0200000A" : "B80D0120000000000000000001000000",
                version == 4 ? "00000000" : "00000000000000000000000000000000", uid);
        assert(length > 0 && (size_t) length < size);
        proc_contents = table;
    }
}

static void blocked_uid_unreachable(int tunnel, int version) {
    uint8_t output[2048];
    tun_packet(tunnel, output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
}
#endif

void test_dns_policy(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    real_network = 1; // Local sockets only; sendto is captured by expect_destination.
    for (int version = 4; version <= 6; version += 2) {
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int scenario = 0; scenario < (kind == 0 ? 12 : 8); scenario++) {
                running.fwd53 = scenario & 1;
                int action = scenario / 2;
                // Basic cases: allowed root/ordinary/unknown owners, denied ordinary/unknown.
                // Lifecycle cases: full table, active denial, blocked promotion, full promotion.
                char table[256];
                jint initial = kind == 0 ? (action == 0 ? 0 : action == 2 || action >= 4 ? -1 : 12345)
                                        : kind == 2 ? (action == 0 ? 0 : action == 2 ? -1 : 12345)
                                        : kind == 3 ? (action == 2 ? 12345 : action == 3 ? 0 : -1)
                                                    : action >= 2 ? -1 : 12345;
                blocked_uid_lookup(version, legacy, initial, table, sizeof(table));
                deny_address = kind == 0 ? action == 3 || action == 4 : kind == 2 || kind == 3 || action >= 2;
                memset(&policy_redirect, 0, sizeof(policy_redirect));
                const char *dest = version == 4 ? "203.0.113.2" : "2001:db8::2";
                bool redirected = running.fwd53 && !(kind == 0 && action == 5);
                const char *target = redirected ? (version == 4 ? "127.0.0.1" : "::1") : dest;
                if (redirected) {
                    strcpy(policy_redirect.raddr, target);
                    policy_redirect.rport = 5354;
                }
                _Alignas(8) uint8_t packet[128];
                size_t off = ip_header(packet, version, IPPROTO_UDP, 20, dest);
                struct udphdr *udp = (void *) (packet + off);
                udp->source = htons(12345); udp->dest = htons(53); udp->len = htons(20);
                packet_checksums(packet, off + 20, off, IPPROTO_UDP);
                expect_destination(version, target, redirected ? 5354 : 53);
                int queries = uid_lookups;
                udp_lookups = policy_checks = 0;
                bool full = kind == 1 && action == 0;
                handle_ip(&running, packet, off + 20, epoll_fd, full ? SESSION_MAX : 0, SESSION_MAX);
                struct ng_session *s = args->ctx->ng_session;
                assert(policy_checks == !full && uid_lookups == queries + (!legacy && !full));
                if (full) {
                    assert(s == NULL && destination_calls == 0);
                } else {
                    assert(s != NULL && s->udp.uid == initial);
                    assert(s->udp.uid_retry_at == -1); // DNS checks ownership on each request.
                    bool denied = deny_address != 0;
                    assert(s->udp.state == (denied ? UDP_BLOCKED : UDP_ACTIVE));
                    assert(destination_calls == !denied && policy_uid == initial);
                    if (denied) blocked_uid_unreachable(tunnel[0], version);
                    int socket = s->socket;
                    if (kind == 0) {
                        // Check policy again on an immediate repeat, preserving known owners.
                        // A failed OS lookup may restore only an exact live known owner.
                        ng_free(uid_cache, __FILE__, __LINE__);
                        uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                        blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                        handle_ip(&running, packet, off + 20, epoll_fd, 1, SESSION_MAX);
                        assert(policy_checks == 2 && args->ctx->ng_session == s && s->socket == socket);
                        assert(policy_uid == (denied ? -1 : initial));
                        assert(destination_calls == (denied ? 0 : 2));
                        assert(s->udp.uid == initial);
                        if (denied) blocked_uid_unreachable(tunnel[0], version);
                    } else if (kind == 3) {
                        // Retain a successful lookup while capacity postpones reopening.
                        ng_free(uid_cache, __FILE__, __LINE__);
                        uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                        jint resolved = action == 0 || action == 2 ? 0 : 23456;
                        jint retained = initial >= 0 ? initial : resolved;
                        blocked_uid_lookup(version, legacy, resolved, table, sizeof(table));
                        deny_address = 0;
                        handle_ip(&running, packet, off + 20, epoll_fd, SESSION_MAX + (action & 1), SESSION_MAX);
                        assert(policy_checks == 2 && policy_uid == resolved);
                        assert(args->ctx->ng_session == s && s->udp.state == UDP_BLOCKED && s->udp.uid == retained);
                        assert(s->socket == -1 && sockets == 0 && destination_calls == 0);
                        assert(!is_readable(tunnel[0]));
                        ng_free(uid_cache, __FILE__, __LINE__);
                        uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                        blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                        for (int deny = 0; deny <= 1; deny++) {
                            deny_address = deny;
                            handle_ip(&running, packet, off + 20, epoll_fd, SESSION_MAX, SESSION_MAX);
                            // Stored blocked ownership is metadata, never a fallback for policy.
                            assert(policy_checks == 3 + deny && policy_uid == -1);
                            assert(args->ctx->ng_session == s && s->udp.state == UDP_BLOCKED && s->udp.uid == retained);
                            assert(s->socket == -1 && sockets == 0 && destination_calls == 0);
                            if (deny) blocked_uid_unreachable(tunnel[0], version);
                            assert(!is_readable(tunnel[0]));
                        }
                        deny_address = 0;
                        handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                        struct ng_session *active = args->ctx->ng_session;
                        assert(policy_checks == 5 && policy_uid == -1);
                        assert(active != s && active->udp.state == UDP_ACTIVE && active->udp.uid == -1);
                        assert(s->udp.state == UDP_CLOSED && s->udp.uid == retained && destination_calls == 1);
                        assert(find_udp_session(&running, packet, packet + off) == active);
                    } else if (kind == 2) {
                        ng_free(uid_cache, __FILE__, __LINE__);
                        uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                        blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                        for (int retry = 0; retry < 3; retry++) {
                            // Repeated failed lookups must preserve a denied record's owner.
                            deny_address = 1;
                            handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                            assert(policy_checks == retry + 2 && policy_uid == -1);
                            assert(args->ctx->ng_session == s && s->socket == -1);
                            assert(s->udp.state == UDP_BLOCKED && s->udp.uid == initial);
                            assert(destination_calls == 0 && sockets == 0);
                            blocked_uid_unreachable(tunnel[0], version);
                        }
                        // Permitting unknown traffic must also permit this flow to reopen.
                        // Capacity remains a separate constraint, even for unknown owners.
                        deny_address = 0;
                        handle_ip(&running, packet, off + 20, epoll_fd, SESSION_MAX, SESSION_MAX);
                        assert(policy_checks == 5 && policy_uid == -1);
                        assert(args->ctx->ng_session == s && s->udp.state == UDP_BLOCKED);
                        assert(s->udp.uid == initial && destination_calls == 0 && sockets == 0);
                        assert(!is_readable(tunnel[0]));
                        handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                        struct ng_session *unknown = args->ctx->ng_session;
                        assert(policy_checks == 6 && policy_uid == -1);
                        assert(unknown != s && unknown->udp.state == UDP_ACTIVE && unknown->udp.uid == -1);
                        assert(s->udp.state == UDP_CLOSED && s->udp.uid == initial && destination_calls == 1);
                        assert(find_udp_session(&running, packet, packet + off) == unknown);
                        jint recovered = action == 0 || action == 2 ? 0 : action == 3 ? 23456 : 12345;
                        blocked_uid_lookup(version, legacy, recovered, table, sizeof(table));
                        deny_address = 1; // Successful ownership alone must not bypass policy.
                        handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                        struct ng_session *blocked = args->ctx->ng_session;
                        assert(policy_checks == 7 && policy_uid == recovered);
                        assert(blocked != unknown && blocked->udp.uid == recovered);
                        assert(blocked->udp.state == UDP_BLOCKED && destination_calls == 1);
                        assert(unknown->udp.state == UDP_FINISHING);
                        // The retired flow's earlier traffic must retain the owner just learned.
                        assert(unknown->udp.uid == recovered && unknown->udp.sent > 0);
                        blocked_uid_unreachable(tunnel[0], version);
                        deny_address = 0;
                        handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                        struct ng_session *active = args->ctx->ng_session;
                        assert(policy_checks == 8 && policy_uid == recovered);
                        assert(active != blocked && active->udp.uid == recovered && active->udp.state == UDP_ACTIVE);
                        assert(blocked->udp.state == UDP_CLOSED && destination_calls == 2);
                        assert(find_udp_session(&running, packet, packet + off) == active);
                    } else if (action == 1) {
                        deny_address = 1;
                        handle_ip(&running, packet, off + 20, epoll_fd, 1, SESSION_MAX);
                        struct ng_session *blocked = args->ctx->ng_session;
                        assert(blocked != s && blocked->udp.state == UDP_BLOCKED && blocked->socket == -1);
                        assert(s->udp.state == UDP_FINISHING && destination_calls == 1 && policy_checks == 2);
                        assert(find_udp_session(&running, packet, packet + off) == blocked);
                        blocked_uid_unreachable(tunnel[0], version);
                        struct epoll_event stale = {.events = EPOLLIN | EPOLLERR, .data.ptr = s};
                        check_udp_socket(&running, &stale);
                        assert(!is_readable(tunnel[0]));
                    } else {
                        deny_address = 0;
                        blocked_uid_lookup(version, legacy, 0, table, sizeof(table));
                        handle_ip(&running, packet, off + 20, epoll_fd,
                                  action == 3 ? SESSION_MAX : 0, SESSION_MAX);
                        assert(policy_checks == 2 && policy_uid == 0);
                        if (action == 3) {
                            assert(args->ctx->ng_session == s && s->udp.state == UDP_BLOCKED);
                            assert(destination_calls == 0 && s->socket == -1);
                            // Recover as soon as an active slot is available.
                            handle_ip(&running, packet, off + 20, epoll_fd, 0, SESSION_MAX);
                        }
                        struct ng_session *active = args->ctx->ng_session;
                        assert(active != s && active->udp.state == UDP_ACTIVE && active->udp.uid == 0);
                        assert(s->udp.state == UDP_CLOSED && destination_calls == 1);
                        assert(find_udp_session(&running, packet, packet + off) == active);
                    }
                }
                clear(args->ctx);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0 && !is_readable(tunnel[0]));
            }
        }
    }
    close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
    real_network = 0; deny_address = 0;
    args->ctx->sdk = 29;
    memset(&policy_redirect, 0, sizeof(policy_redirect));
#endif
}

void test_udp_retry_routes(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    real_network = 1;
    test_monotonic_enabled = true;
    for (int version = 4; version <= 6; version += 2) {
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int scenario = 0; scenario < 5; scenario++) {
                // Unchanged redirect, changed port, changed family, active->blocked->active,
                // and blocked->active with an unchanged redirect.
                char table[256];
                blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                deny_address = scenario == 4;
                strcpy(policy_redirect.raddr, "127.0.0.1"); policy_redirect.rport = 5354;
                const char *target = version == 4 ? "127.0.0.1" : "::1";
                int target_version = version;
                uint16_t port = 5354;
                expect_destination(target_version, target, port);
                _Alignas(8) uint8_t packet[128];
                size_t length = checksum_packet(packet, version, IPPROTO_UDP, 1);
                size_t off = version == 4 ? 20 : 40;
                test_monotonic_ms = 10000;
                policy_checks = 0;
                handle_ip(&running, packet, length, epoll_fd, 0, SESSION_MAX);
                struct ng_session *current = args->ctx->ng_session;
                assert(current != NULL && current->udp.uid < 0 && current->udp.uid_retries == 0);
                assert(current->udp.uid_retry_at == 11000 && policy_checks == 1);
                if (deny_address) blocked_uid_unreachable(tunnel[0], version);
                const long long deadlines[] = {11000, 13000, 17000};
                for (int retry = 0; retry < UDP_UID_RETRY_MAX; retry++) {
                    struct ng_session *previous = current;
                    int socket = previous->socket;
                    bool denied = scenario == 3 && retry == 0;
                    deny_address = denied;
                    if (scenario == 1 || (scenario == 3 && retry == 2)) port++;
                    if (scenario == 2) {
                        target_version = retry % 2 == 0 ? (version == 4 ? 6 : 4) : version;
                        target = target_version == 4 ? "192.0.2.10" : "2001:db8::10";
                    }
                    // Different textual forms of the same IPv6 endpoint must compare equal.
                    strcpy(policy_redirect.raddr, scenario == 0 && version == 6 ? "0:0:0:0:0:0:0:1" : target);
                    policy_redirect.rport = port;
                    expect_destination(target_version, target, port);
                    test_monotonic_ms = deadlines[retry];
                    int clocks = test_monotonic_calls, queries = uid_lookups;
                    handle_ip(&running, packet, length, epoll_fd, 1, SESSION_MAX);
                    current = find_udp_session(&running, packet, packet + off);
                    assert(current != NULL && current->udp.uid < 0);
                    assert(current->udp.uid_retries == retry + 1);
                    assert(current->udp.uid_retry_at == deadlines[retry] + (UDP_UID_RETRY_INTERVAL << (retry + 1)));
                    assert(test_monotonic_calls == clocks + 1); // Replacement must not restart its timer.
                    assert(uid_lookups == queries + !legacy && policy_checks == retry + 2);
                    assert(current->udp.state == (denied ? UDP_BLOCKED : UDP_ACTIVE));
                    assert(destination_calls == !denied);
                    bool replaced = scenario == 1 || scenario == 2 || scenario == 3 ||
                                    (scenario == 4 && retry == 0);
                    assert((current != previous) == replaced);
                    if (replaced) {
                        assert(previous->udp.state == (socket < 0 ? UDP_CLOSED : UDP_FINISHING));
                        if (socket >= 0) {
                            struct epoll_event stale = {.events = EPOLLIN | EPOLLERR, .data.ptr = previous};
                            check_udp_socket(&running, &stale);
                            assert(!is_readable(tunnel[0]) || denied);
                            check_udp_session(&running, previous, 1, SESSION_MAX);
                            assert(previous->socket == -1 && previous->udp.state == UDP_CLOSED);
                        }
                    } else assert(current->socket == socket);
                    if (denied) blocked_uid_unreachable(tunnel[0], version);
                    assert(sockets == (denied ? 0 : 1));
                }
                int clocks = test_monotonic_calls, queries = uid_lookups, files = file_closes;
                int socket = current->socket, sends = destination_calls;
                test_monotonic_ms = 1000000;
                for (int i = 0; i < 20; i++)
                    handle_ip(&running, packet, length, epoll_fd, 1, SESSION_MAX);
                assert(args->ctx->ng_session == current && current->socket == socket);
                assert(current->udp.uid_retries == UDP_UID_RETRY_MAX && policy_checks == UDP_UID_RETRY_MAX + 1);
                assert(uid_lookups == queries && file_closes == files && test_monotonic_calls == clocks);
                assert(destination_calls == sends + 20 && sockets == 1);
                clear(args->ctx);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0 && !is_readable(tunnel[0]));
            }
        }
    }
    args->ctx->sdk = 29;
    real_network = 0; test_monotonic_enabled = false; deny_address = 0;
    memset(&policy_redirect, 0, sizeof(policy_redirect));
    close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_unknown_uid_policy(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    capture_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        for (int lookup = 0; lookup < 3; lookup++) {
            args->ctx->sdk = lookup == 0 ? 29 : 28;
            for (int kind = 0; kind < 4; kind++) {
                int protocol = kind == 0 ? IPPROTO_TCP : kind == 1 || kind == 2 ? IPPROTO_UDP :
                               version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                for (int deny = 0; deny <= 1; deny++) {
                    fake_proc = lookup != 0;
                    proc_contents = proc6_contents = "header\n";
                    proc6_open_error = lookup == 2 ? EACCES : 0;
                    uid_lookup_result = -1;
                    deny_address = deny;
                    running.fwd53 = 0;
                    _Alignas(8) uint8_t packet[128];
                    size_t length = checksum_packet(packet, version, protocol, kind == 2 ? 12 : 0);
                    size_t off = version == 4 ? 20 : 40;
                    if (kind == 2) {
                        struct udphdr *udp = (void *) (packet + off);
                        udp->dest = htons(53);
                        memset(packet + off + sizeof(*udp), 0, 12);
                        packet_checksums(packet, length, off, protocol);
                    }
                    int calls = protocol_calls(protocol), checks = policy_checks;
                    handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                    assert(policy_checks == checks + 1 && policy_uid < 0);
                    assert(protocol_calls(protocol) == calls + (protocol == IPPROTO_TCP || !deny));
                    if (protocol == IPPROTO_TCP) assert(tcp_last_allowed == !deny);
                    if (protocol == IPPROTO_UDP && deny) {
                        assert(args->ctx->ng_session != NULL && args->ctx->ng_session->udp.state == UDP_BLOCKED);
                        blocked_uid_unreachable(tunnel[0], version);
                    } else assert(args->ctx->ng_session == NULL);
                    clear(args->ctx);
                    release_uid_cache();
                    assert(sockets == 0 && !is_readable(tunnel[0]));
                }
            }
        }
    }
    args->ctx->sdk = 29;
    capture_packets = deny_address = 0;
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_denied_icmp_uid(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    capture_packets = 1; // Exercise IP policy/metadata without opening ping sockets.
    deny_address = 1;
    for (int version = 4; version <= 6; version += 2) {
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int scenario = 0; scenario < 7; scenario++) {
                _Alignas(8) uint8_t packet[128];
                size_t length = checksum_packet(packet, version, protocol, 0);
                size_t off = version == 4 ? 20 : 40;
                struct icmp *echo = (void *) (packet + off);
                echo->icmp_id = htons(12345);
                packet_checksums(packet, length, off, protocol);
                struct ng_session session = {.protocol = (uint8_t) protocol, .socket = 42};
                session.icmp.version = version;
                session.icmp.uid = scenario == 2 ? 23456 : -1;
                session.icmp.id = echo->icmp_id;
                memcpy(&session.icmp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
                memcpy(&session.icmp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
                if (scenario == 3) session.socket = -1;
                if (scenario == 4) session.icmp.stop = 1;
                if (scenario == 5) session.icmp.id++;
                if (scenario == 6) ((uint8_t *) &session.icmp.daddr)[0] ^= 1;
                set_test_sessions(args->ctx, &session);
                char table[256];
                jint resolved = scenario == 0 ? 0 : 12345;
                blocked_uid_lookup(version, legacy, resolved, table, sizeof(table));
                int checks = policy_checks, calls = icmp_calls;
                handle_ip(args, packet, length, -1, 0, SESSION_MAX);
                jint expected = scenario == 2 ? 23456 : scenario >= 3 ? -1 : resolved;
                assert(policy_checks == checks + 1 && policy_uid == resolved);
                assert(icmp_calls == calls && args->ctx->ng_session == &session);
                assert(session.icmp.uid == expected);
                // A subsequent failed lookup may restore only a retained live exact owner.
                ng_free(uid_cache, __FILE__, __LINE__);
                uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                handle_ip(args, packet, length, -1, 0, SESSION_MAX);
                assert(policy_checks == checks + 2 && policy_uid == expected);
                assert(session.icmp.uid == expected && icmp_calls == calls);
                set_test_sessions(args->ctx, NULL);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0);
            }
        }
    }
    args->ctx->sdk = 29;
    capture_packets = deny_address = 0;
#endif
}

void test_icmp_capacity(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    enum { ROOT, OWNER, UNKNOWN, KNOWN, DENIED, STOPPED, SOCKETLESS, OTHER_ID, OTHER_ADDRESS, NEW_FLOW };
    int upstream[2], tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, upstream) == 0);
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    for (int version = 4; version <= 6; version += 2) {
        int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int extra = 0; extra <= 1; extra++) {
                for (int scenario = ROOT; scenario <= NEW_FLOW; scenario++) {
                    _Alignas(8) uint8_t packet[128];
                    size_t length = checksum_packet(packet, version, protocol, 0);
                    size_t off = version == 4 ? 20 : 40;
                    struct icmp *echo = (void *) (packet + off);
                    echo->icmp_id = htons(12345);
                    packet_checksums(packet, length, off, protocol);
                    struct ng_session session = {.protocol = (uint8_t) protocol, .socket = upstream[0]};
                    session.icmp.version = version; session.icmp.id = echo->icmp_id;
                    session.icmp.uid = scenario == KNOWN ? 23456 : -1;
                    memcpy(&session.icmp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
                    memcpy(&session.icmp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
                    if (scenario == STOPPED) session.icmp.stop = 1;
                    if (scenario == SOCKETLESS) session.socket = -1;
                    if (scenario == OTHER_ID) session.icmp.id++;
                    if (scenario == OTHER_ADDRESS) ((uint8_t *) &session.icmp.daddr)[0] ^= 1;
                    struct ng_session *head = scenario == NEW_FLOW ? NULL : &session;
                    set_test_sessions(args->ctx, head);
                    char table[256];
                    jint resolved = scenario == ROOT ? 0 : scenario == UNKNOWN || scenario == KNOWN ? -1 : 12345;
                    blocked_uid_lookup(version, legacy, resolved, table, sizeof(table));
                    deny_address = scenario == DENIED;
                    expect_destination(version, version == 4 ? "203.0.113.2" : "2001:db8::2", 0);
                    int queries = uid_lookups, checks = policy_checks;
                    bool existing = scenario <= DENIED;
                    handle_ip(&running, packet, length, -1, SESSION_MAX + extra, SESSION_MAX);
                    assert(args->ctx->ng_session == head && session.socket == (scenario == SOCKETLESS ? -1 : upstream[0]));
                    assert(uid_lookups == queries + (existing && !legacy));
                    assert(policy_checks == checks + existing && destination_calls == (scenario < DENIED));
                    jint retained = scenario == KNOWN ? 23456 : existing ? resolved : -1;
                    assert(session.icmp.uid == retained);
                    if (existing) assert(policy_uid == retained);
                    if (scenario < DENIED) {
                        assert(!session.icmp.stop && echo->icmp_id == (uint16_t) ~session.icmp.id);
                        uint8_t reply[ICMP_MINLEN] = {0};
                        reply[0] = version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY;
                        assert(write(upstream[1], reply, sizeof(reply)) == sizeof(reply));
                        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &session};
                        check_icmp_socket(&running, &event);
                        _Alignas(8) uint8_t output[2048];
                        assert(tun_packet(tunnel[0], output, version, protocol) == off + ICMP_MINLEN);
                        assert(output[off] == reply[0] && load16(output + off + 4) == 12345);
                    }
                    assert(!is_readable(tunnel[0]) && session.icmp.stop == (scenario == STOPPED));
                    set_test_sessions(args->ctx, NULL);
                    release_uid_cache();
                    assert(allocations == 0 && sockets == 0);
                }
            }
        }
    }
    args->ctx->sdk = 29; deny_address = 0;
    close(upstream[0]); close(upstream[1]); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_blocked_udp_uid_retry(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    enum { BUDGET, INITIAL_CLOCK, RETRY_CLOCK, KNOWN_ROOT, KNOWN_OWNER, BUDGET_SCENARIOS };
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    real_network = 1; // Socket creation is real; sendto is captured locally.
    test_monotonic_enabled = true;
    for (int version = 4; version <= 6; version += 2) {
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            int scenarios = kind == 0 ? BUDGET_SCENARIOS : kind == 1 ? 16 : 2;
            for (int scenario = 0; scenario < scenarios; scenario++) {
                _Alignas(8) uint8_t packet[128];
                char table[256];
                const char *dest = version == 4 ? "203.0.113.2" : "2001:db8::2";
                size_t off = ip_header(packet, version, IPPROTO_UDP, 9, dest);
                struct udphdr *udp = (void *) (packet + off);
                udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(9);
                packet[off + 8] = 42;
                packet_checksums(packet, off + 9, off, IPPROTO_UDP);
                bool known = kind == 0 && scenario >= KNOWN_ROOT;
                jint owner = known ? (scenario == KNOWN_ROOT ? 0 : 12345) : -1;
                blocked_uid_lookup(version, legacy, owner, table, sizeof(table));
                test_monotonic_ms = 10000;
                test_monotonic_failures = kind == 0 && scenario == INITIAL_CLOCK ? 1 : 0;
                uid_lookups = policy_checks = 0;
                deny_address = 1;
                memset(&policy_redirect, 0, sizeof(policy_redirect));
                expect_destination(version, dest, 5000);
                handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                blocked_uid_unreachable(tunnel[0], version);
                struct ng_session *old = args->ctx->ng_session;
                assert(old != NULL && old->udp.state == UDP_BLOCKED && old->socket == -1);
                assert(old->udp.uid == owner && old->udp.uid_retries == 0);
                assert(old->udp.uid_retry_at == (known || (kind == 0 && scenario == INITIAL_CLOCK) ? -1 : 11000));
                assert(sockets == 0 && destination_calls == 0 && policy_checks == 1);
                int queries = uid_lookups, closes = file_closes;
                if (kind == 0) {
                    // Bursts before the deadline must retain the same cached block.
                    for (int i = 0; i < 16; i++) {
                        handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                        blocked_uid_unreachable(tunnel[0], version);
                    }
                    assert(uid_lookups == queries && file_closes == closes && policy_checks == 1);
                    if (scenario == BUDGET) {
                        const long long deadlines[] = {11000, 13000, 17000};
                        for (int i = 0; i < UDP_UID_RETRY_MAX; i++) {
                            test_monotonic_ms = deadlines[i];
                            handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                            blocked_uid_unreachable(tunnel[0], version);
                            assert(old->udp.uid_retries == i + 1 && old->udp.uid < 0);
                            assert(policy_checks == i + 2 && policy_uid < 0);
                            assert(uid_lookups == queries + (legacy ? 0 : i + 1));
                            assert(file_closes == closes + (legacy ? (version == 4 ? 2 : 1) * (i + 1) : 0));
                        }
                    }
                    if (scenario == RETRY_CLOCK) test_monotonic_failures = 1;
                    blocked_uid_lookup(version, legacy, 23456, table, sizeof(table));
                    queries = uid_lookups; closes = file_closes;
                    test_monotonic_ms = 1000000;
                    deny_address = 0; // Known blocks must survive even if policy would now allow.
                    handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                    blocked_uid_unreachable(tunnel[0], version);
                    int clock_reads = test_monotonic_calls;
                    for (int i = 0; i < 16; i++) {
                        handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                        blocked_uid_unreachable(tunnel[0], version);
                    }
                    assert(uid_lookups == queries && file_closes == closes &&
                           policy_checks == (scenario == BUDGET ? UDP_UID_RETRY_MAX + 1 : 1));
                    assert(test_monotonic_calls == clock_reads && old->udp.uid == owner);
                    assert(args->ctx->ng_session == old && old->next == NULL && sockets == 0);
                } else {
                    owner = kind == 3 ? -1 : kind == 1 && !(scenario & 1) ? 0 : 12345;
                    bool deny = kind == 1 && (scenario & 2) != 0;
                    bool redirect = kind == 1 ? (scenario & 4) != 0 : kind == 3 && scenario == 1;
                    int target_version = redirect && !(scenario & 8) ? (version == 4 ? 6 : 4) : version;
                    const char *target = redirect ? (target_version == 4 ? "192.0.2.10" : "2001:db8::10") : dest;
                    blocked_uid_lookup(version, legacy, owner, table, sizeof(table));
                    deny_address = deny;
                    if (redirect) {
                        strcpy(policy_redirect.raddr, target);
                        policy_redirect.rport = 5354;
                    }
                    expect_destination(target_version, target, redirect ? 5354 : 5000);
                    test_monotonic_ms = 11000;
                    // A full active-session table must not consume this record's retry.
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                    blocked_uid_unreachable(tunnel[0], version);
                    assert(uid_lookups == queries && file_closes == closes && policy_checks == 1);
                    assert(old->udp.uid < 0 && old->udp.uid_retries == 0 && sockets == 0);
                    if (kind == 2) {
                        if (scenario == 0) { fail_alloc_tag = "udp session"; fail_alloc_match = 1; }
                        else failure = PROTECT;
                    }
                    handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                    assert(policy_checks == 2 && policy_uid == owner && old->udp.uid == owner);
                    if (kind == 2) {
                        assert(old->udp.state == UDP_CLOSED && old->socket == -1);
                        assert(find_udp_session(&running, packet, packet + off) == NULL);
                        assert(destination_calls == 0 && sockets == 0 && allocations == 1 + (uid_cache != NULL));
                        if (scenario == 0) assert(fail_alloc_match == 0);
                        fail_alloc_tag = NULL; failure = NONE;
                        handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                    }
                    struct ng_session *current = find_udp_session(&running, packet, packet + off);
                    assert(current != NULL && current->udp.uid == owner);
                    if (deny) {
                        blocked_uid_unreachable(tunnel[0], version);
                        assert(current == old && old->udp.state == UDP_BLOCKED && sockets == 0);
                        queries = uid_lookups; closes = file_closes;
                        deny_address = 0; test_monotonic_ms = 1000000;
                        handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
                        blocked_uid_unreachable(tunnel[0], version);
                        assert(uid_lookups == queries && file_closes == closes && policy_checks == 2);
                    } else {
                        assert(current != old && current->udp.state == UDP_ACTIVE && current->socket >= 0);
                        assert(old->udp.state == UDP_CLOSED && old->socket == -1);
                        assert(destination_calls == 1 && sockets == 1);
                        assert(allocations == 2 + (uid_cache != NULL));
                        unindex_session(args->ctx, old); // Cleanup of the retired record must leave the new index intact.
                        assert(find_udp_session(&running, packet, packet + off) == current);
                        queries = uid_lookups; closes = file_closes;
                        int checks = policy_checks, socket = current->socket;
                        memset(&policy_redirect, 0, sizeof(policy_redirect));
                        handle_ip(&running, packet, off + 9, epoll_fd, 1, SESSION_MAX);
                        assert(destination_calls == 2 && current->socket == socket);
                        assert(uid_lookups == queries && file_closes == closes && policy_checks == checks);
                    }
                }
                clear(args->ctx);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0 && !is_readable(tunnel[0]));
            }
        }
    }
    real_network = 0;
    test_monotonic_enabled = false;
    args->ctx->sdk = 29;
    deny_address = 0;
    close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_udp_uid_retry(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    real_network = 1; // Real local sockets, with sendto captured below.
    test_monotonic_enabled = true;
    for (int version = 4; version <= 6; version += 2) {
        int scenarios = kind == 1 ? 64 : 2;
        for (int scenario = 0; scenario < scenarios; scenario++) {
            bool legacy = kind == 1 ? (scenario & 8) != 0 : kind == 0 && scenario == 1;
            args->ctx->sdk = legacy ? 28 : 29;
            _Alignas(8) uint8_t packet[128], output[2048];
            const char *dest = version == 4 ? "203.0.113.2" : "2001:db8::2";
            size_t off = ip_header(packet, version, IPPROTO_UDP, 9, dest);
            struct udphdr *udp = (void *) (packet + off);
            udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(9);
            packet[off + 8] = 42;
            packet_checksums(packet, off + 9, off, IPPROTO_UDP);
            test_monotonic_ms = 10000;
            test_monotonic_failures = kind == 2 && scenario == 0 ? 1 : 0;
            uid_lookup_result = -1;
            fake_proc = legacy;
            proc_contents = "header\n";
            proc6_contents = NULL;
            uid_lookups = policy_checks = 0;
            deny_address = 0;
            memset(&policy_redirect, 0, sizeof(policy_redirect));
            bool had_redirect = kind == 1 && (scenario & 16) != 0;
            if (had_redirect) {
                strcpy(policy_redirect.raddr, dest);
                policy_redirect.rport = 5000;
            }
            expect_destination(version, dest, 5000);
            handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
            struct ng_session *old = args->ctx->ng_session;
            int original_socket = old == NULL ? -1 : old->socket;
            assert(old != NULL && old->udp.uid < 0 && old->udp.state == UDP_ACTIVE);
            assert(old->udp.uid_retries == 0 && allocations == 1 && sockets == 1);
            assert(policy_checks == 1 && destination_calls == 1);
            assert(old->udp.uid_retry_at == (kind == 2 && scenario == 0 ? -1 : 11000));

            if (kind == 0) {
                // A burst before the deadline must not query the owner or policy.
                int closes = file_closes;
                int queries = uid_lookups;
                for (int i = 0; i < 64; i++)
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                assert(uid_lookups == queries && file_closes == closes && policy_checks == 1);
                const long long deadlines[] = {11000, 13000, 17000};
                for (int i = 0; i < UDP_UID_RETRY_MAX; i++) {
                    test_monotonic_ms = deadlines[i];
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                    assert(old->udp.uid_retries == i + 1 && old->udp.uid < 0);
                    assert(uid_lookups == queries + (legacy ? 0 : i + 1));
                    assert(file_closes == closes + (legacy ? (version == 4 ? 2 : 1) * (i + 1) : 0));
                }
                int clock_reads = test_monotonic_calls;
                queries = uid_lookups; closes = file_closes;
                test_monotonic_ms = 1000000;
                for (int i = 0; i < 64; i++)
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                assert(test_monotonic_calls == clock_reads && uid_lookups == queries);
                assert(file_closes == closes && policy_checks == UDP_UID_RETRY_MAX + 1 && allocations == 1 && sockets == 1);
                old->udp.uid_retries = 0; old->udp.uid_retry_at = 0;
                old->udp.uid = 0; // A known owner must never be queried or replaced.
                handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                assert(old->udp.uid == 0 && uid_lookups == queries && file_closes == closes);
                old->udp.uid = -1; old->udp.state = UDP_FINISHING;
                handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                assert(uid_lookups == queries && file_closes == closes);
            } else if (kind == 1) {
                jint owner = scenario & 1 ? 12345 : 0;
                bool deny = (scenario & 2) != 0;
                bool redirect = (scenario & 4) != 0;
                bool retire = deny || redirect || had_redirect;
                memset(&policy_redirect, 0, sizeof(policy_redirect));
                uid_lookup_result = owner;
                char table[256];
                snprintf(table, sizeof(table), "header\n0: %s:3039 %s:0000 07 0:0 0:0 0 %d 0 0\n",
                         version == 4 ? "0200000A" : "B80D0120000000000000000001000000",
                         version == 4 ? "00000000" : "00000000000000000000000000000000", owner);
                proc_contents = table;
                if (legacy && version == 4) proc6_contents = "header\n";
                deny_address = deny;
                int target_version = redirect && !(scenario & 32) ? (version == 4 ? 6 : 4) : version;
                const char *target = redirect ? (target_version == 4 ? "192.0.2.10" : "2001:db8::10") : dest;
                if (redirect) {
                    strcpy(policy_redirect.raddr, target);
                    policy_redirect.rport = 5354;
                }
                expect_destination(target_version, target, redirect ? 5354 : 5000);
                test_monotonic_ms = 11000;
                handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                struct ng_session *current = find_udp_session(&running, packet, packet + off);
                assert(current != NULL && current->udp.uid == owner);
                assert((current != old) == retire);
                assert(current->udp.state == (deny ? UDP_BLOCKED : UDP_ACTIVE));
                assert(old->udp.uid == owner && old->udp.state == (retire ? UDP_FINISHING : UDP_ACTIVE));
                assert(policy_checks == 2 && policy_uid == owner);
                assert(allocations == 1 + retire + (uid_cache != NULL));
                assert(destination_calls == !deny && sockets == (retire && !deny ? 2 : 1));
                assert(args->ctx->ng_session == current && current->next == (retire ? old : NULL));
                if (deny)
                    tun_packet(tunnel[0], output, version, version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
                else {
                    struct sockaddr_storage local;
                    socklen_t length = sizeof(local);
                    assert(getsockname(current->socket, (struct sockaddr *) &local, &length) == 0);
                    assert(local.ss_family == (target_version == 4 ? AF_INET : AF_INET6));
                    assert(current->udp.redirect_version == (redirect ? target_version : 0));
                }
                if (retire) {
                    // A queued event for the retired socket must not read or forward data.
                    struct epoll_event event = {.events = EPOLLIN | EPOLLERR, .data.ptr = old};
                    assert(epoll_ctl(epoll_fd, EPOLL_CTL_MOD, old->socket, &event) == -1 && errno == ENOENT);
                    check_udp_socket(&running, &event);
                    assert(old->udp.state == UDP_FINISHING && destination_calls == !deny);
                    check_udp_session(&running, old, 1, SESSION_MAX);
                    assert(old->socket == -1 && old->udp.state == UDP_CLOSED);
                }
                if (!deny) {
                    int queries = uid_lookups, closes = file_closes;
                    int current_socket = current->socket;
                    uid_lookup_result = 23456;
                    // The session must own its target, not retain this mutable policy result.
                    memset(&policy_redirect, 0, sizeof(policy_redirect));
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                    assert(current->socket == current_socket && current->udp.uid == owner);
                    if (!retire) assert(current->socket == original_socket);
                    assert(destination_calls == 3 && policy_checks == 2);
                    assert(uid_lookups == queries && file_closes == closes);
                }
                assert(find_udp_session(&running, packet, packet + off) == current);
            } else {
                test_monotonic_ms = 11000;
                if (scenario == 1) test_monotonic_failures = 1;
                int queries = uid_lookups;
                handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                int clock_reads = test_monotonic_calls;
                test_monotonic_ms = 1000000;
                for (int i = 0; i < 64; i++)
                    handle_ip(&running, packet, off + 9, epoll_fd, SESSION_MAX, SESSION_MAX);
                assert(uid_lookups == queries && policy_checks == 1 && old->udp.uid < 0);
                assert(test_monotonic_calls == clock_reads);
            }
            clear(args->ctx);
            release_uid_cache();
            assert(allocations == 0 && sockets == 0 && !is_readable(tunnel[0]));
        }
    }
    test_monotonic_enabled = false;
    real_network = 0;
    args->ctx->sdk = 29;
    close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_uid_ipv4_fallback(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint32_t address;
    assert(inet_pton(AF_INET, "127.0.0.1", &address) == 1);
    const int protocols[] = {IPPROTO_TCP, IPPROTO_UDP};
    for (size_t i = 0; i < sizeof(protocols) / sizeof(*protocols); i++) {
        for (int kind = 0; kind < 7; kind++) {
            fake_proc = 1;
            proc_contents = kind == 6 ? "header\ninvalid entry\n" : proc_one;
            proc6_open_error = kind == 0 || kind == 6 ? ENOENT : kind == 1 ? EACCES : 0;
            proc6_contents = kind == 2 ? "header\ninvalid entry\n" : kind == 3 ? "header\n" :
                kind == 5 ? "header\n0: 0000000000000000FFFF00000100007F:04D2 00000000000000000000000000000000:0000 07 0:0 0:0 0 0 0 0\n" :
                            "header\n0: 0000000000000000FFFF00000100007F:04D2 00000000000000000000000000000000:0000 07 0:0 0:0 0 23456 0 0\n";
            int before = file_closes;
            jint expected = kind == 6 ? -2 : kind == 5 ? 0 : kind == 4 ? 23456 : 10001;
            assert(get_uid(4, protocols[i], &address, 1234, &address, 53) == expected);
            // Successful mapped lookups, including UID zero, must not read IPv4.
            int closes = kind == 2 || kind == 3 ? 2 : 1;
            assert(file_closes == before + closes);
            release_uid_cache();
        }
    }
#endif
}

void test_uid_proc_parser(int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    if (kind == 1) {
        const char *rows[] = {
            "0: 010007F:04D2 00000000:0000 07 0:0 0:0 0 10001 0 0\n",
            "0: 0100007G:04D2 00000000:0000 07 0:0 0:0 0 10001 0 0\n",
            "0: 0100007F:10000 00000000:0000 07 0:0 0:0 0 10001 0 0\n",
            "0: 0100007F:04D2 00000000:0000 07 0:0 0:0\n",
            "0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 2147483648 0 0\n",
            "0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0 10001x 0 0\n",
            "0: 0100007F:04D2 00000000:0000 07 0:0 0:0 0\n"
        };
        for (size_t i = 0; i < sizeof(rows) / sizeof(*rows); i++) {
            char table[256];
            snprintf(table, sizeof(table), "header\n%s", rows[i]);
            fake_proc = 1; proc_contents = table;
            int before = file_closes;
            assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == -2);
            assert(file_closes == before + 1 && uid_cache_size == 0);
            release_uid_cache();
        }
        const char *ipv6_rows[] = {
            "0: 0000000000000000000000000100000:04D2 00000000000000000000000000000000:0000 07 0:0 0:0 0 10001\n",
            "0: 000000000000000000000000010000000:04D2 00000000000000000000000000000000:0000 07 0:0 0:0 0 10001\n",
            "0: 0000000000000000000000000100000G:04D2 00000000000000000000000000000000:0000 07 0:0 0:0 0 10001\n",
            "0: 00000000000000000000000001000000:04D2 0000000000000000000000000000000:0000 07 0:0 0:0 0 10001\n"
        };
        for (size_t i = 0; i < sizeof(ipv6_rows) / sizeof(*ipv6_rows); i++) {
            char table[256];
            snprintf(table, sizeof(table), "header\n%s", ipv6_rows[i]);
            fake_proc = 1; proc_contents = table;
            int before = file_closes;
            assert(lookup_uid(6, IPPROTO_UDP, 1234, 0) == -2);
            assert(file_closes == before + 1 && uid_cache_size == 0);
            release_uid_cache();
        }
    } else if (kind == 2) {
        // Preserve the historical last match on a proc scan and first match on
        // a cache hit, including wildcard endpoints and duplicate exact rows.
        fake_proc = 1;
        proc_contents = "header\n"
            "0: 00000000:04D2 00000000:0000 07 0:0 0:0 0 10001 0 0\n"
            "1: 0100007F:04D2 0100007F:0035 07 0:0 0:0 0 10002 0 0\n"
            "2: 0100007F:04D2 0100007F:0035 07 0:0 0:0 0 10003 0 0\n";
        assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10003);
        assert(uid_cache_size == 2);
        int before = file_closes;
        assert(lookup_uid(4, IPPROTO_UDP, 1234, 0) == 10001);
        assert(file_closes == before && uid_cache[1].uid == 10003);
        release_uid_cache();
    } else {
        for (int version = 4; version <= 6; version += 2) {
            uint8_t source[16], dest[16];
            const char *s = version == 4 ? "192.0.2.171" : "2001:db8:abcd:1234:5678:9abc:def0:1234";
            const char *d = version == 4 ? "198.51.100.205" : "2001:db8:fedc:ba98:7654:3210:abcd:ef01";
            assert(inet_pton(version == 4 ? AF_INET : AF_INET6, s, source) == 1);
            assert(inet_pton(version == 4 ? AF_INET : AF_INET6, d, dest) == 1);
            fake_proc = 1;
            proc_contents = version == 4
                ? "header\n 42: aB0200C0:beEf cD6433C6:01bB 01 00000001:00000002 00:00000000 00000000 12345 0 0\n"
                : "header\n 42: B80D01203412CDABBC9A78563412F0DE:beEF B80D012098BADCFE1032547601EFCDAB:01bB 01 1:2 0:0 0 12345 0 0\n";
            assert(get_uid_sub(version, IPPROTO_TCP, source, 48879, dest, 443, s, d, 0) == 12345);
            int before = file_closes;
            assert(get_uid_sub(version, IPPROTO_TCP, source, 48879, dest, 443, s, d, 0) == 12345);
            assert(file_closes == before && uid_cache_size == 1);
            release_uid_cache();
        }
    }
#endif
}

void test_uid_proc_recovery(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        const char *local = version == 4 ? "0100007F" : "00000000000000000000000001000000";
        const char *zero = version == 4 ? "00000000" : "00000000000000000000000000000000";
        const int protocols[] = {IPPROTO_TCP, IPPROTO_UDP};
        for (size_t protocol = 0; protocol < sizeof(protocols) / sizeof(*protocols); protocol++) {
            for (int mode = 0; mode < 3; mode++) {
                for (int scenario = 0; scenario < 9; scenario++) {
                    char row[200], second[200], table[1400];
                    jint owner = scenario & 1 ? 0 : 12345;
                    snprintf(row, sizeof(row), "0: %s:04D2 %s:0000 07 0:0 0:0 0 %d 0 0\n", local, zero, owner);
                    snprintf(second, sizeof(second), "1: %s:04D2 %s:0000 07 0:0 0:0 0 23456 0 0\n", local, zero);
                    strcpy(table, "header\n");
                    if (scenario == 0 || scenario == 2) strcat(table, "malformed\n");
                    if (scenario <= 3) {
                        strcat(table, row);
                        if (scenario != 0) strcat(table, "malformed\n");
                        if (scenario == 3) strcat(table, second); // Last valid match still wins.
                    } else if (scenario == 4 || scenario == 5 || scenario == 8) {
                        // A valid-looking continuation belongs to the same overlong row.
                        size_t start = scenario == 8 ? 0 : strlen(table);
                        memset(table + start, '!', 249);
                        strcpy(table + start + 249, second);
                        if (scenario != 5) strcat(table, row);
                    } else if (scenario == 6) {
                        strcat(table, row);
                        table[strlen(table) - 1] = 0; // Complete final row without a newline.
                    } else {
                        // The first chunk ends inside the UID: never cache its partial value.
                        size_t start = strlen(table);
                        int prefix = snprintf(table + start, sizeof(table) - start,
                                "0: %s:04D2 %s:0000 07 0:0 0:0 0 ", local, zero);
                        assert(prefix > 0 && prefix < 248);
                        memset(table + start + prefix, ' ', 248 - prefix);
                        strcpy(table + start + 248, "12345 0 0\n");
                    }
                    fake_proc = 1; proc_contents = table;
                    fail_cache_alloc = mode == 2;
                    int before = file_closes;
                    int64_t now = mode == 1 ? -1 : 0;
                    jint expected = scenario == 5 || scenario == 7 ? -2 : scenario == 3 ? 23456 : owner;
                    assert(lookup_uid(version, protocols[protocol], 1234, now) == expected);
                    assert(file_closes == before + 1);
                    bool cached = mode == 0 && expected >= 0;
                    assert(uid_cache_size == cached && allocations == cached);
                    if (cached) {
                        assert(uid_cache[0].uid == expected);
                        proc_contents = "header\nmalformed\n";
                        assert(lookup_uid(version, protocols[protocol], 1234, now) == expected);
                        assert(file_closes == before + 1);
                    }
                    release_uid_cache();
                    assert(sockets == 0);
                }
            }
        }
    }
#endif
}

void test_restore_uid_icmp(const struct arguments *args, int version) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint8_t source[16] = {1}, dest[16] = {2};
    int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
    struct icmp echo = {.icmp_type = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                         .icmp_id = htons(1234)};
    struct ng_session older = {.protocol = (uint8_t) protocol, .socket = 42};
    older.icmp.version = version;
    older.icmp.uid = 12345;
    older.icmp.id = echo.icmp_id;
    memcpy(&older.icmp.saddr, source, version == 4 ? 4 : 16);
    memcpy(&older.icmp.daddr, dest, version == 4 ? 4 : 16);
    struct ng_session newest = older;
    newest.icmp.uid = -1;
    newest.next = &older;
    set_test_sessions(args->ctx, &newest);
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    newest.icmp.uid = 23456;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == 23456);
    newest.socket = -1;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    newest.socket = 42;
    newest.icmp.stop = 1;
    older.icmp.stop = 1;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    newest.icmp.stop = 0;
    echo.icmp_id = htons(1235);
    assert(restore_uid(args, version, protocol, source, 1235, dest, 1235,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    echo.icmp_id = htons(1234);
    dest[version == 4 ? 3 : 15] ^= 1;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    dest[version == 4 ? 3 : 15] ^= 1;
    echo.icmp_type = ICMP_UNREACH;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    set_test_sessions(args->ctx, NULL);
#endif
}
