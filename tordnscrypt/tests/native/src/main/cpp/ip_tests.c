#include "test_support.h"

void test_proxy_mtu(const struct arguments *args) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    extern char proxy_socks5_addr[], tor_socks5_addr[];
    extern int proxy_socks5_port, tor_socks5_port;
    extern jint Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1get_1mtu(
            JNIEnv *, jobject);
    strcpy(tor_socks5_addr, "127.0.0.1"); tor_socks5_port = 9050;
    for (int mode = 0; mode < 5; mode++) {
        strcpy(proxy_socks5_addr, mode == 2 ? "" : "127.0.0.1");
        proxy_socks5_port = mode == 1 || mode == 2 || mode == 3 ? 1080 : 0;
        unsigned mtu = 1500;
        assert(get_mtu() == mtu); // Neither proxy changes the interface MTU.
        assert(get_default_mss(4) == mtu - 40 && get_default_mss(6) == mtu - 60);
        assert(Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1get_1mtu(NULL, NULL) == mtu);
    }
    strcpy(proxy_socks5_addr, "127.0.0.1"); proxy_socks5_port = 1080;
    int tunnel[2]; assert(pipe(tunnel) == 0);
    struct packet_buffers buffers = {0};
    struct arguments running = *args; running.tun = tunnel[0]; running.buffers = &buffers;
    struct epoll_event event = {.events = EPOLLIN};
    real_network = 1; capture_packets = 1;
    for (int version = 4; version <= 6; version += 2) {
        _Alignas(8) uint8_t packet[1500] = {0};
        size_t off = version == 4 ? 20 : 40;
        ip_header(packet, version, IPPROTO_UDP, sizeof(packet) - off,
                  version == 4 ? "203.0.113.2" : "2001:db8::2");
        struct udphdr *udp = (void *) (packet + off);
        udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(sizeof(packet) - off);
        packet_checksums(packet, sizeof(packet), off, IPPROTO_UDP);
        int before = udp_calls;
        assert(write(tunnel[1], packet, sizeof(packet)) == sizeof(packet));
        assert(check_tun(&running, &event, -1, 0, SESSION_MAX) == 0);
        assert(udp_calls == before + 1 && buffers.buffer[PACKET_INPUT].capacity >= sizeof(packet));
        assert(memcmp(buffers.buffer[PACKET_INPUT].data, packet, sizeof(packet)) == 0);
    }
    capture_packets = 0;
    clear_packet_buffers(&buffers); close(tunnel[0]); close(tunnel[1]);
    *proxy_socks5_addr = *tor_socks5_addr = 0; proxy_socks5_port = tor_socks5_port = 0;
    assert(get_mtu() == 1500 && allocations == 0 && sockets == 0);
#endif
}

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
int uid_clock_reads;

void set_uid_test_clock_failure(bool failure) {
    uid_clock_failure = failure;
}

void set_uid_test_clock(int64_t now) {
    uid_clock_enabled = now >= 0;
    uid_test_clock = now;
    uid_clock_failure = false;
}

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
        uid_clock_reads++;
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
                // Independent scenarios must not share UID evidence or ICMP error budgets.
                clear(args->ctx);
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

static void test_uid_dns_loss_limits(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int version = 4; version <= 6; version += 2) {
        clear(args->ctx); set_uid_test_clock(10000);
        uint8_t source[16] = {0};
        struct uid_dns_answer maps[2] = {{.version = version, .ttl = 30}, {.version = version, .ttl = 30}};
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                         version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
        for (int i = 0; i < 2; i++)
            assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                             version == 4 ? (i ? "192.0.2.11" : "192.0.2.10")
                                          : (i ? "2001:db8::11" : "2001:db8::10"), maps[i].address) == 1);
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        // A lost alias for another hostname must still protect a shared target IP.
        discard_uid_dns_answer(args->ctx, "missing", &maps[1]);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        clear(args->ctx);
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        struct uid_dns_answer safe[2] = {maps[0], maps[1]};
        safe[0].address[0] = safe[1].address[0] = 0x30;
        record_uid_dns_answers(args->ctx, "safe", safe, 2);
        for (unsigned int i = 0; i < UID_DNS_LOSSES; i++) {
            char domain[32]; snprintf(domain, sizeof(domain), "lost-%u", i);
            struct uid_dns_answer loss = {.ttl = 10, .version = 4, .address = {198, 51, 100, (uint8_t) i}};
            discard_uid_dns_answer(args->ctx, domain, &loss);
        }
        assert(args->ctx->uid_dns_mappings.lost_count == UID_DNS_LOSSES);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, safe[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == 12345);
        set_uid_test_clock(10500);
        struct uid_dns_answer lost = maps[1]; lost.address[15] = lost.address[3] = 99; lost.ttl = 10;
        discard_uid_dns_answer(args->ctx, "test", &lost);
        assert(args->ctx->uid_dns_mappings.admit_after == 910500);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, safe[1].address, 443) == 12345);
        // With no room for loss bookkeeping, decline new keys even if mapping slots are free.
        struct uid_dns_answer fresh[2] = {maps[0], maps[1]};
        fresh[0].address[0] = fresh[1].address[0] = 0x40;
        record_uid_dns_answers(args->ctx, "new", fresh, 2);
        assert(args->ctx->uid_dns_mappings.count == 4 && args->ctx->uid_dns_mappings.lost_count == UID_DNS_LOSSES);
        record_uid_dns_answers(args->ctx, "test", maps, 2); // Refresh cannot clear the resident guard.
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, safe[1].address, 443) == 12345);
        set_uid_test_clock(910499);
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        set_uid_test_clock(910500);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[1].address, 443) == 12345);
        // Untracked historical evidence keeps admission closed for fifteen minutes.
        assert(args->ctx->uid_dns_mappings.admit_after == 910500);
        record_uid_dns_answers(args->ctx, "new", fresh, 2);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, fresh[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, fresh[1].address, 443) == 12345);
        // If both arrays are full, preserve resident mappings and live guards.
        clear(args->ctx); set_uid_test_clock(50000);
        for (unsigned int i = 2; i < UID_DNS_MAPPINGS; i++) {
            char domain[32]; snprintf(domain, sizeof(domain), "fill-%u", i);
            struct uid_dns_answer fill = {.version = 4, .ttl = 1, .address = {203, 0, 113, (uint8_t) i}};
            record_uid_dns_answers(args->ctx, domain, &fill, 1);
        }
        // Freshness no longer determines eviction order. Keep the unrelated
        // mappings newer than the intentionally selected overflow victims.
        set_uid_test_clock(50001);
        record_uid_dns_answers(args->ctx, "safe", safe, 2);
        for (unsigned int i = 0; i < UID_DNS_LOSSES; i++) {
            char domain[32]; snprintf(domain, sizeof(domain), "lost-%u", i);
            struct uid_dns_answer loss = {.ttl = 10, .version = 4, .address = {198, 51, 100, (uint8_t) i}};
            discard_uid_dns_answer(args->ctx, domain, &loss);
        }
        record_uid_dns_answers(args->ctx, "new", fresh, 2);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, safe[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, safe[1].address, 443) == 12345);
        assert(args->ctx->uid_dns_mappings.count == UID_DNS_MAPPINGS &&
               args->ctx->uid_dns_mappings.lost_count == UID_DNS_LOSSES);
        for (size_t i = 0; i < args->ctx->uid_dns_mappings.count; i++)
            assert(strcmp(args->ctx->uid_dns_mappings.entry[i].domain, "new") != 0);
        // Long-lived mappings fill the enlarged cache without premature eviction.
        clear(args->ctx); set_uid_test_clock(10000);
        maps[0].ttl = maps[1].ttl = UINT32_MAX;
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        for (unsigned int i = 2; i < 512; i++) {
            char domain[32]; snprintf(domain, sizeof(domain), "long-%u", i);
            struct uid_dns_answer fill = {.version = 4, .ttl = 600, .address = {203, 0, 113, (uint8_t) i}};
            fill.address[2] = (uint8_t) (i >> 8);
            record_uid_dns_answers(args->ctx, domain, &fill, 1);
            if (i == 256) assert(args->ctx->uid_dns_mappings.lost_count == 0);
        }
        assert(args->ctx->uid_dns_mappings.count == 512 && args->ctx->uid_dns_mappings.lost_count == 0);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, maps[1].address, 443) == 12345);
        set_uid_test_clock(10500);
        record_uid_dns_answers(args->ctx, "safe", safe, 1);
        assert(args->ctx->uid_dns_mappings.lost_count == 1);
        assert(args->ctx->uid_dns_mappings.lost[0].inference_until == 910000);
        set_uid_test_clock(11000);
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        // Refreshing the mappings must not extend or erase the original guard.
        set_uid_test_clock(909999);
        record_uid_observation(args->ctx, version, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, maps[1].address, 443) == -1);
        set_uid_test_clock(910000);
        assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, maps[1].address, 443) == 12345);
        clear(args->ctx); set_uid_test_clock(10000);
        discard_uid_dns_answer(args->ctx, "oversized", &maps[0]);
        assert(args->ctx->uid_dns_mappings.lost[0].inference_until == 910000);
        clear(args->ctx);
        assert(args->ctx->uid_dns_mappings.lost_count == 0 && args->ctx->uid_dns_mappings.admit_after == 0);
        assert(allocations == 0 && sockets == 0 && string_refs == 0);
    }
    set_uid_test_clock(-1);
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static void test_uid_capacity_guards(struct arguments *args) {
    struct context *ctx = args->ctx;
    for (int version = 4; version <= 6; version += 2) {
        clear(ctx); set_uid_test_clock(10000);
        uint8_t source[16] = {1}, dest[16] = {0};
        for (uint32_t i = 0; i < UID_OBSERVATIONS + UID_OBSERVATION_LOSSES + 2; i++) {
            memcpy(dest, &i, sizeof(i));
            record_uid_observation(ctx, version, IPPROTO_TCP, source, dest, 443, 12345);
        }
        assert(ctx->uid_observations.count == UID_OBSERVATIONS);
        assert(ctx->uid_observations.lost_count == UID_OBSERVATION_LOSSES);
        assert(ctx->uid_observations.incomplete_until == 15000);
        set_uid_test_clock(11000);
        uint32_t next = 999; memcpy(dest, &next, sizeof(next));
        record_uid_observation(ctx, version, IPPROTO_UDP, source, dest, 443, 12345);
        assert(infer_uid_destination(ctx, version, IPPROTO_TCP, source, dest, 443) == -1);

        clear(ctx); set_uid_test_clock(20000);
        struct uid_dns_answer answer = {.version = version, .ttl = 30};
        for (uint32_t i = 0; i < UID_DNS_MAPPINGS + UID_DNS_LOSSES + 2; i++) {
            memcpy(answer.address, &i, sizeof(i));
            record_uid_dns_answers(ctx, "capacity.example", &answer, 1);
        }
        assert(ctx->uid_dns_mappings.count == UID_DNS_MAPPINGS);
        assert(ctx->uid_dns_mappings.lost_count == UID_DNS_LOSSES);
        assert(ctx->uid_dns_mappings.admit_after == 920000);
        set_uid_test_clock(21000);
        next = 999; memcpy(answer.address, &next, sizeof(next));
        record_uid_dns_answers(ctx, "capacity.example", &answer, 1);
        assert(ctx->uid_dns_mappings.admit_after == 921000);
        // A second loss in the same batch cannot extend these resident guards.
        // Only the full loss array needs key comparisons; skip resident keys.
        ctx->uid_dns_mappings.entry[0].incomplete_until = 930000;
        memory_comparisons = 0; count_memory_comparisons = true;
        discard_uid_dns_answer(ctx, "unrelated.example", &answer);
        count_memory_comparisons = false;
        assert(memory_comparisons == UID_DNS_LOSSES);
        assert(ctx->uid_dns_mappings.admit_after == 921000);
        for (size_t i = 0; i < ctx->uid_dns_mappings.count; i++) {
            assert(ctx->uid_dns_mappings.entry[i].incomplete_until == (i == 0 ? 930000 : 921000));
            assert(ctx->uid_dns_mappings.entry[i].inference_until == 920000);
        }
        set_uid_test_clock(22000);
        discard_uid_dns_answer(ctx, "discarded.example", &answer);
        discard_uid_dns_answer(ctx, "capacity.example", &answer);
        for (size_t i = 0; i < ctx->uid_dns_mappings.count; i++)
            assert(ctx->uid_dns_mappings.entry[i].incomplete_until == (i == 0 ? 930000 : 922000));
        clear_sessions(ctx);
        assert(ctx->uid_dns_mappings.admit_after == 922000);
        assert(ctx->uid_dns_mappings.lost_count == UID_DNS_LOSSES);
        set_uid_test_clock(1900000); // Expired slots are reusable without discarding live guards.
        next++; memcpy(answer.address, &next, sizeof(next));
        record_uid_dns_answers(ctx, "fresh.example", &answer, 1);
        assert(ctx->uid_dns_mappings.entry[0].inference_until == 2800000);
        assert(strcmp(ctx->uid_dns_mappings.entry[0].domain, "fresh.example") == 0);
        discard_uid_dns_answer(ctx, "fresh.example", &answer);
        assert(ctx->uid_dns_mappings.lost[0].inference_until == 2800000);
        assert(strcmp(ctx->uid_dns_mappings.lost[0].domain, "fresh.example") == 0);
        assert(uid_probe_logs == 0);
        clear(ctx);
    }
}
#endif

