#include "test_support.h"

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
                bool existing = kind == 0 ? present : !running.fwd53;
                bool dropped = full && !existing;
                assert(udp_lookups == (kind == 0 ? 1 : 0));
                assert(uid_lookups == (!dropped && !existing));
                assert(policy_checks == (!dropped && !existing));
                assert(udp_calls == !dropped);
                assert(udp_lookup_supplied == (!dropped && kind == 0));
                assert(udp_lookup_session == (!dropped && kind == 0 && present ? &target : NULL));
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
            assert(uid_lookups == 1 && policy_checks == 1 && udp_calls == 0 && udp_lookups == 0);
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

static jint lookup_uid(int version, int protocol, uint16_t sport, long now) {
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
    assert(allocations == 0);
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
