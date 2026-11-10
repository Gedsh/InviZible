#include "test_support.h"

void compatibility_suite(void) {
    for (int version = 4; version <= 6; version += 2) {
        udp_flow(version, 0, 0);
        udp_flow(version, 1, 0);
        tcp_flow(version);
        icmp_packets(version);
    }
    socks_flow(4, 0, 0);
    socks_flow(4, 1, 0);
    socks_flow(6, 1, 1);
    dns_flows();
    assert(allocations == 0 && sockets == 0 && string_refs == 0);
    printf("Functional regressions passed: %u scenarios.\n", cases);
}
