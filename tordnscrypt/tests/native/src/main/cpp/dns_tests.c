#include "test_support.h"

void test_dns_question_counts(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint8_t data[64] = {0}, original[64];
    struct ng_session s = {.protocol = IPPROTO_UDP};
    struct arguments args = {.rcode = 3};
    data[2] = 0x81;
    for (int opcode = 0; opcode <= 5; opcode += 5) {
        data[2] = (uint8_t) (0x81 | opcode << 3);
        const uint16_t counts[] = {0, 1, 2, UINT16_MAX};
        for (size_t i = 0; i < sizeof(counts) / sizeof(*counts); i++) {
            store16(data + 4, counts[i]);
            assert(dns_has_multiple_questions(data, sizeof(data)) == (opcode == 0 && counts[i] > 1));
        }
    }
    data[2] = 0x81;
    store16(data + 4, 2);
    store16(data + 6, 1);
    // The remainder looks like a normal answer. It must never produce callbacks or rewriting.
    memcpy(original, data, sizeof(data));
    size_t len = sizeof(data);
    block_domain = 1;
    parse_dns_response(&args, &s, data, &len);
    assert(resolved_calls == 0 && allocations == 0);
    assert(len == sizeof(data) && memcmp(data, original, len) == 0);
    for (size_t short_len = 0; short_len < sizeof(struct dns_header); short_len++)
        assert(!dns_has_multiple_questions(data, short_len));
    store16(data + 4, 0);
    len = sizeof(struct dns_header);
    parse_dns_response(&args, &s, data, &len);
    assert(len == sizeof(struct dns_header) && resolved_calls == 0);
    block_domain = 0;
#endif
}

void test_dns_tcp_framing(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint8_t frames[42] = {0};
    for (int i = 0; i < 3; i++) {
        store16(frames + i * 14, 12);
        frames[i * 14 + 4] = 0x81;
        store16(frames + i * 14 + 6, i == 1 ? 0 : 1);
    }
    for (size_t split = 0; split <= sizeof(frames); split++) {
        struct tcp_session tcp = {0};
        assert(check_dns_tcp_response(&tcp, frames, split));
        assert(check_dns_tcp_response(&tcp, frames + split, sizeof(frames) - split));
        assert(tcp.dns_header_used == 0 && tcp.dns_body_remaining == 0);
    }
    // Reject the second frame, including when either prefix/header is split at any byte.
    store16(frames + 20, 2);
    for (size_t split = 0; split < 28; split++) {
        struct tcp_session tcp = {0};
        assert(check_dns_tcp_response(&tcp, frames, split));
        assert(!check_dns_tcp_response(&tcp, frames + split, sizeof(frames) - split));
    }
    struct tcp_session tcp = {0};
    uint8_t header[14] = {0}, body[256] = {0};
    store16(header, UINT16_MAX);
    header[4] = 0x81;
    store16(header + 6, 1);
    assert(check_dns_tcp_response(&tcp, header, sizeof(header)));
    size_t left = UINT16_MAX - 12;
    while (left) {
        size_t chunk = left < sizeof(body) ? left : sizeof(body);
        assert(check_dns_tcp_response(&tcp, body, chunk));
        left -= chunk;
    }
    assert(tcp.dns_header_used == 0 && tcp.dns_body_remaining == 0 && allocations == 0);
    // Zero-length frames still make progress and cannot hide a following invalid frame.
    uint8_t empty[2] = {0};
    assert(check_dns_tcp_response(&tcp, empty, sizeof(empty)));
    assert(!check_dns_tcp_response(&tcp, frames + 14, 14));
#endif
}