void test_uid_activity_batch(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP) {
            clear(args->ctx); set_uid_test_clock(10000);
            struct ng_session *s = ng_calloc(1, sizeof(*s), "batch session");
            assert(s != NULL);
            s->protocol = protocol;
            s->socket = socket(AF_INET, SOCK_DGRAM, 0); assert(s->socket >= 0);
            uint8_t source[16] = {0}, dest[16] = {0};
            assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                             version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
            assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                             version == 4 ? "203.0.113.2" : "2001:db8::2", dest) == 1);
            if (protocol == IPPROTO_TCP) {
                s->tcp.version = version; s->tcp.uid = 12345; s->tcp.state = TCP_ESTABLISHED;
                s->tcp.dest = htons(443);
                memcpy(&s->tcp.saddr, source, sizeof(source)); memcpy(&s->tcp.daddr, dest, sizeof(dest));
            } else {
                s->udp.version = version; s->udp.uid = 12345; s->udp.state = UDP_ACTIVE;
                s->udp.dest = htons(443);
                memcpy(&s->udp.saddr, source, sizeof(source)); memcpy(&s->udp.daddr, dest, sizeof(dest));
            }
            add_session(args->ctx, s);
            int64_t batch_time = -1;
            args->ctx->uid_activity_batch_time = &batch_time;
            uid_clock_reads = 0;
            for (int i = 0; i < TUN_YIELD; i++) refresh_uid_activity(args->ctx, s);
            assert(uid_clock_reads == 1 && s->uid_activity_at == 10000);
            assert(args->ctx->uid_observations.entry[s->uid_observation_slot].expires == 15000);

            // Even a suspended thread or slow callback cannot extend stale evidence.
            set_uid_test_clock(20000);
            refresh_uid_activity(args->ctx, s);
            assert(uid_clock_reads == 1 && s->uid_activity_at == 10000);
            assert(infer_uid_destination(args->ctx, version, protocol, source, dest, 443) == -1);
            assert(uid_clock_reads == 2);
            // An actual new owner write must also read the current clock inside a batch.
            record_uid_observation(args->ctx, version, protocol, source, dest, 443, 23456);
            assert(uid_clock_reads == 3 && args->ctx->uid_observations.entry[0].expires == 25000);

            // Each new batch, and standalone calls, must sample again.
            args->ctx->uid_activity_batch_time = NULL;
            batch_time = -1;
            args->ctx->uid_activity_batch_time = &batch_time;
            refresh_uid_activity(args->ctx, s);
            assert(uid_clock_reads == 4 && s->uid_activity_at == 20000);
            assert(infer_uid_destination(args->ctx, version, protocol, source, dest, 443) == -1);
            args->ctx->uid_activity_batch_time = NULL;
            set_uid_test_clock(21000);
            refresh_uid_activity(args->ctx, s);
            assert(s->uid_activity_at == 21000);
            // First-use clock failures and backwards time still invalidate evidence.
            for (int failure = 0; failure < 2; failure++) {
                batch_time = -1; args->ctx->uid_activity_batch_time = &batch_time;
                if (failure == 0) set_uid_test_clock_failure(true);
                else set_uid_test_clock(1);
                refresh_uid_activity(args->ctx, s);
                assert(s->uid_activity_at == -1 && s->uid_observation_slot == UINT16_MAX);
                assert(args->ctx->uid_observations.count == 0);
                args->ctx->uid_activity_batch_time = NULL;
                set_uid_test_clock(22000);
                refresh_uid_activity(args->ctx, s);
                assert(s->uid_activity_at == 22000);
            }
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0 && string_refs == 0);
        }
    set_uid_test_clock(-1);
#endif
}

void test_uid_owner_slot_hint(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
            for (int mode = 0; mode < 15; mode++) {
                clear(args->ctx); set_uid_test_clock(10000);
                struct ng_session *s = ng_calloc(1, sizeof(*s), "hint session");
                assert(s != NULL);
                s->protocol = protocol;
                s->socket = socket(AF_INET, SOCK_DGRAM, 0); assert(s->socket >= 0);
                uint8_t source[16] = {0}, dest[16] = {0}, other[16] = {198};
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                 version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                 version == 4 ? "203.0.113.2" : "2001:db8::2", dest) == 1);
                if (protocol == IPPROTO_TCP) {
                    s->tcp.version = version; s->tcp.uid = 12345;
                    s->tcp.state = TCP_ESTABLISHED; s->tcp.dest = htons(443);
                    memcpy(&s->tcp.saddr, source, sizeof(source));
                    memcpy(&s->tcp.daddr, dest, sizeof(dest));
                } else {
                    s->udp.version = version; s->udp.uid = 12345;
                    s->udp.state = UDP_ACTIVE; s->udp.dest = htons(443);
                    memcpy(&s->udp.saddr, source, sizeof(source));
                    memcpy(&s->udp.daddr, dest, sizeof(dest));
                }
                s->uid_observation_slot = 123; // Reused sessions must not inherit a hint.
                add_session(args->ctx, s);
                assert(s->uid_observation_slot == UINT16_MAX);
                for (unsigned int i = 0; i < UID_OBSERVATIONS - 1; i++) {
                    other[version == 4 ? 3 : 15] = (uint8_t) i;
                    record_uid_observation(args->ctx, version, protocol, source, other, 443, 12345);
                }
                refresh_uid_activity(args->ctx, s);
                size_t slot = args->ctx->uid_observations.last;
                assert(slot == UID_OBSERVATIONS - 1);
                assert(args->ctx->uid_observations.entry[slot].expires == 15000);
                set_uid_test_clock(10999);
                memory_comparisons = 0; count_memory_comparisons = true;
                refresh_uid_activity(args->ctx, s);
                count_memory_comparisons = false;
                assert(memory_comparisons == 0); // Throttled packets do not validate the hint.
                set_uid_test_clock(11000);
                other[version == 4 ? 3 : 15] = 0;
                record_uid_observation(args->ctx, version, protocol, source, other, 443, 12345);
                struct uid_observation *entry = &args->ctx->uid_observations.entry[slot];
                if (mode == 1) entry->uid = 23456;
                if (mode == 2) entry->protocol = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
                if (mode == 3) entry->version = 10 - version;
                if (mode == 4) entry->source[0] ^= 1;
                if (mode == 5) entry->dest[0] ^= 1;
                if (mode == 6) entry->port = 444;
                if (mode == 7) s->uid_observation_slot = UINT16_MAX;
                if (mode == 8) s->uid_observation_slot = UID_OBSERVATIONS;
                if (mode == 9) entry->expires = 11000;
                if (mode == 10) {
                    // A reset followed by unrelated insertions may reuse the same index.
                    memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
                    for (unsigned int i = 0; i < UID_OBSERVATIONS; i++) {
                        other[version == 4 ? 3 : 15] = (uint8_t) i;
                        record_uid_observation(args->ctx, version, protocol, source, other, 443, 23456);
                    }
                }
                if (mode == 11) memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
                if (mode == 12) set_uid_test_clock_failure(true);
                if (mode == 13) set_uid_test_clock(9000);
                if (mode == 14) set_uid_test_clock(16000); // Logical reset after all owners expired.
                struct uid_observation previous = *entry;
                memory_comparisons = 0; count_memory_comparisons = true;
                refresh_uid_activity(args->ctx, s);
                count_memory_comparisons = false;
                if (mode == 0) assert(memory_comparisons == 2); // Constant work despite a full, interleaved cache.
                if (mode == 12 || mode == 13) {
                    assert(s->uid_observation_slot == UINT16_MAX && s->uid_activity_at == -1);
                    assert(args->ctx->uid_observations.count == 0);
                    set_uid_test_clock(11000);
                    refresh_uid_activity(args->ctx, s);
                }
                assert(s->uid_observation_slot < args->ctx->uid_observations.count);
                struct uid_observation *refreshed = &args->ctx->uid_observations.entry[s->uid_observation_slot];
                assert(refreshed->uid == 12345 && refreshed->protocol == protocol && refreshed->version == version);
                assert(refreshed->port == 443 && memcmp(refreshed->source, source, version == 4 ? 4 : 16) == 0);
                assert(memcmp(refreshed->dest, dest, version == 4 ? 4 : 16) == 0);
                assert(refreshed->expires == (mode == 14 ? 21000 : 16000));
                assert(args->ctx->uid_observations.last == s->uid_observation_slot);
                if ((mode >= 1 && mode <= 6) || mode == 10) {
                    assert(memcmp(entry, &previous, sizeof(previous)) == 0); // Never refresh a replacement owner/key.
                }
                if (mode == 0) {
                    record_uid_observation(args->ctx, version, protocol, source, dest, 443, 23456);
                    set_uid_test_clock(12000);
                    size_t guards = args->ctx->uid_observations.lost_count;
                    refresh_uid_activity(args->ctx, s);
                    assert(args->ctx->uid_observations.lost_count == guards);
                    assert(infer_uid_destination(args->ctx, version, protocol, source, dest, 443) == -1);
                }
                clear(args->ctx);
                assert(allocations == 0 && sockets == 0 && string_refs == 0);
            }
    set_uid_test_clock(-1);
#endif
}

void test_uid_dns_slot_hint(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    struct uid_dns_mappings *before = ng_malloc(sizeof(*before), "DNS hint initial state");
    struct uid_dns_mappings *expected = ng_malloc(sizeof(*expected), "DNS hint expected state");
    assert(before != NULL && expected != NULL);
    for (int version = 4; version <= 6; version += 2)
        for (int unique = 0; unique <= 1; unique++)
            for (int mode = 0; mode < 13; mode++) {
                void (*record)(struct context *, const char *, const struct uid_dns_answer *, size_t) =
                        unique ? record_uid_dns_unique_answers : record_uid_dns_answers;
                clear(args->ctx); set_uid_test_clock(10000);
                struct uid_dns_mappings *cache = &args->ctx->uid_dns_mappings;
                for (uint32_t i = 0; i < UID_DNS_MAPPINGS; i++) {
                    struct uid_dns_answer answer = {.version = version, .ttl = 30};
                    memcpy(answer.address, &i, sizeof(i));
                    record(args->ctx, "hint.example", &answer, 1);
                }
                enum { BATCH = 8 };
                const size_t first = UID_DNS_MAPPINGS - BATCH;
                struct uid_dns_answer answers[BATCH] = {0};
                for (size_t i = 0; i < BATCH; i++) {
                    answers[i].version = version; answers[i].ttl = 30;
                    memcpy(answers[i].address, cache->entry[first + i].address, sizeof(answers[i].address));
                }
                cache->last = first - 1; // The successor probe should find the first answer.
                if (mode == 1) cache->last = first;
                if (mode == 2) cache->last = UID_DNS_MAPPINGS - 1; // Wraparound miss.
                if (mode == 3) cache->last = SIZE_MAX;
                if (mode == 4) cache->last = UID_DNS_MAPPINGS;
                if (mode >= 5 && mode <= 8) cache->last = first;
                if (mode == 5) strcpy(cache->entry[first].domain, "other.example");
                if (mode == 6) cache->entry[first].version = 10 - version;
                if (mode == 7) cache->entry[first].address[version == 4 ? 3 : 15] = 0xff;
                if (mode == 8) {
                    cache->entry[0].inference_until = 11000; // Earlier free slot must not hide an exact match.
                    cache->entry[first].inference_until = 11000;
                    cache->entry[first].incomplete_until = 1100000;
                }
                if (mode == 9) answers[3].ttl = 0;
                if (mode == 10) {
                    cache->entry[first + 1].incomplete_until = 1100000;
                    cache->admit_after = 1200000;
                }
                if (mode == 11) answers[0].address[version == 4 ? 3 : 15] = 0xfe;
                if (mode == 12) memset(cache, 0, sizeof(*cache));
                *before = *cache;
                set_uid_test_clock(11000);
                memory_comparisons = 0; count_memory_comparisons = true;
                record(args->ctx, "hint.example", answers, BATCH);
                count_memory_comparisons = false;
                if (mode < 2) assert(memory_comparisons <= 2 * BATCH);
                *expected = *cache;
                *cache = *before;
                // Force the original full scan for every distinct input key.
                // A fixed clock makes separate updates equivalent to this batch.
                for (size_t i = 0; i < BATCH; i++) {
                    cache->last = SIZE_MAX;
                    record(args->ctx, "hint.example", answers + i, 1);
                }
                assert(memcmp(cache, expected, sizeof(*cache)) == 0);
                assert(cache->count <= UID_DNS_MAPPINGS && cache->lost_count <= UID_DNS_LOSSES);
            }
    clear(args->ctx); set_uid_test_clock(-1);
    ng_free(before, __FILE__, __LINE__); ng_free(expected, __FILE__, __LINE__);
    assert(allocations == 0 && sockets == 0 && string_refs == 0);
#endif
}

