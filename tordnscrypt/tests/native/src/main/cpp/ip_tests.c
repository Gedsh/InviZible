#include "test_support.h"

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