void test_names(void) {
    char name[DNS_QNAME_MAX + 1];
    const uint8_t plain[] = {1, 'a', 3, 'c', 'o', 'm', 0};
    assert(get_qname(plain, sizeof(plain), 0, name) == sizeof(plain));
    assert(strcmp(name, "a.com") == 0);
    const uint8_t compressed[] = {3, 'c', 'o', 'm', 0, 1, 'a', 0xC0, 0};
    assert(get_qname(compressed, sizeof(compressed), 5, name) == sizeof(compressed));
    assert(strcmp(name, "a.com") == 0);
    const uint8_t loop[] = {0xC0, 0};
    assert(get_qname(loop, sizeof(loop), 0, name) == -1 && name[0] == 0);
    const uint8_t truncated[] = {1, 'a', 0xC0};
    assert(get_qname(truncated, sizeof(truncated), 0, name) == -1 && name[0] == 0);
    uint8_t longest[257];
    for (int i = 0; i < 4; i++) {
        longest[i * 64] = 63;
        memset(longest + i * 64 + 1, 'a', 63);
    }
    longest[256] = 0;
    assert(get_qname(longest, sizeof(longest), 0, name) == -1 && name[0] == 0);
    longest[192] = 61; longest[254] = 0;
    assert(get_qname(longest, 255, 0, name) == 255 && strlen(name) == 253);
    uint32_t random = 1;
    for (int i = 0; i < 5000; i++) {
        size_t len = (size_t) i % 257;
        uint8_t *data = malloc(len + 1);
        for (size_t j = 0; j < len; j++) {
            random = random * 1664525 + 1013904223;
            data[j] = (uint8_t) (random >> 24);
        }
        memset(name, 0xA5, sizeof(name));
        int32_t end = get_qname(data, len, 0, name);
        assert(memchr(name, 0, sizeof(name)) != NULL);
        assert(end == -1 || (end > 0 && (size_t) end <= len));
        free(data);
    }
}

void test_dns(const struct arguments *args) {
    struct ng_session session = {.protocol = IPPROTO_UDP};
    session.udp.version = 4;
    uint8_t response[] = {
        0, 0, 0x80, 0, 0, 1, 0, 1, 0, 0, 0, 0,
        1, 'a', 0, 0, 1, 0, 1,
        0xC0, 12, 0, 5, 0, 1, 0, 0, 0, 0, 0, 2, 0xC0, 12
    };
    size_t len = sizeof(response);
    resolved_calls = 0;
    parse_dns_response(args, &session, response, &len);
    assert(resolved_calls == 1);
    response[31] = 1; response[32] = 'b'; // Unterminated CNAME.
    resolved_calls = 0;
    parse_dns_response(args, &session, response, &len);
    assert(resolved_calls == 0);
    response[31] = 0xC0; response[32] = 12;
    response[30] = 1; // CNAME crosses the advertised RDATA boundary.
    parse_dns_response(args, &session, response, &len);
    assert(resolved_calls == 0);
}

static size_t dns_answer(uint8_t *data, uint16_t type) {
    memset(data, 0, 512);
    store16(data, 1234); data[2] = 0x81; data[3] = 0x80;
    store16(data + 4, 1); store16(data + 6, 1);
    data[12] = 4; memcpy(data + 13, "test", 4);
    store16(data + 18, 1); store16(data + 20, 1);
    data[22] = 0xC0; data[23] = 12;
    store16(data + 24, type); store16(data + 26, 1); data[31] = 60;
    size_t len = (type == DNS_QTYPE_A ? 4 : type == DNS_QTYPE_AAAA ? 16 : 2);
    store16(data + 32, len);
    if (type == DNS_QTYPE_A)
        inet_pton(AF_INET, "192.0.2.10", data + 34);
    else if (type == DNS_QTYPE_AAAA)
        inet_pton(AF_INET6, "2001:db8::10", data + 34);
    else if (type == DNS_QTYPE_CNAME) {
        data[34] = 0xC0; data[35] = 12;
    }
    return 34 + len;
}