void test_uid_dns_unique_updates(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    struct uid_dns_mappings *saved = ng_malloc(sizeof(*saved), "DNS comparison");
    assert(saved != NULL);
    for (int version = 4; version <= 6; version += 2)
        for (size_t count = 2; count <= UID_DNS_ANSWERS; count *= 2) {
            struct uid_dns_answer answers[UID_DNS_ANSWERS] = {0};
            for (size_t i = 0; i < count; i++) {
                answers[i].version = version;
                answers[i].address[0] = (uint8_t) i;
                answers[i].ttl = (uint32_t) (5 + i);
            }
            for (int mode = 0; mode < 4; mode++) {
                clear(args->ctx); set_uid_test_clock(10000);
                if (mode == 1 || mode == 3) {
                    // Exercise eviction and exhausted guards as well as an empty cache.
                    for (uint32_t i = 0; i < UID_DNS_MAPPINGS + (mode == 3 ? UID_DNS_LOSSES : 0); i++) {
                        struct uid_dns_answer fill = {.version = version, .ttl = 1};
                        memcpy(fill.address, &i, sizeof(i));
                        record_uid_dns_answers(args->ctx, "fill.test", &fill, 1);
                    }
                }
                if (mode == 2) {
                    record_uid_dns_answers(args->ctx, "unique.test", answers, count);
                    for (size_t i = 0; i < count; i += 2) answers[i].ttl = 0;
                }
                struct uid_dns_mappings *before = ng_malloc(sizeof(*before), "DNS initial state");
                assert(before != NULL);
                *before = args->ctx->uid_dns_mappings;
                memory_comparisons = 0; count_memory_comparisons = true;
                record_uid_dns_answers(args->ctx, "unique.test", answers, count);
                count_memory_comparisons = false;
                int general_comparisons = memory_comparisons;
                *saved = args->ctx->uid_dns_mappings;
                args->ctx->uid_dns_mappings = *before;
                ng_free(before, __FILE__, __LINE__);
                memory_comparisons = 0; count_memory_comparisons = true;
                record_uid_dns_unique_answers(args->ctx, "unique.test", answers, count);
                count_memory_comparisons = false;
                int unique_comparisons = memory_comparisons;
                assert(memcmp(saved, &args->ctx->uid_dns_mappings, sizeof(*saved)) == 0);
                if (mode == 0) {
                    // Positive TTL differences no longer require address comparisons.
                    assert(general_comparisons == unique_comparisons);
                }
                if (mode == 2)
                    assert(general_comparisons - unique_comparisons == (int) (count * count / 4));
                if (mode == 2)
                    for (size_t i = 0; i < count; i++) answers[i].ttl = (uint32_t) (5 + i);
            }
        }
    // The general API must still merge duplicates, with zero TTL taking precedence.
    for (int reverse = 0; reverse <= 1; reverse++) {
        clear(args->ctx); set_uid_test_clock(10000);
        struct uid_dns_answer answers[4] = {{.version = 4, .ttl = 30, .address = {1}},
                                           {.version = 6, .ttl = 40, .address = {1}},
                                           {.version = 4, .ttl = 0, .address = {1}},
                                           {.version = 6, .ttl = 5, .address = {1}}};
        if (reverse)
            for (size_t i = 0; i < 2; i++) {
                struct uid_dns_answer swap = answers[i];
                answers[i] = answers[3 - i]; answers[3 - i] = swap;
            }
        record_uid_dns_answers(args->ctx, "duplicates.test", answers, 4);
        assert(args->ctx->uid_dns_mappings.count == 1);
        assert(args->ctx->uid_dns_mappings.entry[0].version == 6);
        assert(args->ctx->uid_dns_mappings.entry[0].inference_until == 910000);
    }
    // A free earlier slot must not replace a later exact match. Refreshes retain
    // its key and loss deadline; insertion into the free slot must set a new key.
    // Paused admission still permits exact refresh/revocation, then insertion at its boundary.
    for (int version = 4; version <= 6; version += 2)
        for (int mode = 0; mode < 4; mode++) {
            bool unique = mode % 2 != 0, paused = mode >= 2;
            void (*record)(struct context *, const char *, const struct uid_dns_answer *, size_t) =
                    unique ? record_uid_dns_unique_answers : record_uid_dns_answers;
            clear(args->ctx); set_uid_test_clock(10000);
            char domain[256] = {0};
            memset(domain, 'a', 253); domain[63] = domain[127] = domain[191] = '.';
            struct uid_dns_answer answer = {.version = version, .ttl = 30, .address = {1}};
            record(args->ctx, "expired.example", &answer, 1);
            answer.address[0] = 2;
            record(args->ctx, domain, &answer, 1);
            struct uid_dns_mapping *free_slot = &args->ctx->uid_dns_mappings.entry[0];
            struct uid_dns_mapping *existing = &args->ctx->uid_dns_mappings.entry[1];
            free_slot->inference_until = 10000;
            existing->incomplete_until = 1100000;
            struct uid_dns_mapping expected = *existing;
            set_uid_test_clock(11000); answer.ttl = 10;
            args->ctx->uid_dns_mappings.last = SIZE_MAX; // Exercise the full exact-key search.
            if (paused) args->ctx->uid_dns_mappings.admit_after = 12000;
            record(args->ctx, domain, &answer, 1);
            expected.inference_until = 911000;
            assert(memcmp(existing, &expected, sizeof(expected)) == 0);
            assert(args->ctx->uid_dns_mappings.count == 2 && free_slot->inference_until == 10000);
            answer.ttl = 0;
            args->ctx->uid_dns_mappings.last = SIZE_MAX;
            record(args->ctx, domain, &answer, 1);
            expected.inference_until = 11000;
            assert(memcmp(existing, &expected, sizeof(expected)) == 0);
            if (paused) {
                struct uid_dns_answer missing = {.version = 10 - version, .ttl = 30, .address = {3}};
                record(args->ctx, "replacement.example", &missing, 1);
                assert(args->ctx->uid_dns_mappings.count == 2 && args->ctx->uid_dns_mappings.lost_count == 1);
                struct uid_dns_mapping *loss = &args->ctx->uid_dns_mappings.lost[0];
                assert(loss->inference_until == 911000 && loss->version == missing.version);
                assert(memcmp(loss->address, missing.address, missing.version == 4 ? 4 : 16) == 0);
                assert(strcmp(loss->domain, "replacement.example") == 0);
                missing.ttl = 0;
                record(args->ctx, "replacement.example", &missing, 1);
                assert(args->ctx->uid_dns_mappings.count == 2 && args->ctx->uid_dns_mappings.lost_count == 1);
                assert(loss->inference_until == 911000 && free_slot->inference_until == 10000);
                assert(memcmp(existing, &expected, sizeof(expected)) == 0);
                assert(args->ctx->uid_dns_mappings.admit_after == 12000);
            }
            set_uid_test_clock(12000); answer.ttl = UINT32_MAX;
            record(args->ctx, domain, &answer, 1);
            expected.inference_until = 12000 + UID_DNS_INFERENCE_MS;
            assert(memcmp(existing, &expected, sizeof(expected)) == 0);
            answer.version = 10 - version; answer.address[0] = 3;
            record(args->ctx, "replacement.example", &answer, 1);
            assert(args->ctx->uid_dns_mappings.count == 2);
            assert(memcmp(existing, &expected, sizeof(expected)) == 0);
            assert(free_slot->version == answer.version && free_slot->incomplete_until == 0);
            assert(memcmp(free_slot->address, answer.address, answer.version == 4 ? 4 : 16) == 0);
            assert(strcmp(free_slot->domain, "replacement.example") == 0);
            assert(free_slot->inference_until == expected.inference_until);
        }
    clear(args->ctx); set_uid_test_clock(-1);
    ng_free(saved, __FILE__, __LINE__);
    assert(allocations == 0 && sockets == 0 && string_refs == 0);
#endif
}

void test_uid_dns_history(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP) {
            clear(args->ctx); set_uid_test_clock(10000);
            uint8_t source[16] = {1};
            struct uid_dns_answer maps[3] = {{.version = version, .ttl = 1},
                                            {.version = version, .ttl = 1},
                                            {.version = version, .ttl = 1}};
            for (int i = 0; i < 3; i++) maps[i].address[0] = (uint8_t) (20 + i);
            record_uid_dns_answers(args->ctx, "history.example", maps, 3);
            assert(args->ctx->uid_dns_mappings.entry[0].inference_until == 910000);
            set_uid_test_clock(610000); // Browser uses the DNS addresses ten minutes later.
            record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
            int other = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == 12345);
            set_uid_test_clock(615000);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
            record_uid_observation(args->ctx, version, other, source, maps[2].address, 443, 23456);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            set_uid_test_clock(620000);
            // Both old owner observations expired; another family/transport may confirm ownership.
            struct uid_dns_answer cross = maps[0]; cross.version = 10 - version;
            record_uid_dns_answers(args->ctx, "history.example", &cross, 1);
            record_uid_observation(args->ctx, cross.version, other, source, cross.address, 443, 12345);
            assert(infer_uid_destination(args->ctx, version, protocol, source, maps[1].address, 443) == 12345);
            clear_sessions(args->ctx);
            assert(args->ctx->uid_dns_mappings.entry[1].inference_until == 910000);
            set_uid_test_clock(909999);
            record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == 12345);
            set_uid_test_clock(910000);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            record_uid_dns_answers(args->ctx, "history.example", maps, 1); // Refresh only the known address.
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            record_uid_dns_answers(args->ctx, "history.example", maps + 1, 1);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == 12345);
            maps[1].ttl = 0;
            record_uid_dns_answers(args->ctx, "history.example", maps + 1, 1);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            maps[1].ttl = 1;
            record_uid_dns_answers(args->ctx, "history.example", maps + 1, 1);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == 12345);
            maps[1].ttl = 0; // Oversized DNS responses must also revoke resident history.
            discard_uid_dns_answer(args->ctx, "history.example", maps + 1);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            maps[1].ttl = 1;
            record_uid_dns_answers(args->ctx, "history.example", maps + 1, 1);
            record_uid_dns_answers(args->ctx, "ambiguous.example", maps + 1, 1);
            assert(infer_uid_destination(args->ctx, version, other, source, maps[1].address, 443) == -1);
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0 && string_refs == 0);
        }
    set_uid_test_clock(-1);
#endif
}

