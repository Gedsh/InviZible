#include "test_support.h"

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
