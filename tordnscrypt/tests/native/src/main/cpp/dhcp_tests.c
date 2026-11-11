#include "test_support.h"

void test_dhcp_allocation_failure(const struct arguments *args) {
    struct udp_session udp = {.version = 4};
    dhcp_packet request = {.opcode = 1, .htype = 1, .hlen = 6,
                          .option_format = htonl(DHCP_OPTION_MAGIC_NUMBER)};
    fail_alloc_tag = "dhcp";
    fail_alloc_match = 1;
    assert(check_dhcp(args, &udp, (const uint8_t *) &request, sizeof(request)) == -1);
    assert(fail_alloc_match == 0);
    assert(allocations == 0);
    fail_alloc_tag = NULL;
}

void test_dhcp_response_bounds(const struct arguments *args) {
    struct udp_session udp = {.version = 4, .uid = own_uid};
    dhcp_packet request = {.opcode = 1, .htype = 1, .hlen = 6,
                          .option_format = htonl(DHCP_OPTION_MAGIC_NUMBER)};
    capture_dhcp = 1;
    assert(check_dhcp(args, &udp, (const uint8_t *) &request, sizeof(request)) == 0);
    assert(capture_dhcp == 0 && allocations == 0);
}

void dhcp_offer(void) {
    // The old response builder writes outside its allocation, so the baseline
    // comparison cannot exercise it. Assert the repaired wire options instead.
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .tun = -1};
    struct udp_session udp = {.version = 4};
    dhcp_packet request = {.opcode = 1, .htype = 1, .hlen = 6,
                          .option_format = htonl(DHCP_OPTION_MAGIC_NUMBER)};
    capture_dhcp = 1;
    assert(check_dhcp(&args, &udp, (void *) &request, sizeof(request)) == 0);
    assert(capture_dhcp == 0);
    puts("DHCP offer/options passed"); cases++;
}