size_t test_uid_dns_response(uint8_t *data, int version, bool alias) {
    dns_answer(data, version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
    memcpy(data + 13, "TeSt", 4);
    store16(data + 18, version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
    store16(data + 6, alias ? 3 : 2);
    size_t off = 22;
    if (alias) {
        store16(data + off + 2, DNS_QTYPE_CNAME); store16(data + off + 8, 10);
        store16(data + off + 10, 6);
        data[off + 12] = 4; memcpy(data + off + 13, "EdGe", 4); data[off + 17] = 0;
        off += 18;
    }
    for (int i = 0; i < 2; i++) {
        size_t size = version == 4 ? 4 : 16;
        memset(data + off, 0, 12 + size);
        data[off] = 0xC0; data[off + 1] = alias ? 34 : 12;
        store16(data + off + 2, version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
        store16(data + off + 4, DNS_QCLASS_IN); store16(data + off + 8, 30);
        store16(data + off + 10, (uint16_t) size);
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                         version == 4 ? (i ? "192.0.2.11" : "192.0.2.10")
                                      : (i ? "2001:db8::11" : "2001:db8::10"), data + off + 12) == 1);
        off += 12 + size;
    }
    return off;
}

void test_dns_uid_answer_order(struct arguments *args) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int version = 4; version <= 6; version += 2)
        for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
            for (int hops = 1; hops <= 2; hops++)
                for (int reverse = 0; reverse <= 1; reverse++)
                    for (int rotation = 0; rotation < hops + 3; rotation++) {
                        clear(args->ctx); set_uid_test_clock(10000);
                        uint8_t data[512], addresses[3][16], source[16] = {0};
                        dns_answer(data, version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
                        store16(data + 18, version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
                        store16(data + 6, (uint16_t) (hops + 3));
                        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                         version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                        for (int i = 0; i < 3; i++) {
                            char address[32];
                            snprintf(address, sizeof(address), version == 4 ? "192.0.2.%d" : "2001:db8::%d", 10 + i);
                            assert(inet_pton(version == 4 ? AF_INET : AF_INET6, address, addresses[i]) == 1);
                        }
                        size_t length = 22;
                        for (int r = 0; r < hops + 3; r++) {
                            int record = (rotation + (reverse ? hops + 2 - r : r)) % (hops + 3);
                            bool alias = record < hops;
                            const char *owner = alias ? (record == 0 ? "test" : "next") : "edge";
                            data[length++] = 4; memcpy(data + length, owner, 4); length += 4; data[length++] = 0;
                            memset(data + length, 0, 10);
                            store16(data + length, alias ? DNS_QTYPE_CNAME : version == 4 ? DNS_QTYPE_A : DNS_QTYPE_AAAA);
                            store16(data + length + 2, DNS_QCLASS_IN);
                            store16(data + length + 6, alias ? (record == 0 ? 10 : 5) : 30);
                            size_t size = alias ? 6 : version == 4 ? 4 : 16;
                            store16(data + length + 8, (uint16_t) size); length += 10;
                            if (alias) {
                                data[length] = 4;
                                memcpy(data + length + 1, record + 1 == hops ? "edge" : "next", 4);
                                data[length + 5] = 0;
                            } else memcpy(data + length, addresses[record - hops], size);
                            length += size;
                        }
                        struct ng_session session = {.protocol = protocol};
                        record_uid_observation(args->ctx, version, protocol, source, addresses[0], 443, 12345);
                        parse_dns_response(args, &session, data, &length);
                        // Every address must participate, including those preceding its alias.
                        assert(infer_uid_destination(args->ctx, version, protocol, source, addresses[1], 443) == 12345);
                        record_uid_observation(args->ctx, version, protocol, source, addresses[2], 443, 23456);
                        assert(infer_uid_destination(args->ctx, version, protocol, source, addresses[1], 443) == -1);
                        assert(args->ctx->uid_dns_mappings.count == 3);
                        for (size_t i = 0; i < 3; i++)
                            assert(args->ctx->uid_dns_mappings.entry[i].expires == (hops == 1 ? 20000 : 15000));
                        clear(args->ctx);
                        assert(allocations == 0 && sockets == 0 && string_refs == 0);
                    }
    set_uid_test_clock(-1);
#endif
}

void test_dns_uid_mappings(struct arguments *args, int kind) {
#if defined(INVIZIBLE_PACKET_OWNER) && !defined(INVIZIBLE_BASELINE_TESTS)
    for (int protocol = IPPROTO_TCP; protocol <= IPPROTO_UDP; protocol += IPPROTO_UDP - IPPROTO_TCP)
        for (int version = 4; version <= 6; version += 2)
            for (int alias = 0; alias <= 1; alias++) {
                uint8_t data[2048];
                struct ng_session session = {.protocol = protocol};
                for (int scenario = 0; scenario < (kind == 1 ? 9 : 1); scenario++) {
                    clear(args->ctx); set_uid_test_clock(10000);
                    size_t length = test_uid_dns_response(data, version, alias);
                    if (kind == 1) {
                        if (scenario == 0) data[2] |= 2; // Truncated.
                        if (scenario == 1) data[3] |= 3; // Failed response.
                        if (scenario == 2) length--; // A later address is incomplete.
                        if (scenario == 3) data[length++] = 0; // Unparsed trailing bytes.
                        if (scenario == 4) store16(data + 20, 3); // Non-IN question.
                        if (scenario == 5) data[14] = 0; // Binary label must not alias a prefix.
                        if (scenario == 6) data[14] = '.'; // Embedded label separator.
                        if (scenario == 7) block_domain = 1;
                        if (scenario == 8) { store16(data + 10, 1); data[length++] = 0; } // Truncated additional RR.
                    }
                    parse_dns_response(args, &session, data, &length);
                    assert(args->ctx->uid_observations.count == 0); // DNS sessions never supply an owner.
                    if (kind == 1) assert(args->ctx->uid_dns_mappings.count == 0);
                    else {
                        assert(args->ctx->uid_dns_mappings.count == 2);
                        for (size_t i = 0; i < 2; i++) {
                            const struct uid_dns_mapping *entry = &args->ctx->uid_dns_mappings.entry[i];
                            assert(strcmp(entry->domain, "test") == 0 && entry->version == version);
                            assert(entry->expires == (alias ? 20000 : 40000));
                        }
                        if (kind == 2) {
                            // Zero address/CNAME TTL must revoke that exact cached association.
                            length = test_uid_dns_response(data, version, alias);
                            store16(data + 30, 0);
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.entry[0].expires == 10000);
                            if (alias) assert(args->ctx->uid_dns_mappings.entry[1].expires == 10000);
                            // A single mapping's TTL is capped even for an enormous wire TTL.
                            clear(args->ctx);
                            length = test_uid_dns_response(data, version, false);
                            memset(data + 28, 0xFF, 4);
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.entry[0].expires == 70000);
                            // Duplicate addresses use the shortest TTL, regardless of order.
                            clear(args->ctx);
                            length = test_uid_dns_response(data, version, false);
                            size_t size = version == 4 ? 4 : 16, stride = 12 + size;
                            memcpy(data + 34 + stride, data + 34, size);
                            store16(data + 30 + stride, 5);
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.count == 1);
                            assert(args->ctx->uid_dns_mappings.entry[0].expires == 15000);
                            // A valid but unrelated owner name must not inherit the question.
                            clear(args->ctx);
                            length = test_uid_dns_response(data, version, false);
                            data[23] = 17; // The root name at the question's terminator.
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.count == 1);
                            assert(args->ctx->uid_dns_mappings.entry[0].address[version == 4 ? 3 : 15] == (version == 4 ? 11 : 0x11));
                            // A CNAME cycle is not evidence, even with well-formed wire names.
                            clear(args->ctx);
                            length = test_uid_dns_response(data, version, true);
                            store16(data + 6, 2); store16(data + 42, DNS_QTYPE_CNAME);
                            store16(data + 50, 2); data[52] = 0xC0; data[53] = 12; length = 54;
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.count == 0);
                            // No partial map set may be published when the batch is too large.
                            clear(args->ctx);
                            length = test_uid_dns_response(data, version, false);
                            store16(data + 6, UID_DNS_ANSWERS + 1);
                            for (unsigned int i = 2; i <= UID_DNS_ANSWERS; i++) {
                                memcpy(data + length, data + 22, stride);
                                length += stride;
                            }
                            parse_dns_response(args, &session, data, &length);
                            assert(args->ctx->uid_dns_mappings.count == 0);
                            assert(args->ctx->uid_dns_mappings.lost_count == 2);
                            assert(args->ctx->uid_dns_mappings.lost[0].expires == 40000);
                            // Overflow must neither disable unrelated names nor hide a
                            // shared address appearing beyond the first 32 answers.
                            clear(args->ctx);
                            struct uid_dns_answer safe[2] = {{.version = version, .ttl = 30}, {.version = version, .ttl = 30}};
                            uint8_t source[16] = {0};
                            assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                             version == 4 ? "10.0.0.2" : "2001:db8::1", source) == 1);
                            for (int i = 0; i < 2; i++)
                                assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                                                 version == 4 ? (i ? "198.51.100.11" : "198.51.100.10")
                                                              : (i ? "2001:db8:1::11" : "2001:db8:1::10"), safe[i].address) == 1);
                            record_uid_dns_answers(args->ctx, "safe", safe, 2);
                            struct uid_dns_answer shared[2] = {safe[0], safe[1]};
                            shared[1].address[version == 4 ? 3 : 15] = 99;
                            record_uid_dns_answers(args->ctx, "shared", shared, 2);
                            record_uid_observation(args->ctx, version, protocol, source, safe[0].address, 443, 12345);
                            length = test_uid_dns_response(data, version, false);
                            store16(data + 6, UID_DNS_ANSWERS + 1);
                            for (unsigned int i = 2; i <= UID_DNS_ANSWERS; i++) {
                                memcpy(data + length, data + 22, stride);
                                if (i == UID_DNS_ANSWERS) memcpy(data + length + 12, shared[1].address, size);
                                length += stride;
                            }
                            parse_dns_response(args, &session, data, &length);
                            assert(infer_uid_destination(args->ctx, version, protocol, source, safe[1].address, 443) == 12345);
                            assert(infer_uid_destination(args->ctx, version, protocol, source, shared[1].address, 443) == -1);
                            assert(args->ctx->uid_dns_mappings.count == 4 && args->ctx->uid_dns_mappings.lost_count == 3);
                        }
                    }
                    block_domain = 0;
                    clear(args->ctx);
                    assert(allocations == 0 && sockets == 0 && string_refs == 0);
                }
            }
    set_uid_test_clock(-1);