void test_uid_inference_policy(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    test_uid_capacity_guards(args);
    int tunnel[2]; assert(pipe(tunnel) == 0);
    struct arguments running = *args; running.tun = tunnel[1];
    capture_packets = 1; deny_address = 1;
    for (int legacy = 0; legacy <= 1; legacy++)
        for (int version = 4; version <= 6; version += 2)
            for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
                for (size_t scenario = 0; scenario < 20; scenario++) {
                    clear(args->ctx); release_uid_cache(); set_uid_test_clock(10000);
                    args->ctx->sdk = legacy ? 28 : 29;
                    _Alignas(8) uint8_t packet[128];
                    size_t length = checksum_packet(packet, version, protocol, 0);
                    size_t off = version == 4 ? 20 : 40;
                    uint16_t sport = htons(12345), dport = htons(443);
                    memcpy(packet + off, &sport, 2); memcpy(packet + off + 2, &dport, 2);
                    packet_checksums(packet, length, off, protocol);
                    const void *source = packet + (version == 4 ? 12 : 8);
                    const void *dest = packet + (version == 4 ? 16 : 24);
                    struct uid_dns_answer maps[3] = {0};
                    for (int i = 0; i < 3; i++) {
                        maps[i].version = version; maps[i].ttl = scenario == 2 ? 1 : 30;
                        memcpy(maps[i].address, dest, version == 4 ? 4 : 16);
                        if (i != 1) maps[i].address[version == 4 ? 3 : 15] ^= (uint8_t) (0x40 + i);
                    }
                    if (scenario != 0)
                        record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
                    if ((scenario >= 2 && scenario <= 5) || scenario == 7 || scenario == 8 || scenario == 16 || scenario == 18)
                        record_uid_dns_answers(args->ctx, "test.example", maps, 3);
                    if (scenario == 18) {
                        maps[1].ttl = 0;
                        record_uid_dns_answers(args->ctx, "test.example", maps + 1, 1);
                    }
                    if (scenario == 2) {
                        set_uid_test_clock(911000);
                        record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
                    }
                    if (scenario == 3) record_uid_dns_answers(args->ctx, "other.example", maps + 1, 1);
                    if (scenario == 4) discard_uid_dns_answer(args->ctx, "test.example", maps + 1);
                    if (scenario == 5) args->ctx->uid_dns_mappings.entry[1].incomplete_until = 15000;
                    if (scenario == 6) record_uid_dns_answers(args->ctx, "test.example", maps + 1, 1);
                    if (scenario == 7)
                        record_uid_observation(args->ctx, version, protocol, source, maps[2].address, 443, 23456);
                    if (scenario == 8 || scenario == 11) {
                        args->ctx->uid_observations.lost[0] = args->ctx->uid_observations.entry[0];
                        args->ctx->uid_observations.lost_count = 1;
                        if (scenario == 11)
                            memcpy(args->ctx->uid_observations.lost[0].dest, dest, version == 4 ? 4 : 16);
                    }
                    if (scenario == 9) {
                        record_uid_observation(args->ctx, version, protocol, source, dest, 443, 12345);
                        record_uid_observation(args->ctx, version, protocol, source, dest, 443, 23456);
                    }
                    if (scenario == 10) record_uid_observation(args->ctx, version, protocol, source, dest, 443, 0);
                    if (scenario == 12) args->ctx->uid_observations.incomplete_until = 15000;
                    if (scenario == 17) set_uid_test_clock(15000);
                    if (scenario == 19) {
                        // Keep expired and live long hostnames to exercise ambiguity without logging.
                        char names[3][254];
                        for (int i = 0; i < 3; i++) {
                            memset(names[i], 'a', 253); names[i][253] = 0;
                            names[i][0] = (char) ('a' + i);
                            names[i][63] = names[i][127] = names[i][191] = '.';
                        }
                        record_uid_dns_answers(args->ctx, names[0], maps + 1, 1);
                        set_uid_test_clock(510000);
                        record_uid_dns_answers(args->ctx, names[1], maps + 1, 1);
                        record_uid_dns_answers(args->ctx, names[1], maps, 1);
                        record_uid_dns_answers(args->ctx, names[2], maps + 1, 1);
                        set_uid_test_clock(911000);
                        record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
                    }
                    uid_clock_failure = scenario == 13;
                    char table[256];
                    jint raw = scenario == 14 ? 0 : scenario == 15 ? 12345 : -1;
                    blocked_uid_lookup(version, legacy, raw, table, sizeof(table));
                    uid_probe_logs = 0;
                    int checks = policy_checks;
                    handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                    assert(policy_checks == checks + 1);
                    assert(policy_uid == (scenario == 14 ? 0 : scenario == 15 || scenario == 16 ? 12345 : -1));
                    assert(uid_probe_logs == 0);
                    if (scenario == 16) {
                        clear_sessions(args->ctx);
                        record_uid_dns_answers(args->ctx, "other.example", maps + 1, 1);
                        handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                        assert(policy_uid == -1 && uid_probe_logs == 0);
                    }
                    uid_clock_failure = false;
                    uint8_t output[2048];
                    while (is_readable(tunnel[0])) assert(read(tunnel[0], output, sizeof(output)) > 0);
                    clear(args->ctx); release_uid_cache();
                    assert(allocations == 0 && sockets == 0 && string_refs == 0);
                }
    capture_packets = 0; deny_address = 0; fake_proc = 0;
    args->ctx->sdk = 29; set_uid_test_clock(-1);
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_uid_relevant_domain_work(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int version = 4; version <= 6; version += 2)
        for (int known_version = 4; known_version <= 6; known_version += 2)
            for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
                for (int reverse = 0; reverse <= 1; reverse++)
                    for (int mode = 0; mode < 5; mode++) {
                        clear(args->ctx); set_uid_test_clock(10000);
                        uint8_t source[16] = {10, 0, 0, 2};
                        struct uid_dns_answer target = {.version = version, .ttl = 30, .address = {203, 0, 113, 2}};
                        struct uid_dns_answer known = {.version = known_version, .ttl = 30, .address = {203, 0, 113, 3}};
                        int other_protocol = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
                        for (size_t n = 0; n < UID_DNS_MAPPINGS; n++) {
                            size_t i = reverse ? UID_DNS_MAPPINGS - n - 1 : n;
                            if (i < 2) {
                                const struct uid_dns_answer *answer = i == 0 ? &target : &known;
                                record_uid_dns_answers(args->ctx, "candidate.example", answer, 1);
                            } else {
                                char domain[32];
                                snprintf(domain, sizeof(domain), "unrelated-%zu.example", i);
                                struct uid_dns_answer answer = {.version = i % 2 ? 4 : 6, .ttl = 30, .address = {198}};
                                answer.address[answer.version == 4 ? 2 : 14] = (uint8_t) (i >> 8);
                                answer.address[answer.version == 4 ? 3 : 15] = (uint8_t) i;
                                record_uid_dns_answers(args->ctx, domain, &answer, 1);
                                if (i < UID_OBSERVATIONS)
                                    record_uid_observation(args->ctx, answer.version, other_protocol,
                                                             source, answer.address, 443, 12345 + (jint) (i % 2));
                            }
                        }
                        record_uid_observation(args->ctx, known.version, other_protocol, source, known.address, 443, 12345);
                        assert(args->ctx->uid_dns_mappings.count == UID_DNS_MAPPINGS);
                        assert(args->ctx->uid_observations.count == UID_OBSERVATIONS - 1);
                        size_t slot = args->ctx->uid_observations.last;
                        if (mode == 1) {
                            // Neither a lost owner nor DNS loss for unrelated names may veto this hostname.
                            args->ctx->uid_observations.lost[0] = args->ctx->uid_observations.entry[slot == 0 ? 1 : 0];
                            args->ctx->uid_observations.lost_count = 1;
                            struct uid_dns_answer unrelated = {.version = 6, .ttl = 30, .address = {198}};
                            unrelated.address[15] = 2;
                            discard_uid_dns_answer(args->ctx, "unrelated-2.example", &unrelated);
                        }
                        if (mode == 2)
                            record_uid_observation(args->ctx, known.version, protocol, source, known.address, 443, 23456);
                        if (mode == 3) {
                            args->ctx->uid_observations.lost[0] = args->ctx->uid_observations.entry[slot];
                            args->ctx->uid_observations.lost_count = 1;
                        }
                        if (mode == 4) discard_uid_dns_answer(args->ctx, "candidate.example", &known);
                        memory_comparisons = 0; count_memory_comparisons = true;
                        jint uid = infer_uid_destination(args->ctx, version, protocol, source, target.address, 443);
                        count_memory_comparisons = false;
                        assert(uid == (mode < 2 ? 12345 : -1));
                        // Only two mappings belong to this candidate, despite a full DNS cache and hundreds of owners.
                        assert(memory_comparisons < 12 * (UID_DNS_MAPPINGS + UID_OBSERVATIONS + UID_OBSERVATION_LOSSES));
                        assert(allocations == 0 && sockets == 0 && string_refs == 0);
                    }
    clear(args->ctx); set_uid_test_clock(-1);
#endif
}

void test_uid_domain_work(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    // Missing/expired DNS candidates must not scan unrelated destination losses.
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
            for (int mode = 0; mode < 7; mode++) {
                clear(args->ctx); set_uid_test_clock(10000);
                uint8_t source[16] = {1}, dest[16] = {2};
                struct uid_dns_answer pair[2] = {{.version = version, .ttl = 30, .address = {2}},
                                                 {.version = version, .ttl = 30, .address = {3}}};
                record_uid_dns_answers(args->ctx, "candidate.example", pair, 2);
                record_uid_observation(args->ctx, version, protocol, source, pair[1].address, 443, 12345);
                struct uid_dns_mappings *dns = &args->ctx->uid_dns_mappings;
                if (mode == 0) memset(dns, 0, sizeof(*dns));
                if (mode == 1) dns->entry[0].inference_until = dns->entry[1].inference_until = 10000;
                if (mode == 2) dest[0] = 4;
                if (mode == 3) dns->entry[0].version = dns->entry[1].version = 10 - version;
                if (mode == 4) {
                    dns->entry[0].inference_until = 10000;
                    dns->entry[0].incomplete_until = 15000;
                }
                memory_comparisons = 0; count_memory_comparisons = true;
                jint baseline_uid = infer_uid_destination(args->ctx, version, protocol, source, dest, 443);
                count_memory_comparisons = false;
                int baseline_comparisons = memory_comparisons;
                assert(baseline_uid == (mode >= 5 ? 12345 : -1));
                for (uint32_t i = 0; i < UID_DNS_LOSSES; i++) {
                    dns->lost[i].version = version;
                    dns->lost[i].address[0] = 5;
                    memcpy(dns->lost[i].address + 1, &i, 3);
                    strcpy(dns->lost[i].domain, "lost.example");
                    dns->lost[i].inference_until = 15000;
                }
                dns->lost_count = UID_DNS_LOSSES;
                if (mode == 5) memcpy(dns->lost[UID_DNS_LOSSES - 1].address, dest, sizeof(dest));
                memory_comparisons = 0; count_memory_comparisons = true;
                jint uid = infer_uid_destination(args->ctx, version, protocol, source, dest, 443);
                count_memory_comparisons = false;
                assert(uid == (mode == 6 ? 12345 : -1));
                if (mode < 5) assert(memory_comparisons == baseline_comparisons);
                if (mode == 6) assert(memory_comparisons == baseline_comparisons + UID_DNS_LOSSES);
                assert(allocations == 0 && sockets == 0 && string_refs == 0);
            }
    // Unrelated address guards add one pass, not one pass per shared hostname.
    for (int version = 4; version <= 6; version += 2)
        for (int scenario = 0; scenario < 7; scenario++) {
            clear(args->ctx); set_uid_test_clock(10000);
            uint8_t source[16] = {1};
            struct uid_dns_answer pair[2] = {{.version = version, .ttl = 30, .address = {2}},
                                             {.version = version, .ttl = 30, .address = {3}}};
            for (size_t i = 0; i < 8; i++) {
                char domain[32];
                snprintf(domain, sizeof(domain), "guard-%zu.example", i);
                record_uid_dns_answers(args->ctx, domain, pair, 2);
            }
            record_uid_observation(args->ctx, version, IPPROTO_TCP, source, pair[1].address, 443, 12345);
            struct uid_dns_mappings *dns = &args->ctx->uid_dns_mappings;
            const size_t mappings = dns->count;
            // Fill the rest with unrelated keys; these cannot identify an owner.
            for (size_t i = mappings; i < UID_DNS_MAPPINGS; i++) {
                dns->entry[i].version = version;
                dns->entry[i].address[0] = 4;
                strcpy(dns->entry[i].domain, "unrelated.example");
            }
            dns->count = UID_DNS_MAPPINGS;
            memory_comparisons = 0; count_memory_comparisons = true;
            assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, pair[0].address, 443) == 12345);
            count_memory_comparisons = false;
            int baseline = memory_comparisons;
            for (size_t i = 0; i < UID_DNS_LOSSES; i++) {
                dns->lost[i] = dns->entry[mappings];
                dns->lost[i].inference_until = scenario == 1 ? 10000 : 15000;
            }
            dns->lost_count = UID_DNS_LOSSES;
            for (size_t i = mappings; i < dns->count; i++)
                dns->entry[i].incomplete_until = scenario == 1 ? 10000 : 15000;
            if (scenario == 2) memcpy(dns->lost[UID_DNS_LOSSES - 1].address, pair[0].address, 16);
            if (scenario == 3) memcpy(dns->entry[UID_DNS_MAPPINGS - 1].address, pair[0].address, 16);
            if (scenario == 4) strcpy(dns->lost[UID_DNS_LOSSES - 1].domain, "guard-7.example");
            if (scenario == 5) strcpy(dns->entry[UID_DNS_MAPPINGS - 1].domain, "guard-7.example");
            if (scenario == 6) {
                for (size_t i = 0; i < dns->lost_count; i++) {
                    dns->lost[i].version = 10 - version;
                    memcpy(dns->lost[i].address, pair[0].address, 16);
                }
                for (size_t i = mappings; i < dns->count; i++) {
                    dns->entry[i].version = 10 - version;
                    memcpy(dns->entry[i].address, pair[0].address, 16);
                }
            }
            memory_comparisons = 0; count_memory_comparisons = true;
            jint uid = infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, pair[0].address, 443);
            count_memory_comparisons = false;
            assert(uid == ((scenario == 0 || scenario == 1 || scenario == 6) ? 12345 : -1));
            assert(memory_comparisons <= baseline + UID_DNS_LOSSES + UID_DNS_MAPPINGS - (int) mappings);
            assert(allocations == 0 && sockets == 0 && string_refs == 0);
        }
    const size_t domains = UID_DNS_MAPPINGS / 2;
    assert(domains <= UID_OBSERVATIONS);
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
            for (int reverse = 0; reverse <= 1; reverse++)
                for (int scenario = 0; scenario < 9; scenario++) {
                    clear(args->ctx); set_uid_test_clock(10000);
                    uint8_t source[16] = {0};
                    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                     version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                    struct uid_dns_answer pair[2] = {{.version = version, .ttl = 30},
                                                     {.version = version, .ttl = 30}};
                    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                     version == 4 ? "203.0.113.2" : "2001:db8::2", pair[0].address) == 1);
                    char domain[32];
                    for (size_t i = 0; i < domains; i++) {
                        size_t name = reverse ? domains - i - 1 : i;
                        snprintf(domain, sizeof(domain), "shared-%zu.example", name);
                        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                         version == 4 ? "198.18.0.0" : "2001:db8:1::", pair[1].address) == 1);
                        pair[1].address[version == 4 ? 2 : 14] = (uint8_t) (name >> 8);
                        pair[1].address[version == 4 ? 3 : 15] = (uint8_t) name;
                        record_uid_dns_answers(args->ctx, domain, pair, 2);
                        record_uid_observation(args->ctx, version,
                                               protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP,
                                               source, pair[1].address, 443, 12345);
                    }
                    assert(args->ctx->uid_dns_mappings.count == UID_DNS_MAPPINGS);
                    assert(args->ctx->uid_dns_mappings.lost_count == 0);
                    struct uid_observation *last = &args->ctx->uid_observations.entry[domains - 1];
                    if (scenario == 1) last->uid = 23456;
                    if (scenario == 2) {
                        last->expires = 10000;
                        args->ctx->uid_observations.last = 0; // Newest live observation.
                    }
                    if (scenario == 3) {
                        args->ctx->uid_observations.lost[0] = *last;
                        args->ctx->uid_observations.lost_count = 1;
                    }
                    if (scenario == 4) discard_uid_dns_answer(args->ctx, domain, pair + 1);
                    if (scenario == 5) args->ctx->uid_dns_mappings.entry[UID_DNS_MAPPINGS - 1].incomplete_until = 15000;
                    if (scenario == 6) discard_uid_dns_answer(args->ctx, "unseen.example", pair);
                    if (scenario == 7) last->uid = 0;
                    if (scenario == 8) last->uid = own_uid;
                    memory_comparisons = 0; count_memory_comparisons = true;
                    jint uid = infer_uid_destination(args->ctx, version, protocol, source, pair[0].address, 443);
                    count_memory_comparisons = false;
                    assert(uid == (scenario == 0 ? 12345 : -1));
                    // Permit a constant number of owner/DNS passes, independent of
                    // the number of hostnames sharing the destination address.
                    size_t bound = 4u * UID_DNS_MAPPINGS * (UID_OBSERVATIONS + UID_OBSERVATION_LOSSES);
                    if ((size_t) memory_comparisons > bound) {
                        char detail[128];
                        snprintf(detail, sizeof(detail), "shared-domain comparisons=%d bound=%zu", memory_comparisons, bound);
                        native_test_fail(detail, __FILE__, __LINE__);
                    }
                    assert(allocations == 0 && sockets == 0 && string_refs == 0);
                }
    clear(args->ctx); set_uid_test_clock(-1);
