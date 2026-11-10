#include "test_support.h"

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