#endif
}

void test_dns_allocation_failure(const struct arguments *args) {
    struct ng_session session = {.protocol = IPPROTO_UDP};
    session.udp.version = 4;
    uint8_t response[512];
    size_t length = dns_answer(response, DNS_QTYPE_HINFO);
    fail_alloc_tag = "hinfo";
    fail_alloc_match = 1;
    parse_dns_response(args, &session, response, &length);
    assert(fail_alloc_match == 0);
    assert(allocations == 0 && resolved_calls == 0);
    fail_alloc_tag = NULL;
}

void dns_flows(void) {
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .rcode = 3};
    struct ng_session s = {.protocol = IPPROTO_UDP};
    s.udp.version = 4;
    const uint16_t types[] = {DNS_QTYPE_A, DNS_QTYPE_AAAA, DNS_QTYPE_CNAME, DNS_QTYPE_HTTPS};
    trace_dns = 1;
    for (size_t i = 0; i < sizeof(types) / sizeof(*types); i++) {
        uint8_t data[512], original[512];
        size_t len = dns_answer(data, types[i]), before = len;
        memcpy(original, data, len);
        int callbacks = resolved_calls;
        parse_dns_response(&args, &s, data, &len);
        if (types[i] == DNS_QTYPE_HTTPS) {
            assert(len == 22 && load16(data + 6) == 0 && (data[3] & 15) == 3);
            assert(resolved_calls == callbacks);
        } else {
            assert(len == before && memcmp(data, original, len) == 0);
            assert(resolved_calls == callbacks + 1);
        }
        printf("DNS type=%u response=%08x\n", types[i], fingerprint(data, len));
        cases++;
    }
    uint8_t data[512];
    size_t len = dns_answer(data, DNS_QTYPE_A);
    block_domain = 1;
    parse_dns_response(&args, &s, data, &len);
    assert(len == 22 && load16(data + 6) == 0 && (data[3] & 15) == 3);
    printf("DNS blocked response=%08x\n", fingerprint(data, len));
    block_domain = 0;
    len = dns_answer(data, DNS_QTYPE_A);
    store16(data + 6, 0); data[3] = 0x83; len = 22;
    parse_dns_response(&args, &s, data, &len);
    assert(len == 22 && (data[3] & 15) == 3);
    printf("DNS NXDOMAIN response=%08x\n", fingerprint(data, len));
    trace_dns = 0;
    cases += 2;
}