#endif
}

void test_uid_shared_domains(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    const char *names[] = {"alpha.example", "beta.example"};
    for (int version = 4; version <= 6; version += 2)
        for (int known_version = 4; known_version <= 6; known_version += 2)
            for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
                for (int reverse = 0; reverse <= 1; reverse++)
                    for (int scenario = 0; scenario < 26; scenario++) {
                        if (scenario == 18 && known_version != version) continue;
                        clear(args->ctx); set_uid_test_clock(10000);
                        uint8_t source[16] = {0}, known_source[16] = {0}, other_source[16];
                        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                         version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                        assert(inet_pton(known_version == 4 ? AF_INET : AF_INET6,
                                         known_version == 4 ? "10.0.0.2" : "2001:db8::1", known_source) == 1);
                        memcpy(other_source, known_source, sizeof(other_source));
                        other_source[known_version == 4 ? 3 : 15] ^= 0x40;
                        struct uid_dns_answer maps[3] = {
                            {.version = version, .ttl = 30},
                            {.version = known_version, .ttl = 30},
                            {.version = known_version, .ttl = 30}};
                        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                         version == 4 ? "203.0.113.2" : "2001:db8::2", maps[0].address) == 1);
                        for (int i = 1; i < 3; i++)
                            assert(inet_pton(known_version == 4 ? AF_INET : AF_INET6,
                                             known_version == 4 ? (i == 1 ? "192.0.2.10" : "192.0.2.11")
                                                                : (i == 1 ? "2001:db8::10" : "2001:db8::11"),
                                             maps[i].address) == 1);
                        for (int i = 0; i < 2; i++) {
                            int name = reverse ? 1 - i : i;
                            struct uid_dns_answer pair[] = {maps[0], maps[name + 1]};
                            record_uid_dns_answers(args->ctx, names[name], pair, 2);
                        }
                        int known_protocol = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
                        record_uid_observation(args->ctx, known_version, known_protocol, known_source,
                                               maps[1].address, 443, 12345);
                        jint second_uid = scenario == 2 || scenario == 7 || scenario == 15 ? 23456 :
                                          scenario == 3 ? 0 : scenario == 4 ? 1000 :
                                          scenario == 5 ? own_uid : 12345;
                        if (scenario != 1 && scenario != 16)
                            record_uid_observation(args->ctx, known_version, protocol,
                                                   scenario == 18 ? other_source : known_source,
                                                   maps[2].address, scenario == 17 ? 444 : 443, second_uid);
                        if (scenario == 6) {
                            set_uid_test_clock(15000); // Only beta's ownership expires.
                            record_uid_observation(args->ctx, known_version, known_protocol, known_source,
                                                   maps[1].address, 443, 12345);
                        }
                        if (scenario == 7) {
                            set_uid_test_clock(10500);
                            record_uid_dns_answers(args->ctx, names[0], maps, 2);
                            set_uid_test_clock(910000); // Only beta's DNS history expires.
                            record_uid_observation(args->ctx, known_version, known_protocol, known_source,
                                                   maps[1].address, 443, 12345);
                            record_uid_observation(args->ctx, known_version, protocol, known_source,
                                                   maps[2].address, 443, second_uid);
                        }
                        if (scenario == 8) discard_uid_dns_answer(args->ctx, names[1], maps + 2);
                        if (scenario == 9) discard_uid_dns_answer(args->ctx, "unseen.example", maps);
                        if (scenario == 10)
                            for (size_t i = 0; i < args->ctx->uid_dns_mappings.count; i++)
                                if (strcmp(args->ctx->uid_dns_mappings.entry[i].domain, names[1]) == 0)
                                    args->ctx->uid_dns_mappings.entry[i].incomplete_until = 15000;
                        if (scenario == 11 || scenario == 19) {
                            args->ctx->uid_observations.lost[0] = args->ctx->uid_observations.entry[1];
                            args->ctx->uid_observations.lost_count = 1;
                            if (scenario == 19) args->ctx->uid_observations.lost[0].expires = 10000;
                        }
                        if (scenario == 12)
                            record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 34567);
                        if (scenario == 13) {
                            struct uid_dns_answer unrelated = maps[0]; unrelated.address[0] ^= 0x40;
                            discard_uid_dns_answer(args->ctx, "unrelated.example", &unrelated);
                        }
                        if (scenario == 14)
                            record_uid_observation(args->ctx, known_version, known_protocol, known_source,
                                                   maps[2].address, 443, 23456);
                        if (scenario == 15) {
                            struct uid_dns_answer revoked = maps[0]; revoked.ttl = 0;
                            record_uid_dns_answers(args->ctx, names[1], &revoked, 1);
                        }
                        if (scenario == 20 || scenario == 21) {
                            struct uid_dns_answer pair[] = {maps[0], maps[1]};
                            record_uid_dns_answers(args->ctx, "gamma.example", pair, scenario == 20 ? 1 : 2);
                        }
                        if (scenario >= 22) {
                            // A revoked mapping can retain a hostname guard, even
                            // after a matching owner was found earlier in the scan.
                            struct uid_dns_answer revoked = maps[1];
                            revoked.address[known_version == 4 ? 3 : 15] ^= 0x40;
                            const char *guard_name = scenario < 24 ? names[0] : "unrelated.example";
                            record_uid_dns_answers(args->ctx, guard_name, &revoked, 1);
                            struct uid_dns_mappings *dns = &args->ctx->uid_dns_mappings;
                            dns->entry[dns->count - 1].incomplete_until = scenario % 2 == 0 ? 15000 : 10000;
                            revoked.ttl = 0;
                            record_uid_dns_answers(args->ctx, guard_name, &revoked, 1);
                            if (!reverse) {
                                struct uid_dns_mapping swap = dns->entry[0];
                                dns->entry[0] = dns->entry[dns->count - 1];
                                dns->entry[dns->count - 1] = swap;
                            }
                        }
                        size_t count = args->ctx->uid_observations.count;
                        jint expected = scenario == 12 ? 34567 :
                                        scenario == 0 || scenario == 7 || scenario == 13 ||
                                        scenario == 15 || scenario == 19 || scenario == 21 ||
                                        scenario >= 23 ? 12345 : -1;
                        assert(infer_uid_destination(args->ctx, version, protocol, source, maps[0].address, 443) == expected);
                        assert(args->ctx->uid_observations.count == count); // Inference never feeds evidence back.
                        assert(allocations == 0 && sockets == 0 && string_refs == 0);
                    }
    clear(args->ctx); set_uid_test_clock(-1);
#endif
}

