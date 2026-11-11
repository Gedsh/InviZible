#include "test_support.h"

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