void test_uid_domain(struct arguments *args, int kind) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    if (kind == 0) {
        int tunnel[2]; assert(pipe(tunnel) == 0);
        struct arguments running = *args; running.tun = tunnel[1];
        capture_packets = 1; policy_requires_known_uid = true;
        for (int known_version = 4; known_version <= 6; known_version += 2)
            for (int version = 4; version <= 6; version += 2)
                for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
                    for (int cross_protocol = 0; cross_protocol <= 1; cross_protocol++)
                        for (int legacy = 0; legacy <= 1; legacy++)
                            for (int mode = 0; mode < 8; mode++) {
                                bool historical = mode % 4 >= 2;
                                int denied = mode % 2;
                                clear(args->ctx); release_uid_cache(); set_uid_test_clock(10000);
                                args->ctx->sdk = legacy ? 28 : 29;
                                _Alignas(8) uint8_t packet[128]; uint8_t dns[512]; char table[256];
                                int known_protocol = cross_protocol
                                        ? (protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP) : protocol;
                                size_t length = checksum_packet(packet, known_version, known_protocol, 0);
                                size_t off = known_version == 4 ? 20 : 40;
                                assert(inet_pton(known_version == 4 ? AF_INET : AF_INET6,
                                                 known_version == 4 ? "192.0.2.10" : "2001:db8::10",
                                                 packet + (known_version == 4 ? 16 : 24)) == 1);
                                store16(packet + off + 2, protocol == IPPROTO_TCP ? 443 : 5000);
                                packet_checksums(packet, length, off, known_protocol);
                                blocked_uid_lookup(known_version, legacy, 12345, table, sizeof(table));
                                deny_address = 0;
                                handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                                assert(policy_uid == 12345 && !captured_owner.inferred);
                                assert(args->ctx->uid_observations.count == 1);
                                // Learn both address families after the confirmed connection.
                                struct ng_session dns_session = {.protocol = IPPROTO_UDP};
                                for (int family = 4; family <= 6; family += 2) {
                                    size_t size = test_uid_dns_response(dns, family, true);
                                    parse_dns_response(&running, &dns_session, dns, &size);
                                }
                                if (mode >= 4) {
                                    // A second plausible hostname has the same complete address set.
                                    size_t count = args->ctx->uid_dns_mappings.count;
                                    for (size_t i = 0; i < count; i++) {
                                        const struct uid_dns_mapping *mapping = &args->ctx->uid_dns_mappings.entry[i];
                                        struct uid_dns_answer answer = {.version = mapping->version, .ttl = 30};
                                        memcpy(answer.address, mapping->address, sizeof(answer.address));
                                        record_uid_dns_answers(args->ctx, "shared.example", &answer, 1);
                                    }
                                }
                                if (historical) {
                                    set_uid_test_clock(610000);
                                    record_uid_observation(args->ctx, known_version, known_protocol,
                                                           packet + (known_version == 4 ? 12 : 8),
                                                           packet + (known_version == 4 ? 16 : 24),
                                                           protocol == IPPROTO_TCP ? 443 : 5000, 12345);
                                }
                                length = checksum_packet(packet, version, protocol, 0);
                                off = version == 4 ? 20 : 40;
                                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                                 version == 4 ? "192.0.2.11" : "2001:db8::11",
                                                 packet + (version == 4 ? 16 : 24)) == 1);
                                store16(packet + off, 12346);
                                packet_checksums(packet, length, off, protocol);
                                blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                                uid_test_clock = historical ? 614000 : 14000; deny_address = denied;
                                int checks = policy_checks;
                                handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                                assert(policy_uid == 12345 && policy_checks == checks + 1);
                                if (protocol == IPPROTO_TCP || !denied)
                                    assert(captured_owner.uid == 12345 && captured_owner.inferred);
                                else {
                                    assert(args->ctx->ng_session != NULL && args->ctx->ng_session->udp.uid == 12345);
                                    assert(args->ctx->ng_session->udp.uid_inferred);
                                }
                                assert(args->ctx->uid_observations.count == 1); // No inference feedback.
                                clear(args->ctx); release_uid_cache();
                                uint8_t output[2048];
                                while (is_readable(tunnel[0])) assert(read(tunnel[0], output, sizeof(output)) > 0);
                                assert(allocations == 0 && sockets == 0 && string_refs == 0);
                            }
        capture_packets = 0; policy_requires_known_uid = false; deny_address = 0;
        args->ctx->sdk = 29; set_uid_test_clock(-1);
        close(tunnel[0]); close(tunnel[1]);
        return;
    }
    uint8_t source[16] = {10, 0, 0, 2}, other_source[16] = {10, 0, 0, 3};
    uint8_t source6[16]; assert(inet_pton(AF_INET6, "2001:db8::1", source6) == 1);
    struct uid_dns_answer maps[4] = {{.ttl = 10, .version = 4}, {.ttl = 10, .version = 4},
                                    {.ttl = 10, .version = 6}, {.ttl = 10, .version = 6}};
    const char *addresses[] = {"192.0.2.10", "192.0.2.11", "2001:db8::10", "2001:db8::11"};
    for (int i = 0; i < 4; i++) assert(inet_pton(i < 2 ? AF_INET : AF_INET6, addresses[i], maps[i].address) == 1);
    set_uid_test_clock(10000);
    record_uid_dns_answers(args->ctx, "test", maps, 4);
    record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[0].address, 443, 12345);
    assert(infer_uid_destination(args->ctx, 6, IPPROTO_TCP, source6, maps[3].address, 443) == 12345);
    if (kind == 1) {
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, other_source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 444) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, other_source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 444) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443) == 12345);
        record_uid_observation(args->ctx, 6, IPPROTO_UDP, source6, maps[2].address, 443, 23456);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443) == -1);
        uid_test_clock = 10500;
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        uid_test_clock = 14999;
        record_uid_observation(args->ctx, 4, IPPROTO_UDP, source, maps[0].address, 443, 12345);
        uid_test_clock = 15000;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == 12345);
        uid_test_clock = 15500;
        // TCP evidence has expired; the independent UDP confirmation still applies to both.
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == 12345);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443) == 12345);
        uid_test_clock = 19999;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443) == -1);
        const jint excluded[] = {0, 1000, own_uid};
        for (size_t i = 0; i < sizeof(excluded) / sizeof(*excluded); i++) {
            memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
            record_uid_observation(args->ctx, 4, IPPROTO_UDP, source, maps[0].address, 443, excluded[i]);
            assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
            record_uid_observation(args->ctx, 6, IPPROTO_TCP, source6, maps[2].address, 443, 12345);
            assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
            assert(infer_uid_destination(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443) == -1);
        }
        memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        record_uid_dns_answers(args->ctx, "other", &maps[1], 1);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443, 23456);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == 23456);
        record_uid_observation(args->ctx, 4, IPPROTO_UDP, source, maps[1].address, 443, 34567);
        // An exact-IP conflict cannot fall through to domain evidence for UID 12345.
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
    } else if (kind == 2) {
        clear(args->ctx); set_uid_test_clock(10000);
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        record_uid_dns_answers(args->ctx, "unrelated", maps + 2, 2);
        for (unsigned int i = 4; i < UID_DNS_MAPPINGS; i++) {
            char domain[32]; snprintf(domain, sizeof(domain), "other-%u", i);
            struct uid_dns_answer extra = {.ttl = 10, .version = 4}; extra.address[3] = (uint8_t) i;
            record_uid_dns_answers(args->ctx, domain, &extra, 1);
        }
        uid_test_clock = 10500;
        struct uid_dns_answer late[2] = {maps[0], maps[1]};
        late[0].address[0] = late[1].address[0] = 198;
        record_uid_dns_answers(args->ctx, "late", late, 2);
        assert(args->ctx->uid_dns_mappings.count == UID_DNS_MAPPINGS);
        record_uid_observation(args->ctx, 6, IPPROTO_TCP, source6, maps[2].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, 6, IPPROTO_TCP, source6, maps[3].address, 443) == 12345);
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, late[0].address, 443, 12345);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, late[1].address, 443) == 12345);
        // Restoring a discarded mapping cannot erase its original loss deadline.
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        uid_test_clock = 909500;
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        uid_test_clock = 909999;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        uid_test_clock = 910000;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == 12345);
        uid_test_clock = 910500;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        // Lost DNS evidence must never suppress direct-IP evidence.
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443, 23456);
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == 23456);
        test_uid_dns_loss_limits(args);
    } else {
        struct context isolated = {0};
        assert(infer_uid_destination(&isolated, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        discard_uid_dns_answer(args->ctx, "test", &maps[1]);
        assert(args->ctx->uid_dns_mappings.lost_count == 1);
        uid_clock_failure = true;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(args->ctx->uid_dns_mappings.count == 0 && args->ctx->uid_dns_mappings.lost_count == 0 &&
               args->ctx->uid_dns_mappings.admit_after == 0 && args->ctx->uid_observations.count == 0);
        uid_clock_failure = false;
        record_uid_dns_answers(args->ctx, "test", maps, 2);
        record_uid_observation(args->ctx, 4, IPPROTO_TCP, source, maps[0].address, 443, 12345);
        uid_test_clock--;
        assert(infer_uid_destination(args->ctx, 4, IPPROTO_TCP, source, maps[1].address, 443) == -1);
        assert(args->ctx->uid_dns_mappings.count == 0 && args->ctx->uid_observations.count == 0);
    }
    clear(args->ctx); set_uid_test_clock(-1);
    assert(args->ctx->uid_dns_mappings.count == 0 && allocations == 0 && sockets == 0 && string_refs == 0);
#endif
}

void test_uid_lazy_logging(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    const int expected_formats = ANDROID_LOG_INFO >= LOG_LEVEL ? 2 : 0;
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
            for (int root = 0; root <= 1; root++) {
                uint8_t source[16] = {0}, dest[16] = {0};
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                 version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                 version == 4 ? "203.0.113.2" : "2001:db8::2", dest) == 1);
                jint owner = root ? 0 : 12345;
                char table[256]; blocked_uid_lookup(version, true, owner, table, sizeof(table));
                address_formats = 0;
                assert(get_uid(version, protocol, source, 12345, dest, 443) == owner);
                assert(address_formats == expected_formats && uid_cache_size == 1);
                char expected[256];
                snprintf(expected, sizeof(expected), "uid v%d p%d %s/12345 > %s/443 => %d",
                         version, protocol, version == 4 ? "10.0.0.2" : "2001:db8::1",
                         version == 4 ? "203.0.113.2" : "2001:db8::2", owner);
                if (ANDROID_LOG_INFO >= LOG_LEVEL) assert(strcmp(last_log, expected) == 0);
                int closes = file_closes;
                address_formats = 0;
                assert(get_uid(version, protocol, source, 12345, dest, 443) == owner);
                assert(address_formats == expected_formats && uid_cache_size == 1);
                if (ANDROID_LOG_INFO >= LOG_LEVEL) assert(strcmp(last_log, expected) == 0);
                assert(file_closes == closes + (version == 4 ? 1 : 0));
                release_uid_cache();
                if (version == 4) {
                    blocked_uid_lookup(version, true, owner, table, sizeof(table));
                    proc6_open_error = EACCES;
                    address_formats = 0;
                    assert(get_uid(version, protocol, source, 12345, dest, 443) == owner);
                    assert(address_formats == expected_formats);
                    if (ANDROID_LOG_INFO >= LOG_LEVEL) assert(strcmp(last_log, expected) == 0);
                    proc6_open_error = 0;
                    release_uid_cache();
                }
                blocked_uid_lookup(version, true, -1, table, sizeof(table));
                address_formats = 0;
                assert(get_uid(version, protocol, source, 12345, dest, 443) == -1);
                assert(address_formats == expected_formats && uid_cache_size == 0);
                proc_contents = "header\nmalformed row\n";
                address_formats = 0;
                assert(get_uid(version, protocol, source, 12345, dest, 443) < 0);
                assert(address_formats == expected_formats);
                release_uid_cache();
                assert(allocations == 0 && sockets == 0 && string_refs == 0);
            }
    fake_proc = 0;
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static void test_uid_selective_eviction(struct arguments *args, int version, int protocol) {
    uint8_t source[16] = {0};
    struct uid_dns_answer maps[5] = {0};
    int family = version == 4 ? AF_INET : AF_INET6;
    assert(inet_pton(family, version == 4 ? "10.1.10.1" : "fd00::1", source) == 1);
    for (size_t i = 0; i < 5; i++) {
        maps[i].version = version; maps[i].ttl = 60;
        assert(inet_pton(family, version == 4 ? "192.0.2.1" : "2001:db8::1", maps[i].address) == 1);
        maps[i].address[version == 4 ? 3 : 15] += (uint8_t) i;
    }
    clear(args->ctx); uid_test_clock = 10000;
    record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 23456);
    uid_test_clock = 10001;
    record_uid_observation(args->ctx, version, protocol, source, maps[1].address, 443, 12345);
    record_uid_observation(args->ctx, version, protocol, source, maps[3].address, 443, 12345);
    for (unsigned int i = 3; i < UID_OBSERVATIONS; i++)
        record_uid_observation(args->ctx, version, protocol, source, maps[3].address,
                               (uint16_t) (10000 + i), 12345);
    uid_test_clock = 11000;
    record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
    // A discarded conflicting owner must not disable unrelated exact endpoints.
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[3].address, 443) == 12345);
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[0].address, 443) == -1);
    // DNS can arrive after eviction: resolve relationships at inference time.
    record_uid_dns_answers(args->ctx, "affected.example", maps, 3);
    record_uid_dns_answers(args->ctx, "unrelated.example", maps + 3, 2);
    struct uid_dns_answer cross = {.version = 10 - version, .ttl = 60};
    uint8_t cross_source[16] = {0};
    assert(inet_pton(cross.version == 4 ? AF_INET : AF_INET6,
                     cross.version == 4 ? "198.51.100.1" : "2001:db8:1::1", cross.address) == 1);
    assert(inet_pton(cross.version == 4 ? AF_INET : AF_INET6,
                     cross.version == 4 ? "10.1.10.1" : "fd00::1", cross_source) == 1);
    record_uid_dns_answers(args->ctx, "affected.example", &cross, 1);
    assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, maps[4].address, 443) == 12345);
    assert(infer_uid_destination(args->ctx, version, IPPROTO_TCP, source, maps[2].address, 443) == -1);
    assert(infer_uid_destination(args->ctx, cross.version, IPPROTO_UDP, cross_source, cross.address, 443) == -1);
    uid_test_clock = 14999;
    record_uid_observation(args->ctx, version, protocol, source, maps[0].address, 443, 12345);
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[2].address, 443) == -1);
    uid_test_clock = 15000;
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[2].address, 443) == 12345);
    assert(infer_uid_destination(args->ctx, cross.version, IPPROTO_TCP, cross_source, cross.address, 443) == 12345);
    // All loss records are bounded. Exhaustion must never discard a live guard.
    clear(args->ctx); uid_test_clock = 10000;
    for (unsigned int i = 0; i < UID_OBSERVATIONS; i++)
        record_uid_observation(args->ctx, version, protocol, source, maps[0].address,
                               (uint16_t) (10000 + i), 23456);
    uid_test_clock = 11000;
    for (unsigned int i = 0; i <= UID_OBSERVATION_LOSSES; i++)
        record_uid_observation(args->ctx, version, protocol, source, maps[1].address,
                               (uint16_t) (20000 + i), 12345);
    assert(args->ctx->uid_observations.lost_count == UID_OBSERVATION_LOSSES);
    assert(args->ctx->uid_observations.lost[0].expires == 15000);
    assert(args->ctx->uid_observations.incomplete_until == 15000);
    uint16_t latest = 20000 + UID_OBSERVATION_LOSSES;
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[1].address, latest) == -1);
    uid_test_clock = 14999;
    record_uid_observation(args->ctx, version, protocol, source, maps[1].address, latest, 12345);
    uid_test_clock = 15000;
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[1].address, latest) == 12345);
    // Reuse expired loss storage after filling the expired observation slots.
    for (unsigned int i = 0; i < UID_OBSERVATIONS - UID_OBSERVATION_LOSSES; i++)
        record_uid_observation(args->ctx, version, protocol, source, maps[3].address,
                               (uint16_t) (30000 + i), 12345);
    assert(args->ctx->uid_observations.lost[0].expires == 16000);
    assert(args->ctx->uid_observations.incomplete_until == 15000);
    assert(infer_uid_destination(args->ctx, version, protocol, source, maps[1].address, latest) == 12345);
    clear(args->ctx); uid_test_clock = 10000;
    assert(allocations == 0 && sockets == 0 && string_refs == 0);
}
#endif

void test_uid_destination(struct arguments *args, int kind) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    test_monotonic_enabled = true;
    uid_clock_enabled = true;
    test_monotonic_ms = 10000;
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP) {
            clear(args->ctx); uid_test_clock = 10000;
            _Alignas(8) uint8_t packet[128];
            size_t length = checksum_packet(packet, version, protocol, 0), off = version == 4 ? 20 : 40;
            void *source = packet + (version == 4 ? 12 : 8), *dest = packet + (version == 4 ? 16 : 24);
            uint16_t port = protocol == IPPROTO_TCP ? 443 : 5000;
            if (kind == 4) {
                int tunnel[2]; assert(pipe(tunnel) == 0);
                struct arguments running = *args; running.tun = tunnel[1];
                capture_packets = 1; policy_requires_known_uid = true;
                for (int legacy = 0; legacy <= 1; legacy++)
                    for (int cross_protocol = 0; cross_protocol <= 1; cross_protocol++)
                    for (int dns = 0; dns <= (protocol == IPPROTO_UDP); dns++)
                        for (int forwarding = 0; forwarding <= 1; forwarding++)
                            for (int denied = 0; denied <= 1; denied++) {
                                clear(args->ctx); release_uid_cache(); uid_test_clock = 10000;
                                running.fwd53 = forwarding; args->ctx->sdk = legacy ? 28 : 29;
                                uint16_t sport = htons(12345), dport = htons(dns ? 53 : port);
                                memcpy(packet + off, &sport, 2); memcpy(packet + off + 2, &dport, 2);
                                packet_checksums(packet, length, off, protocol);
                                int known_protocol = cross_protocol
                                        ? (protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP) : protocol;
                                _Alignas(8) uint8_t known_packet[128];
                                size_t known_length = checksum_packet(known_packet, version, known_protocol, 0);
                                memcpy(known_packet + off, &sport, 2);
                                memcpy(known_packet + off + 2, &dport, 2);
                                packet_checksums(known_packet, known_length, off, known_protocol);
                                char table[256]; blocked_uid_lookup(version, legacy, 12345, table, sizeof(table));
                                deny_address = 1; // A denied lookup still provides authoritative evidence.
                                handle_ip(&running, known_packet, known_length, -1, 0, SESSION_MAX);
                                assert(policy_uid == 12345);
                                sport = htons(12346); memcpy(packet + off, &sport, 2);
                                packet_checksums(packet, length, off, protocol);
                                blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                                uid_test_clock = 14000; deny_address = denied;
                                int checks = policy_checks, calls = protocol_calls(protocol);
                                handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                                assert(policy_checks == checks + 1 && policy_uid == 12345);
                                assert(protocol_calls(protocol) == calls + (protocol == IPPROTO_TCP || !denied));
                                if (protocol == IPPROTO_TCP || !denied)
                                    assert(captured_owner.uid == 12345 && captured_owner.inferred);
                                if (protocol == IPPROTO_TCP) assert(tcp_last_allowed == !denied);
                                uid_test_clock = 15000;
                                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, dns ? 53 : port) == -1);
                                sport = htons(12347); memcpy(packet + off, &sport, 2);
                                packet_checksums(packet, length, off, protocol);
                                deny_address = 0;
                                for (int known_required = 0; known_required <= 1; known_required++) {
                                    policy_requires_known_uid = known_required;
                                    checks = policy_checks; calls = protocol_calls(protocol);
                                    handle_ip(&running, packet, length, -1, 0, SESSION_MAX);
                                    assert(policy_checks == checks + 1 && policy_uid == -1);
                                    assert(protocol_calls(protocol) == calls + (protocol == IPPROTO_TCP || !known_required));
                                    if (protocol == IPPROTO_TCP) assert(tcp_last_allowed == !known_required);
                                }
                                policy_requires_known_uid = true;
                                uint8_t output[2048];
                                while (is_readable(tunnel[0])) assert(read(tunnel[0], output, sizeof(output)) > 0);
                                clear(args->ctx); release_uid_cache();
                                assert(allocations == 0 && sockets == 0 && string_refs == 0);
                            }
                capture_packets = 0; policy_requires_known_uid = false; deny_address = 0;
                close(tunnel[0]); close(tunnel[1]);
                continue;
            }
            record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
            assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
            if (kind == 0) {
                uint8_t other[16] = {0}; memcpy(other, source, version == 4 ? 4 : 16); other[0] ^= 1;
                assert(infer_uid_destination(args->ctx, version, protocol, other, dest, port) == -1);
                memcpy(other, dest, version == 4 ? 4 : 16); other[0] ^= 1;
                assert(infer_uid_destination(args->ctx, version, protocol, source, other, port) == -1);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port + 1) == -1);
                assert(infer_uid_destination(args->ctx, version, protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP, source, dest, port) == 12345);
                int other_protocol = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
                assert(infer_uid_destination(args->ctx, version, other_protocol, other, dest, port) == -1);
                assert(infer_uid_destination(args->ctx, version, other_protocol, source, other, port) == -1);
                assert(infer_uid_destination(args->ctx, version, other_protocol, source, dest, port + 1) == -1);
                assert(infer_uid_destination(args->ctx, version, IPPROTO_ICMP, source, dest, port) == -1);
                uid_test_clock = 14000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
                uid_test_clock = 14999;
                for (int i = 0; i < 64; i++) assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
                assert(args->ctx->uid_observations.count == 1);
                uid_test_clock++;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
            } else if (kind == 8) {
                int saved_uid = own_uid;
                own_uid = 12345;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                own_uid = saved_uid;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
            } else if (kind == 6) {
                uint8_t other[16] = {0};
                memcpy(other, dest, version == 4 ? 4 : 16);
                size_t byte = version == 4 ? 3 : 15;
                for (unsigned int i = 1; i < UID_OBSERVATIONS; i++) {
                    other[byte] = (uint8_t) (((const uint8_t *) dest)[byte] + i);
                    record_uid_observation(args->ctx, version, protocol, source, other, port, 12345);
                }
                uid_test_clock = 10500;
                memory_comparisons = 0; count_memory_comparisons = true;
                for (int i = 0; i < 64; i++)
                    record_uid_observation(args->ctx, version, protocol, source, other, port, 12345);
                count_memory_comparisons = false;
                assert(memory_comparisons == 128); // Only the two endpoint checks per refresh.
                assert(args->ctx->uid_observations.count == UID_OBSERVATIONS);
                uid_test_clock = 10600;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                // A match found by the ordinary scan must become the newest slot too.
                memory_comparisons = 0; count_memory_comparisons = true;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                count_memory_comparisons = false;
                assert(memory_comparisons == 2);
                uid_test_clock = 10650;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                uid_test_clock = 11000;
                for (int i = 0; i < 64; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                uid_test_clock = 15000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                uid_test_clock = 15600;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                uid_test_clock = 16000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                // Keep scanning after a reusable slot: a later exact key wins.
                clear(args->ctx); uid_test_clock = 20000;
                for (unsigned int i = 0; i < 4; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                struct uid_observations *cache = &args->ctx->uid_observations;
                cache->entry[0].expires = 21000;
                cache->entry[1].expires = 20500; // Older, but the first free slot still wins.
                cache->entry[2].expires = 24000;
                uid_test_clock = 22000;
                record_uid_observation(args->ctx, version, protocol, source, dest, 10002, 12345);
                assert(cache->count == 4 && cache->lost_count == 0 && cache->last == 2);
                assert(cache->entry[2].expires == 27000);
                assert(cache->entry[0].expires == 21000 && cache->entry[1].expires == 20500);
                record_uid_observation(args->ctx, version, protocol, source, dest, 30000, 12345);
                assert(cache->last == 0 && cache->entry[0].port == 30000);
                assert(cache->entry[1].expires == 20500 && cache->lost_count == 0);
                record_uid_observation(args->ctx, version, protocol, source, dest, 30001, 12345);
                assert(cache->last == 1 && cache->entry[1].port == 30001 && cache->lost_count == 0);
                // Full caches still evict the oldest live entry with its original loss deadline.
                cache->count = UID_OBSERVATIONS;
                for (size_t i = 4; i < cache->count; i++) {
                    cache->entry[i] = cache->entry[0];
                    cache->entry[i].port = (uint16_t) (10000 + i);
                }
                record_uid_observation(args->ctx, version, protocol, source, dest, 30002, 12345);
                assert(cache->last == 3 && cache->entry[3].port == 30002 && cache->lost_count == 1);
                assert(cache->lost[0].port == 10003 && cache->lost[0].expires == 25000);
            } else if (kind == 7) {
                for (unsigned int i = 1; i < UID_OBSERVATIONS; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                uid_test_clock = 10500;
                record_uid_observation(args->ctx, version, protocol, source, dest, 40000, 23456);
                assert(args->ctx->uid_observations.lost[0].expires == 15000);
                uid_test_clock = 15500;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                assert(args->ctx->uid_observations.count == 0 && args->ctx->uid_observations.lost_count == 0 &&
                       args->ctx->uid_observations.incomplete_until == 0);
                assert(args->ctx->uid_observations.entry[20].expires == 15000); // Storage was not cleared.
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                assert(args->ctx->uid_observations.count == 1);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                clear(args->ctx); uid_test_clock = 20000;
                for (unsigned int i = 0; i < UID_OBSERVATIONS; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                // A write after idle must reset the count without requiring an inference first.
                uid_test_clock = 25000;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                assert(args->ctx->uid_observations.count == 1 && args->ctx->uid_observations.incomplete_until == 0);
                assert(args->ctx->uid_observations.entry[20].expires == 25000);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, 10020) == -1);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
            } else if (kind == 5) {
                // Suspend advances boot time while the event-loop clock stays still.
                uid_test_clock = 14999;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
                uid_test_clock = 15000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                assert(get_ms() == 10000);
                clear(args->ctx); uid_test_clock = 10000;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                for (unsigned int i = 1; i < UID_OBSERVATIONS; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                uid_test_clock = 10500;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                uid_test_clock = 14999;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                uid_test_clock = 15000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                uid_test_clock = 15500;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 12345);
                assert(get_ms() == 10000);
                uid_test_clock = 10000;
            } else if (kind == 1) {
                uid_test_clock = 10500;
                int other_protocol = protocol == IPPROTO_TCP ? IPPROTO_UDP : IPPROTO_TCP;
                record_uid_observation(args->ctx, version, other_protocol, source, dest, port, 23456);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                assert(infer_uid_destination(args->ctx, version, other_protocol, source, dest, port) == -1);
                // Refreshing the other transport must not refresh the older owner's evidence.
                uid_test_clock = 14999;
                record_uid_observation(args->ctx, version, other_protocol, source, dest, port, 23456);
                assert(args->ctx->uid_observations.count == 2);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                uid_test_clock = 15000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                uid_test_clock = 19999;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                const jint excluded[] = {0, 1000, (jint) getuid()};
                for (size_t i = 0; i < sizeof(excluded) / sizeof(*excluded); i++) {
                    clear(args->ctx);
                    record_uid_observation(args->ctx, version, other_protocol, source, dest, port, excluded[i]);
                    assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                    record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                    assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                }
            } else if (kind == 2) {
                test_uid_selective_eviction(args, version, protocol);
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                for (unsigned int i = 1; i < UID_OBSERVATIONS; i++)
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                uid_test_clock = 10500;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                assert(args->ctx->uid_observations.lost[0].expires == 15000);
                uid_test_clock = 14999;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                assert(args->ctx->uid_observations.count == UID_OBSERVATIONS);
                assert(args->ctx->uid_observations.lost[0].expires == 15000);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                uid_test_clock++;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                clear(args->ctx); uid_test_clock = 10000;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                for (unsigned int i = 1; i < UID_OBSERVATIONS; i++) {
                    uid_test_clock = 10000 + i;
                    record_uid_observation(args->ctx, version, protocol, source, dest, (uint16_t) (10000 + i), 12345);
                }
                uid_test_clock = 11000;
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 23456);
                uid_test_clock++;
                record_uid_observation(args->ctx, version, protocol, source, dest, 60000, 23456);
                assert(args->ctx->uid_observations.lost[1].expires == 15001);
                uid_test_clock = 15000;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
                uid_test_clock++;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == 23456);
            } else {
                struct context isolated = {0};
                assert(infer_uid_destination(&isolated, version, protocol, source, dest, port) == -1);
                uid_clock_failure = true;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                uid_clock_failure = false;
                assert(args->ctx->uid_observations.count == 0);
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                uid_test_clock--;
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
                record_uid_observation(args->ctx, version, protocol, source, dest, port, 12345);
                clear(args->ctx);
                assert(infer_uid_destination(args->ctx, version, protocol, source, dest, port) == -1);
            }
            clear(args->ctx);
            assert(allocations == 0 && sockets == 0 && string_refs == 0);
        }
    test_monotonic_enabled = false;
    uid_clock_enabled = false;
#endif
}

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
                        // Active owners restore exactly; a recent authoritative
                        // observation also supplies the denied destination's owner.
                        ng_free(uid_cache, __FILE__, __LINE__);
                        uid_cache = NULL; uid_cache_size = uid_cache_capacity = 0;
                        blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                        handle_ip(&running, packet, off + 20, epoll_fd, 1, SESSION_MAX);
                        assert(policy_checks == 2 && args->ctx->ng_session == s && s->socket == socket);
                        assert(policy_uid == initial);
                        assert(destination_calls == (denied ? 0 : 2));
                        assert(s->udp.uid == initial);
                        if (denied) blocked_uid_unreachable(tunnel[0], version);
                    } else if (kind == 3) {
                        // Exercise blocked-record metadata without recent destination
                        // evidence; the destination fallback has separate coverage.
                        memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
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
                            memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
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
                        memset(&args->ctx->uid_observations, 0, sizeof(args->ctx->uid_observations));
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
                    // Replacement must not restart its timer; a denied packet also checks the ICMP budget.
                    assert(test_monotonic_calls == clocks + 1 + denied);
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

void test_tcp_syn_capacity(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    enum { UNKNOWN, KNOWN, ROOT, CLOSING, CLOSED, SOCKETLESS, OTHER_SEQUENCE, OTHER_PORT,
           OTHER_DPORT, OTHER_SOURCE, OTHER_DEST, OTHER_FAMILY, SHORT_HEADER, LONG_HEADER,
           SYN_ACK, SYN_FIN, SYN_RST, SYN_URG, NEW_FLOW };
    int upstream[2], tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, upstream) == 0);
    assert(socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0, tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    for (int version = 4; version <= 6; version += 2) {
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            for (int extra = 0; extra <= 1; extra++) {
                for (int scenario = UNKNOWN; scenario <= NEW_FLOW; scenario++) {
                    _Alignas(8) uint8_t packet[128];
                    size_t off = ip_header(packet, version, IPPROTO_TCP, 20,
                                           version == 4 ? "203.0.113.2" : "2001:db8::2");
                    struct tcphdr *tcp = (void *) (packet + off);
                    tcp->source = htons(12345); tcp->dest = htons(443);
                    tcp->doff = 5; tcp->syn = 1; tcp->seq = htonl(100);
                    struct ng_session session = {.protocol = IPPROTO_TCP, .socket = upstream[0]};
                    session.tcp.version = version; session.tcp.state = TCP_ESTABLISHED;
                    session.tcp.uid = scenario == ROOT ? 0 : scenario == KNOWN ? 23456 : -1;
                    session.tcp.source = tcp->source; session.tcp.dest = tcp->dest;
                    session.tcp.remote_start = 100; session.tcp.remote_seq = 101;
                    session.tcp.local_seq = session.tcp.acked = 2000;
                    memcpy(&session.tcp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
                    memcpy(&session.tcp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
                    if (scenario == CLOSING) session.tcp.state = TCP_CLOSING;
                    if (scenario == CLOSED) session.tcp.state = TCP_CLOSE;
                    if (scenario == SOCKETLESS) session.socket = -1;
                    if (scenario == OTHER_SEQUENCE) session.tcp.remote_start++;
                    if (scenario == OTHER_PORT) session.tcp.source++;
                    if (scenario == OTHER_DPORT) session.tcp.dest++;
                    if (scenario == OTHER_SOURCE) ((uint8_t *) &session.tcp.saddr)[0] ^= 1;
                    if (scenario == OTHER_DEST) ((uint8_t *) &session.tcp.daddr)[0] ^= 1;
                    if (scenario == OTHER_FAMILY) session.tcp.version = version == 4 ? 6 : 4;
                    if (scenario == SHORT_HEADER) tcp->doff = 4;
                    if (scenario == LONG_HEADER) tcp->doff = 6;
                    if (scenario == SYN_ACK) tcp->ack = 1;
                    if (scenario == SYN_FIN) tcp->fin = 1;
                    if (scenario == SYN_RST) tcp->rst = 1;
                    if (scenario == SYN_URG) tcp->urg = 1;
                    packet_checksums(packet, off + 20, off, IPPROTO_TCP);
                    struct ng_session *head = scenario == NEW_FLOW ? NULL : &session;
                    set_test_sessions(args->ctx, head);
                    char table[256];
                    blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
                    deny_address = 0;
                    int queries = uid_lookups, checks = policy_checks, files = file_closes;
                    bool existing = scenario <= ROOT;
                    handle_ip(&running, packet, off + 20, -1, SESSION_MAX + extra, SESSION_MAX);
                    assert(args->ctx->ng_session == head && session.next == NULL);
                    assert(uid_lookups == queries + (existing && !legacy));
                    assert(policy_checks == checks + existing);
                    if (!existing) assert(file_closes == files);
                    assert(session.tcp.uid == (scenario == ROOT ? 0 : scenario == KNOWN ? 23456 : -1));
                    if (existing) assert(policy_uid == session.tcp.uid);
                    assert(session.socket == (scenario == SOCKETLESS ? -1 : upstream[0]));
                    assert(session.tcp.state == (scenario == CLOSING ? TCP_CLOSING :
                                               scenario == CLOSED ? TCP_CLOSE : TCP_ESTABLISHED));
                    assert(!is_readable(tunnel[0]) && !is_readable(upstream[1]));
                    set_test_sessions(args->ctx, NULL);
                    release_uid_cache();
                    assert(allocations == 0 && sockets == 0);
                }
            }
        }
    }
    args->ctx->sdk = 29;
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
                    // The 16 error replies check their budget, without retrying ownership.
                    assert(test_monotonic_calls == clock_reads + 16 && old->udp.uid == owner);
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

void test_udp_inferred_uid_failure(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    int tunnel[2]; assert(pipe(tunnel) == 0);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC); assert(epoll_fd >= 0);
    struct arguments running = *args; running.tun = tunnel[1];
    real_network = 1;
    test_monotonic_enabled = true;
    uid_clock_enabled = true;
    policy_requires_known_uid = true;
    for (int version = 4; version <= 6; version += 2)
        for (int legacy = 0; legacy <= 1; legacy++) {
            args->ctx->sdk = legacy ? 28 : 29;
            _Alignas(8) uint8_t packet[128];
            const char *dest = version == 4 ? "203.0.113.2" : "2001:db8::2";
            size_t off = ip_header(packet, version, IPPROTO_UDP, 9, dest);
            struct udphdr *udp = (void *) (packet + off);
            udp->source = htons(12345); udp->dest = htons(5000); udp->len = htons(9);
            packet[off + 8] = 42;
            packet_checksums(packet, off + 9, off, IPPROTO_UDP);
            void *source = packet + (version == 4 ? 12 : 8);
            void *destination = packet + (version == 4 ? 16 : 24);
            uid_test_clock = test_monotonic_ms = 10000;
            record_uid_observation(args->ctx, version, IPPROTO_UDP, source, destination, 5000, 12345);
            char table[256]; blocked_uid_lookup(version, legacy, -1, table, sizeof(table));
            policy_checks = uid_lookups = 0;
            expect_destination(version, dest, 5000);
            handle_ip(&running, packet, off + 9, epoll_fd, 0, SESSION_MAX);
            struct ng_session *session = find_udp_session(&running, packet, packet + off);
            assert(session != NULL && session->udp.uid == 12345 && session->udp.uid_inferred);
            int socket = session->socket;
            assert(socket >= 0 && destination_calls == 1 && policy_uid == 12345);
            const long long deadlines[] = {11000, 13000, 17000};
            for (int i = 0; i < UDP_UID_RETRY_MAX; i++) {
                uid_test_clock = test_monotonic_ms = deadlines[i];
                handle_ip(&running, packet, off + 9, epoll_fd, 1, SESSION_MAX);
                assert(policy_uid == 12345 && policy_checks == i + 2);
                assert(find_udp_session(&running, packet, packet + off) == session);
                assert(session->socket == socket && session->udp.state == UDP_ACTIVE);
                assert(session->udp.uid == 12345 && session->udp.uid_inferred);
                assert(session->udp.uid_retries == i + 1 && destination_calls == i + 2);
                assert(sockets == 1 && allocations == 1 && !is_readable(tunnel[0]));
            }
            assert(infer_uid_destination(args->ctx, version, IPPROTO_UDP, source, destination, 5000) == -1);
            int queries = uid_lookups, closes = file_closes;
            test_monotonic_ms = 100000;
            handle_ip(&running, packet, off + 9, epoll_fd, 1, SESSION_MAX);
            assert(destination_calls == 5 && policy_checks == 4);
            assert(uid_lookups == queries && file_closes == closes);
            clear(args->ctx); release_uid_cache();
            assert(allocations == 0 && sockets == 0 && string_refs == 0 && !is_readable(tunnel[0]));
        }
    policy_requires_known_uid = false;
    uid_clock_enabled = false;
    test_monotonic_enabled = false;
    real_network = 0;
    args->ctx->sdk = 29;
    close(epoll_fd); close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_udp_uid_retry(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    bool inferred_fixture = kind == 3;
    if (inferred_fixture) kind = 1;
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
            if (inferred_fixture) { old->udp.uid = 54321; old->udp.uid_inferred = true; }

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
                if (inferred_fixture) assert(!current->udp.uid_inferred && !old->udp.uid_inferred);
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
    echo.icmp_code = 1;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    echo.icmp_code = 0;
    echo.icmp_type = version == 4 ? ICMP6_ECHO_REQUEST : ICMP_ECHO;
    assert(restore_uid(args, version, protocol, source, 1234, dest, 1234,
                       "source", "dest", "", (const uint8_t *) &echo) == -1);
    echo.icmp_type = version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST;
    assert(restore_uid(args, version, version == 4 ? IPPROTO_ICMPV6 : IPPROTO_ICMP,
                       source, 1234, dest, 1234, "source", "dest", "",
                       (const uint8_t *) &echo) == -1);
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

void test_ip_icmp_validation(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    capture_packets = 1;
    for (int version = 4; version <= 6; version += 2)
        for (int legacy = 0; legacy <= 1; legacy++)
            for (int full = 0; full <= 1; full++)
                for (int kind = 0; kind < 8; kind++) {
                    int protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
                    if (kind == 6) protocol = version == 4 ? IPPROTO_ICMPV6 : IPPROTO_ICMP;
                    _Alignas(8) uint8_t packet[128];
                    size_t length = checksum_packet(packet, version, protocol, 0);
                    size_t off = version == 4 ? 20 : 40;
                    const uint8_t types[] = {version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                                            version == 4 ? ICMP6_ECHO_REQUEST : ICMP_ECHO,
                                            version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                                            version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY,
                                            133, 255, version == 4 ? ICMP_ECHO : ICMP6_ECHO_REQUEST,
                                            version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH};
                    packet[off] = types[kind]; packet[off + 1] = kind == 2 ? 1 : 0;
                    packet_checksums(packet, length, off, protocol);
                    args->ctx->sdk = legacy ? 28 : 29;
                    fake_proc = legacy; proc_contents = proc6_contents = "header\n";
                    uid_lookup_result = 12345;
                    int queries = uid_lookups, files = file_closes, checks = policy_checks, calls = icmp_calls;
                    handle_ip(args, packet, length, -1, full ? SESSION_MAX : 0, SESSION_MAX);
                    bool accepted = kind == 0 && !full;
                    assert(uid_lookups == queries + (accepted && !legacy));
                    assert(accepted && legacy ? file_closes > files : file_closes == files);
                    assert(policy_checks == checks + accepted && icmp_calls == calls + accepted);
                    assert(args->ctx->ng_session == NULL);
                    release_uid_cache();
                    assert(allocations == 0 && sockets == 0 && string_refs == 0);
                }
    args->ctx->sdk = 29; capture_packets = fake_proc = 0;
    proc_contents = proc6_contents = NULL;
#endif
}
