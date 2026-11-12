#include "test_support.h"

static void assert_tcp_queue_totals(const struct tcp_session *tcp) {
#ifndef INVIZIBLE_BASELINE_TESTS
    uint32_t bytes = 0, unsent = 0;
    unsigned int segments = 0;
    for (const struct segment *q = tcp->forward; q != NULL; q = q->next) {
        assert(q->sent <= q->len);
        bytes += q->len;
        unsent += q->len - q->sent;
        segments++;
    }
    assert(tcp->queue_bytes == bytes && tcp->queue_unsent == unsent);
    assert(tcp->queue_segments == segments);
#endif
}

void test_tcp_capacity_reuse(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    real_network = 1;
    for (int version = 4; version <= 6; version += 2) {
        int stream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
        assert(socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0, tunnel) == 0);
        struct arguments running = *args;
        running.tun = tunnel[1];
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = stream[0]};
        s.tcp.version = version; s.tcp.uid = own_uid; s.tcp.time = time(NULL);
        s.tcp.state = TCP_ESTABLISHED; s.tcp.mss = 16;
        s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.remote_seq = 1000;
        s.tcp.recv_window = s.tcp.send_window = 65535;
        uint8_t data[40], output[256];
        for (size_t i = 0; i < sizeof(data); i++) data[i] = (uint8_t) i;
        assert(send(stream[1], data, sizeof(data), 0) == sizeof(data));
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        struct tcp_socket_capacity capacity = {0};
        tcp_sendbuf_queries = tcp_outq_queries = 0;
        size_t offset = 0, iplen = version == 4 ? 20 : 40;
        for (int turn = 0; turn < 3; turn++) {
            check_tcp_socket_with_capacity(&running, &event, -1, &capacity);
            size_t length = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
            size_t payload = length - iplen - 20;
            assert(payload == (turn == 2 ? 8 : 16));
            assert(memcmp(output + iplen + 20, data + offset, payload) == 0);
            offset += payload;
            assert(tcp_sendbuf_queries == 1 && tcp_outq_queries == 1);
        }
        assert(offset == sizeof(data));

        // A new event must see a changed socket buffer, not the previous sample.
        int small = 4096;
        assert(setsockopt(stream[0], SOL_SOCKET, SO_SNDBUF, &small, sizeof(small)) == 0);
        capacity = (struct tcp_socket_capacity) {0};
        assert(send(stream[1], data, 1, 0) == 1);
        check_tcp_socket_with_capacity(&running, &event, -1, &capacity);
        tun_packet(tunnel[0], output, version, IPPROTO_TCP);
        assert(tcp_sendbuf_queries == 2 && tcp_outq_queries == 2);
        assert(capacity.available == 8192 && s.tcp.recv_window == 8192);

        // A successful write in that batch invalidates the sample immediately.
        struct tcphdr queued = {.seq = htonl(1000), .psh = 1};
        assert(queue_tcp(&running, &queued, "capacity refresh", &s.tcp, data, 5) == 1);
        assert_tcp_queue_totals(&s.tcp);
        assert(send(stream[1], data, 1, 0) == 1);
        event.events |= EPOLLOUT;
        check_tcp_socket_with_capacity(&running, &event, -1, &capacity);
        assert(tcp_sendbuf_queries == 3 && tcp_outq_queries == 3);
        assert(read(stream[1], output, sizeof(output)) == 5 && memcmp(output, data, 5) == 0);
        tun_packet(tunnel[0], output, version, IPPROTO_TCP); // App-data ACK.
        tun_packet(tunnel[0], output, version, IPPROTO_TCP); // Upstream payload.
        assert_tcp_queue_totals(&s.tcp);
        assert(s.tcp.forward == NULL && s.tcp.remote_seq == 1005);
        close(stream[0]); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_tcp_buffer_reuse(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    real_network = 1;
    for (int version = 4; version <= 6; version += 2) {
        int stream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        struct packet_buffers buffers = {0};
        struct arguments running = *args;
        running.tun = tunnel[1]; running.buffers = &buffers;
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = stream[0]};
        s.tcp.version = version; s.tcp.uid = own_uid; s.tcp.time = time(NULL);
        s.tcp.state = TCP_ESTABLISHED; s.tcp.mss = 1200;
        s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.remote_seq = 1000;
        s.tcp.recv_window = s.tcp.send_window = 16000;
        uint8_t *packet = get_packet_buffer(&running, PACKET_INPUT, 128, "tcp test input");
        assert(packet != NULL);
        size_t off = ip_header(packet, version, IPPROTO_TCP, 24,
                               version == 4 ? "203.0.113.2" : "2001:db8::2");
        struct tcphdr *tcp = (void *) (packet + off);
        tcp->source = htons(12345); tcp->dest = htons(443);
        tcp->seq = htonl(1000); tcp->ack_seq = htonl(2000);
        tcp->doff = 5; tcp->ack = tcp->psh = 1; tcp->window = htons(16000);
        memcpy(packet + off + 20, "keep", 4);
        memcpy(&s.tcp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
        memcpy(&s.tcp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
        s.tcp.source = tcp->source; s.tcp.dest = tcp->dest;
        args->ctx->ng_session = &s;
        assert(handle_tcp(&running, packet, off + 24, packet + off, own_uid, 1, NULL, -1) == 1);
        assert(s.tcp.forward != NULL && s.tcp.forward->data != packet + off + 20);
        packet = get_packet_buffer(&running, PACKET_INPUT, 4096, "tcp test input");
        assert(packet != NULL);
        memset(packet, 0xA5, 4096); // Simulate the next input, including a realloc.
        assert(memcmp(s.tcp.forward->data, "keep", 4) == 0);
        struct epoll_event event = {.events = EPOLLOUT, .data.ptr = &s};
        check_tcp_socket(&running, &event, -1);
        uint8_t output[2048], received[8];
        assert(read(stream[1], received, sizeof(received)) == 4 && memcmp(received, "keep", 4) == 0);
        tun_packet(tunnel[0], output, version, IPPROTO_TCP); // ACK after forwarding.
        assert(s.tcp.forward == NULL && allocations == 2);

        event.events = EPOLLIN;
        const size_t lengths[] = {17, 1200, 3};
        uint8_t data[1200];
        for (size_t i = 0; i < 3; i++) {
            memset(data, (int) i + 1, lengths[i]);
            assert(send(stream[1], data, lengths[i], 0) == lengths[i]);
            check_tcp_socket(&running, &event, -1);
            size_t length = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
            assert(length == off + 20 + lengths[i]);
            assert(memcmp(output + off + 20, data, lengths[i]) == 0);
            uint16_t checksum = load16(output + off + 16);
            packet_checksums(output, length, off, IPPROTO_TCP);
            assert(load16(output + off + 16) == checksum);
            assert(allocations == 2 && buffers.buffer[PACKET_INPUT].data == packet);
        }
        assert(s.tcp.local_seq == 2000 + 17 + 1200 + 3 && s.tcp.remote_seq == 1004);
        clear_packet_buffers(&buffers);
        args->ctx->ng_session = NULL;
        close(stream[0]); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
#endif
}

void test_lazy_tcp_logging(const struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    for (int version = 4; version <= 6; version += 2) {
        _Alignas(8) uint8_t packet[128], output[128];
        size_t off = ip_header(packet, version, IPPROTO_TCP, 20,
                               version == 4 ? "203.0.113.2" : "2001:db8::2");
        struct tcphdr *tcp = (void *) (packet + off);
        tcp->source = htons(12345); tcp->dest = htons(443);
        tcp->seq = htonl(1000); tcp->ack_seq = htonl(2000);
        tcp->doff = 5; tcp->ack = 1; tcp->window = htons(16000);
        packet_checksums(packet, off + 20, off, IPPROTO_TCP);
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = -1};
        s.tcp.version = version; s.tcp.uid = 12345; s.tcp.time = time(NULL);
        s.tcp.state = TCP_ESTABLISHED; s.tcp.source = tcp->source; s.tcp.dest = tcp->dest;
        s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.local_start = 100;
        s.tcp.remote_seq = 1000; s.tcp.remote_start = 500;
        s.tcp.recv_window = s.tcp.send_window = 16000;
        memcpy(&s.tcp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
        memcpy(&s.tcp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
        args->ctx->ng_session = &s;
        address_formats = 0;
        assert(check_tcp_session(&running, &s, 0, SESSION_MAX) == 0);
        handle_ip(&running, packet, off + 20, -1, 0, SESSION_MAX);
        assert(s.tcp.state == TCP_ESTABLISHED && s.tcp.acked == 2000);
        struct epoll_event event = {.data.ptr = &s};
        check_tcp_socket(&running, &event, -1);
        assert(write_ack(&running, &s.tcp) >= 0);
        tun_packet(tunnel[0], output, version, IPPROTO_TCP);
        assert(address_formats == 0 && allocations == 0 && sockets == 0);

        // An enabled error must still contain addresses and the state before reset.
        tcp->ack_seq = htonl(2001);
        assert(handle_tcp(&running, packet, off + 20, packet + off, -1, 1, NULL, -1) == 0);
        assert(address_formats == 2);
        assert(strstr(last_log, version == 4 ? "10.0.0.2/12345 > 203.0.113.2/443"
                                            : "2001:db8::1/12345 > 2001:db8::2/443") != NULL);
        assert(strstr(last_log, "uid 12345 ESTABLISHED loc 1900 rem 500 acked 1900 future ACK") != NULL);
        assert(s.tcp.state == TCP_CLOSING);
        tun_packet(tunnel[0], output, version, IPPROTO_TCP);
        args->ctx->ng_session = NULL;
    }
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_tcp_destination(const struct arguments *args) {
    for (int version = 4; version <= 6; version += 2) {
        const char *direct = version == 4 ? "198.51.100.1" : "fe80::1";
        const char *target = version == 4 ? "127.0.0.1" : "::1";
        struct tcp_session session = {.version = version, .uid = own_uid, .dest = htons(443)};
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6, direct, &session.daddr) == 1);
        struct allowed redirect = {.rport = 8443};
        strcpy(redirect.raddr, target);
        for (int redirected = 0; redirected <= 1; redirected++) {
            expect_destination(version, redirected ? target : direct, redirected ? 8443 : 443);
            int fd = open_tcp_socket(args, &session, redirected ? &redirect : NULL);
            assert(fd >= 0 && destination_calls == 1);
            close(fd);
            assert(sockets == 0);
        }
    }
}

static void drain_gap(const struct arguments *args, struct ng_session *s, int peer,
                      uint32_t target, size_t head_length, size_t *delivered) {
    struct epoll_event event = {.events = EPOLLOUT, .data.ptr = s};
    for (int turn = 0; turn < 4096 && s->tcp.remote_seq != target; turn++) {
        check_tcp_socket(args, &event, -1);
        assert_tcp_queue_totals(&s->tcp);
        while (is_readable(peer)) {
            uint8_t received[4096];
            ssize_t count = read(peer, received, sizeof(received));
            assert(count > 0);
            for (ssize_t i = 0; i < count; i++, (*delivered)++)
                assert(received[i] == (*delivered < head_length ? 0xA5 : 0x5A));
        }
    }
    assert(s->tcp.remote_seq == target);
}

void test_tcp_gap_recovery(const struct arguments *args, int kind) {
#ifdef TCP_QUEUE_BYTES
    int upstream[2], tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, upstream) == 0);
    assert(pipe(tunnel) == 0);
    real_network = 1;
    assert(fcntl(upstream[0], F_SETFL, O_NONBLOCK) == 0);
    struct ng_session s = {.protocol = IPPROTO_TCP, .socket = upstream[0]};
    s.tcp.version = 4;
    s.tcp.state = TCP_ESTABLISHED;
    s.tcp.remote_seq = UINT32_MAX - 1024;
    s.tcp.recv_window = TCP_QUEUE_BYTES * 2;
    uint32_t start = s.tcp.remote_seq;
    size_t len = kind == 0 ? 1024 : 1;
    unsigned int count = kind == 0 ? TCP_QUEUE_BYTES / len : TCP_QUEUE_SEGMENTS;
    uint8_t data[1024];
    memset(data, 0x5A, sizeof(data));
    struct tcphdr header = {0};
    if (kind == 2) {
        // The missing packet overlaps an existing segment, whose unacknowledged
        // suffix must remain recoverable by a later retransmission.
        header.seq = htonl(start + 1);
        assert(queue_tcp(args, &header, "test", &s.tcp, data, 8) == 1);
        header.seq = htonl(start + 9);
        assert(queue_tcp(args, &header, "test", &s.tcp, data, 1) == 1);
        len = 2;
        count = 2;
    } else {
        for (unsigned int i = 0; i < count; i++) {
            header.seq = htonl(start + (i + 1) * len);
            assert(queue_tcp(args, &header, "test", &s.tcp, data, len) == 1);
        }
        assert(get_receive_window(&s) == 0);
    }
    s.tcp.recv_window = 0;
    struct segment *original = s.tcp.forward;
    header.seq = htonl(start);
    memset(data, 0xA5, sizeof(data));
    for (int match = 1; match <= 2; match++) {
        fail_alloc_tag = "tcp segment";
        fail_alloc_match = match;
        assert(queue_tcp(args, &header, "test", &s.tcp, data, len) == 0);
        assert(fail_alloc_match == 0 && s.tcp.forward == original && allocations == count * 2);
        assert_tcp_queue_totals(&s.tcp);
    }
    fail_alloc_tag = NULL;
    assert(queue_tcp(args, &header, "test", &s.tcp, data, len) == 1);
    assert_tcp_queue_totals(&s.tcp);
    size_t retained = 0;
    unsigned int segments = 0;
    for (struct segment *q = s.tcp.forward; q != NULL; q = q->next) {
        retained += q->len;
        segments++;
    }
    assert(retained <= TCP_QUEUE_BYTES && segments <= TCP_QUEUE_SEGMENTS);
    assert(s.tcp.forward->seq == start);

    struct arguments running = *args;
    running.tun = tunnel[1];
    size_t delivered = 0;
    uint32_t first_end = start + (kind == 2 ? 2 : count * len);
    drain_gap(&running, &s, upstream[1], first_end, kind == 2 ? 1 : len, &delivered);
    assert(delivered == (uint32_t) (first_end - start));
    assert(get_receive_window(&s) > 0);

    // Replay the evicted tail and verify that the entire byte stream is delivered.
    header.seq = htonl(first_end);
    memset(data, 0x5A, sizeof(data));
    size_t replay = kind == 2 ? 7 : len;
    assert(queue_tcp(args, &header, "test", &s.tcp, data, replay) == 1);
    uint32_t end = start + (kind == 2 ? 10 : (count + 1) * len);
    drain_gap(&running, &s, upstream[1], end, kind == 2 ? 1 : len, &delivered);
    assert(delivered == (uint32_t) (end - start));
    assert(s.tcp.forward == NULL && allocations == 0);
    close(upstream[0]); close(upstream[1]);
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_tcp_allocation_failure(const struct arguments *args) {
    uint8_t packet[44] = {0x45};
    struct tcphdr *header = (void *) (packet + 20);
    header->syn = 1;
    header->doff = 5;
    const char *tags[] = {"tcp session", "syn segment", "syn segment data"};
    for (size_t i = 0; i < sizeof(tags) / sizeof(*tags); i++) {
        fail_alloc_tag = tags[i];
        fail_alloc_match = 1;
        assert(handle_tcp(args, packet, sizeof(packet), packet + 20, own_uid, 1, NULL, -1) == 0);
        assert(fail_alloc_match == 0);
        assert(allocations == 0 && sockets == 0 && args->ctx->ng_session == NULL);
    }
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int version = 4; version <= 6; version += 2) {
        struct ng_session s = {.socket = -1};
        s.tcp.version = version;
        s.tcp.state = TCP_ESTABLISHED;
        s.tcp.send_window = 4096;
        s.tcp.mss = 1500;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        fail_alloc_tag = "tcp socket";
        fail_alloc_match = 1;
        check_tcp_socket(args, &event, -1);
        assert(s.tcp.state == TCP_CLOSING && fail_alloc_match == 0);
        fail_alloc_tag = version == 4 ? "tcp write4" : "tcp write 6";
        fail_alloc_match = 1;
        assert(write_tcp(args, &s.tcp, packet, 1, 0, 1, 0, 0) == -1);
        assert(fail_alloc_match == 0 && allocations == 0);
    }
    // Both a new segment and its payload may fail before it is linked.
    struct tcp_session tcp = {.recv_window = UINT16_MAX};
    const uint8_t data[] = {1, 2, 3, 4};
    for (int match = 1; match <= 2; match++) {
        fail_alloc_tag = "tcp segment";
        fail_alloc_match = match;
        assert(queue_tcp(args, header, "test", &tcp, data, sizeof(data)) == 0);
        assert(tcp.forward == NULL && allocations == 0);
        assert_tcp_queue_totals(&tcp);
    }
    fail_alloc_tag = NULL;
    assert(queue_tcp(args, header, "test", &tcp, data, 2) == 1);
    uint8_t *original = tcp.forward->data;
    fail_alloc_tag = "tcp segment smaller";
    fail_alloc_match = 1;
    assert(queue_tcp(args, header, "test", &tcp, data, 4) == 0);
    assert(tcp.forward->data == original && tcp.forward->len == 2);
    assert_tcp_queue_totals(&tcp);
    fail_alloc_tag = NULL;
    assert(queue_tcp(args, header, "test", &tcp, data, 1) == 1);
    assert(tcp.forward->data == original && tcp.forward->len == 2);
    assert(memcmp(original, data, 2) == 0 && allocations == 2);
    clear_tcp_data(&tcp);
#endif
    fail_alloc_tag = NULL;
}

void test_tcp_queue_limits(const struct arguments *args, int kind) {
#ifdef TCP_QUEUE_BYTES
    struct ng_session s = {.socket = socket(AF_INET, SOCK_STREAM, 0)};
    s.tcp.recv_window = TCP_QUEUE_BYTES * 2;
    s.tcp.remote_seq = UINT32_MAX - 1024;
    struct tcphdr header = {0};
    uint8_t data[1024] = {0};
    if (kind == 2) {
        s.tcp.recv_window = 0;
        header.seq = htonl(s.tcp.remote_seq);
        assert(queue_tcp(args, &header, "test", &s.tcp, data, 8) == 0);
        assert(s.tcp.forward == NULL && allocations == 0);
        s.tcp.recv_window = 8;
        assert(queue_tcp(args, &header, "test", &s.tcp, data, 8) == 1);
        header.seq = htonl(s.tcp.remote_seq + 8);
        assert(queue_tcp(args, &header, "test", &s.tcp, data, 1) == 0);
        assert(allocations == 2);
    } else {
        size_t len = kind == 0 ? sizeof(data) : 1;
        unsigned int count = kind == 0 ? TCP_QUEUE_BYTES / len : TCP_QUEUE_SEGMENTS;
        assert(get_receive_window(&s) > 0 && get_receive_window(&s) <= TCP_QUEUE_BYTES);
        for (unsigned int i = 0; i < count; i++) {
            header.seq = htonl(s.tcp.remote_seq + i * len);
            assert(queue_tcp(args, &header, "test", &s.tcp, data, len) == 1);
        }
        header.seq = htonl(s.tcp.remote_seq + count * len);
        assert(queue_tcp(args, &header, "test", &s.tcp, data, len) == 0);
        assert(allocations == count * 2 && get_receive_window(&s) == 0);
        if (kind == 0) {
            uint8_t larger[2048] = {0};
            header.seq = htonl(s.tcp.remote_seq);
            uint8_t *original = s.tcp.forward->data;
            assert(queue_tcp(args, &header, "test", &s.tcp, larger, sizeof(larger)) == 0);
            assert(s.tcp.forward->data == original && s.tcp.forward->len == len);
        }
    }
    assert_tcp_queue_totals(&s.tcp);
    clear_tcp_data(&s.tcp);
    assert_tcp_queue_totals(&s.tcp);
    assert(get_receive_window(&s) > 0 && allocations == 0);
    close(s.socket);
#endif
}

void test_tcp_partial_retransmission(const struct arguments *args) {
    struct tcp_session tcp = {.recv_window = 32, .remote_seq = 100};
    struct tcphdr header = {.seq = htonl(100)};
    uint8_t original[] = {1, 2, 3, 4, 5, 6, 7, 8};
    uint8_t retransmission[12] = {0};
    assert(queue_tcp(args, &header, "test", &tcp, original, sizeof(original)) == 1);
    tcp.forward->sent = 6;
#ifndef INVIZIBLE_BASELINE_TESTS
    tcp.queue_unsent -= 6; // This fixture simulates the first partial send directly.
#endif
    assert_tcp_queue_totals(&tcp);
    uint8_t *buffer = tcp.forward->data;
    assert(queue_tcp(args, &header, "test", &tcp, retransmission, 2) == 1);
    assert(tcp.forward->data == buffer && tcp.forward->len == 8 && tcp.forward->sent == 6);
    assert(memcmp(tcp.forward->data, original, sizeof(original)) == 0);
    assert(queue_tcp(args, &header, "test", &tcp, retransmission, 12) == 1);
    assert(tcp.forward->len == 12 && tcp.forward->sent == 6);
    assert_tcp_queue_totals(&tcp);
    assert(memcmp(tcp.forward->data, original, sizeof(original)) == 0);
    assert(memcmp(tcp.forward->data + 8, retransmission + 8, 4) == 0);

    // Resume the partial send through a real socket: only the unsent suffix is sent.
    int upstream[2], tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_STREAM, 0, upstream) == 0);
    assert(pipe(tunnel) == 0);
    struct ng_session s = {.protocol = IPPROTO_TCP, .socket = upstream[0], .tcp = tcp};
    s.tcp.version = 4;
    s.tcp.state = TCP_ESTABLISHED;
    struct arguments running = *args;
    running.tun = tunnel[1];
    struct epoll_event event = {.events = EPOLLOUT, .data.ptr = &s};
    check_tcp_socket(&running, &event, -1);
    readable(upstream[1]);
    uint8_t received[12];
    assert(read(upstream[1], received, sizeof(received)) == 6);
    const uint8_t expected[] = {7, 8, 0, 0, 0, 0};
    assert(memcmp(received, expected, sizeof(expected)) == 0);
    assert(s.tcp.remote_seq == 112 && s.tcp.forward == NULL && allocations == 0);
    assert_tcp_queue_totals(&s.tcp);
    close(upstream[0]); close(upstream[1]);
    close(tunnel[0]); close(tunnel[1]);
}

void test_tcp(const struct arguments *args) {
    for (int doff = 0; doff <= 15; doff++) {
        uint8_t *pkt = calloc(1, 40);
        pkt[0] = 0x45;
        struct tcphdr *tcp = (void *) (pkt + 20);
        tcp->syn = 1; tcp->doff = doff;
        handle_tcp(args, pkt, 40, pkt + 20, own_uid, 0, NULL, -1);
        free(pkt);
        assert(allocations == 0);
    }
    uint8_t *pkt = calloc(1, 44);
    pkt[0] = 0x45;
    struct tcphdr *tcp = (void *) (pkt + 20);
    tcp->syn = 1; tcp->doff = 5;
    // Payload on a rejected SYN must release both segment allocations.
    handle_tcp(args, pkt, 44, pkt + 20, own_uid, 0, NULL, -1);
    assert(allocations == 0);
    failure = PROTECT;
    handle_tcp(args, pkt, 44, pkt + 20, own_uid, 1, NULL, -1);
    assert(allocations == 0 && sockets == 0);
    failure = NONE;
    tcp->doff = 6;
    const uint8_t options[][4] = {
        {1, 1, 1, 1}, {0, 0, 0, 0}, {2, 0, 0, 0}, {2, 1, 0, 0},
        {2, 5, 0, 0}, {1, 1, 1, 2}, {2, 4, 5, 180}, {3, 3, 255, 0}
    };
    for (size_t i = 0; i < sizeof(options) / sizeof(*options); i++) {
        memcpy(pkt + 40, options[i], 4);
        handle_tcp(args, pkt, 44, pkt + 20, own_uid, 0, NULL, -1);
        assert(allocations == 0);
    }
    free(pkt);
}

void test_tcp_socket_cleanup(const struct arguments *args) {
    struct tcp_session session = {.version = 4, .uid = own_uid};
    for (failure = PROTECT; failure <= CONNECT; failure++) {
        assert(open_tcp_socket(args, &session, NULL) == -1);
        assert(sockets == 0);
    }
    failure = NONE;
    int fd = open_tcp_socket(args, &session, NULL);
    assert(fd >= 0 && sockets == 1);
    close(fd);
    assert(sockets == 0);
}

void test_tcp_data_cleanup(void) {
    struct tcp_session tcp = {0};
    tcp.forward = ng_calloc(1, sizeof(*tcp.forward), "test segment");
    tcp.forward->data = ng_malloc(4, "test payload");
    clear_tcp_data(&tcp);
    clear_tcp_data(&tcp);
    assert(tcp.forward == NULL && allocations == 0);

}

void test_tcp_closed_data_cleanup(const struct arguments *args) {
    for (int closing = 0; closing <= 1; closing++) {
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = -1};
        s.tcp.version = 4;
        s.tcp.time = time(NULL);
        s.tcp.state = (closing ? TCP_CLOSING : TCP_CLOSE);
        s.tcp.sent = 10;
        s.tcp.received = 20;
        if (closing)
            s.socket = socket(PF_INET, SOCK_STREAM, 0);
        s.tcp.forward = ng_calloc(1, sizeof(*s.tcp.forward), "test segment");
        s.tcp.forward->data = ng_malloc(65535, "test queued payload");
        s.tcp.forward->len = 65535;
        assert(check_tcp_session(args, &s, 1, 100) == 0);
        assert(s.tcp.state == TCP_CLOSE && s.socket == -1);
        assert(s.tcp.forward == NULL && allocations == 0 && sockets == 0);
        assert(s.tcp.sent == 0 && s.tcp.received == 0);
        assert(check_tcp_session(args, &s, 1, 100) == 0); // Metadata still handles late packets.
        clear_tcp_data(&s.tcp); // Later session destruction remains safe.
    }
    assert(accounted_sent == 20 && accounted_received == 40);
}

static size_t tcp_packet(uint8_t *packet, int version, uint32_t seq, uint32_t ack,
                          int syn, int fin, const uint8_t *data, size_t len) {
    size_t optlen = (syn ? 8 : 0);
    size_t off = ip_header(packet, version, IPPROTO_TCP, 20 + optlen + len,
                           version == 4 ? "203.0.113.2" : "2001:db8::2");
    struct tcphdr *tcp = (void *) (packet + off);
    tcp->source = htons(12345); tcp->dest = htons(443);
    tcp->seq = htonl(seq); tcp->ack_seq = htonl(ack);
    tcp->syn = syn; tcp->fin = fin; tcp->ack = !syn; tcp->psh = len != 0;
    tcp->window = htons(16000); tcp->doff = (20 + optlen) / 4;
    if (syn) {
        const uint8_t options[] = {2, 4, 4, 176, 3, 3, 2, 0}; // MSS 1200, scale 2.
        memcpy(packet + off + 20, options, sizeof(options));
    }
    if (len != 0)
        memcpy(packet + off + 20 + optlen, data, len);
    return off + 20 + optlen + len;
}

static void tcp_input(const struct arguments *args, uint8_t *packet, size_t len, int version) {
    assert(handle_tcp(args, packet, len, packet + (version == 4 ? 20 : 40),
                      own_uid, 1, NULL, -1) == 1);
}

void test_tcp_backpressure(const struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    real_network = 1;
    for (int version = 4; version <= 6; version += 2) {
        int stream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
        assert(socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0, tunnel) == 0);
        int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
        assert(epoll_fd >= 0);
        struct arguments running = *args;
        running.tun = tunnel[1];
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = stream[0]};
        s.tcp.version = version; s.tcp.state = TCP_ESTABLISHED;
        s.tcp.uid = own_uid; s.tcp.time = time(NULL); s.tcp.mss = 1200;
        s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.remote_seq = 1000;
        s.tcp.recv_window = 65535;
        s.ev.events = EPOLLERR; s.ev.data.ptr = &s;
        assert(epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s.socket, &s.ev) == 0);
        _Alignas(8) uint8_t packet[128], output[2048];
        size_t length = tcp_packet(packet, version, 1000, 2000, 0, 0, NULL, 0);
        size_t off = version == 4 ? 20 : 40;
        struct tcphdr *header = (void *) (packet + off);
        s.tcp.source = header->source; s.tcp.dest = header->dest;
        memcpy(&s.tcp.saddr, packet + (version == 4 ? 12 : 8), version == 4 ? 4 : 16);
        memcpy(&s.tcp.daddr, packet + (version == 4 ? 16 : 24), version == 4 ? 4 : 16);
        args->ctx->ng_session = &s;
        struct epoll_event ready;
        if (kind == 0) {
            tcp_test_clock = 100000;
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 1000);
            assert(!is_readable(tunnel[0]) && !(s.ev.events & EPOLLIN));
            tcp_test_clock += 999;
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 1);
            assert(!is_readable(tunnel[0]));
            tcp_test_clock++;
            int intervals[] = {2000, 4000, 8000, 16000, 32000, 60000, 60000};
            for (size_t i = 0; i < sizeof(intervals) / sizeof(*intervals); i++) {
                assert(monitor_tcp_session(&running, &s, epoll_fd) == intervals[i]);
                tun_packet(tunnel[0], output, version, IPPROTO_TCP);
                const struct tcphdr *probe = (const void *) (output + off);
                assert(probe->ack && ntohl(probe->seq) == 2000 && ntohl(probe->ack_seq) == 999);
                assert(s.tcp.remote_seq == 1000 && !is_readable(tunnel[0]));
                tcp_test_clock += intervals[i];
            }
            // A tiny app window can be consumed entirely by pending DNS output.
            // It must still restart the probe interval rather than retain 60 s.
            s.tcp.dns_response = ng_calloc(1, 14, "pending DNS output");
            assert(s.tcp.dns_response != NULL);
            s.tcp.dns_response[1] = 12; s.tcp.dns_response[4] = 0x80;
            s.tcp.dns_response_size = s.tcp.dns_response_used = 14;
            s.tcp.dns_response_ready = true;
            s.tcp.send_window = 41; // One byte after the existing header allowance.
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 1000);
            assert(s.tcp.dns_response_sent == 1 && s.tcp.window_probe_interval == 1000);
            assert(tun_packet(tunnel[0], output, version, IPPROTO_TCP) == off + 21);
            assert(!is_readable(tunnel[0]));
            clear_tcp_data(&s.tcp);
            header->ack_seq = htonl(s.tcp.local_seq);
            // A real app ACK wakes TUN independently of the probe deadline.
            assert(handle_tcp(&running, packet, length, packet + off, own_uid, 1, NULL, epoll_fd) == 1);
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
            assert(s.tcp.window_probe_interval == 0 && (s.ev.events & EPOLLIN));
            assert(send(stream[1], "reply", 5, 0) == 5);
            assert(epoll_wait(epoll_fd, &ready, 1, 100) == 1);
            check_tcp_socket(&running, &ready, epoll_fd);
            assert(tun_packet(tunnel[0], output, version, IPPROTO_TCP) == off + 25);
            assert(memcmp(output + off + 20, "reply", 5) == 0);
            s.tcp.send_window = 0;
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 1000);
            assert(!is_readable(tunnel[0]));
            s.tcp.upstream_eof = true;
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
            tcp_test_clock = 0;
        } else {
            s.tcp.send_window = 16000;
            uint8_t data[16384];
            for (size_t i = 0; i < sizeof(data); i++) data[i] = (uint8_t) i;
            struct tcphdr queued = {.seq = htonl(1001)};
            assert(queue_tcp(&running, &queued, "gap", &s.tcp, data, 1) == 1);
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
            assert(!(s.ev.events & EPOLLOUT) && epoll_wait(epoll_fd, &ready, 1, 0) == 0);
            clear_tcp_data(&s.tcp);

            int small = 4096;
            assert(setsockopt(stream[0], SOL_SOCKET, SO_SNDBUF, &small, sizeof(small)) == 0);
            while (send(stream[0], data, sizeof(data), 0) > 0) {}
            assert(errno == EAGAIN);
            queued.seq = htonl(1000);
            assert(queue_tcp(&running, &queued, "blocked", &s.tcp, data, sizeof(data)) == 1);
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
            assert((s.ev.events & EPOLLOUT) && epoll_wait(epoll_fd, &ready, 1, 0) == 0);
            ready.events = EPOLLOUT; ready.data.ptr = &s;
            check_tcp_socket(&running, &ready, epoll_fd); // Stale readiness / EAGAIN.
            assert(s.tcp.forward != NULL && s.tcp.forward->sent == 0 && allocations == 2);
            while (read(stream[1], output, sizeof(output)) > 0) {}
            assert(errno == EAGAIN);
            size_t received = 0;
            for (int turn = 0; turn < 16 && s.tcp.forward != NULL; turn++) {
                assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
                assert(epoll_wait(epoll_fd, &ready, 1, 100) == 1 && (ready.events & EPOLLOUT));
                check_tcp_socket(&running, &ready, epoll_fd);
                if (turn == 0) {
                    assert(s.tcp.forward != NULL && s.tcp.forward->sent > 0);
                    assert(s.tcp.forward->sent < sizeof(data) && s.tcp.remote_seq == 1000);
                }
                ssize_t count;
                while ((count = read(stream[1], output, sizeof(output))) > 0) {
                    assert(received + count <= sizeof(data));
                    assert(memcmp(output, data + received, count) == 0);
                    received += count;
                }
                while (is_readable(tunnel[0])) tun_packet(tunnel[0], output, version, IPPROTO_TCP);
            }
            assert(received == sizeof(data) && s.tcp.remote_seq == 1000 + sizeof(data));
            assert(s.tcp.forward == NULL && allocations == 0);
            assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
            assert(!(s.ev.events & EPOLLOUT) && epoll_wait(epoll_fd, &ready, 1, 0) == 0);
        }
        args->ctx->ng_session = NULL;
        close(epoll_fd); close(stream[0]); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
        assert(allocations == 0 && sockets == 0);
    }
    real_network = 0;
#endif
}

void test_tcp_window_reopen(const struct arguments *args, int version) {
#ifndef INVIZIBLE_BASELINE_TESTS
    // Bypass the single-socket setup spy for this real loopback TCP fixture.
    int __real_socket(int domain, int type, int protocol);
    int __real_connect(int fd, const struct sockaddr *addr, socklen_t len);
    real_network = 1;
    int family = version == 4 ? AF_INET : AF_INET6;
    int listener = __real_socket(family, SOCK_STREAM | SOCK_NONBLOCK, 0);
    assert(listener >= 0);
    struct sockaddr_storage address = {0};
    socklen_t address_length;
    if (version == 4) {
        struct sockaddr_in *addr = (void *) &address;
        addr->sin_family = AF_INET;
        addr->sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address_length = sizeof(*addr);
    } else {
        struct sockaddr_in6 *addr = (void *) &address;
        addr->sin6_family = AF_INET6;
        addr->sin6_addr = in6addr_loopback;
        address_length = sizeof(*addr);
    }
    assert(bind(listener, (void *) &address, address_length) == 0);
    assert(listen(listener, 1) == 0);
    assert(getsockname(listener, (void *) &address, &address_length) == 0);
    int client = __real_socket(family, SOCK_STREAM | SOCK_NONBLOCK, 0);
    assert(client >= 0);
    int small = 4096, cork = 1;
    assert(setsockopt(client, SOL_SOCKET, SO_SNDBUF, &small, sizeof(small)) == 0);
    int connected = __real_connect(client, (void *) &address, address_length);
    assert(connected == 0 || (connected == -1 && errno == EINPROGRESS));
    struct pollfd accepted = {.fd = listener, .events = POLLIN};
    assert(poll(&accepted, 1, 5000) == 1);
    int peer = accept4(listener, NULL, NULL, SOCK_NONBLOCK | SOCK_CLOEXEC);
    assert(peer >= 0);
    struct pollfd writable = {.fd = client, .events = POLLOUT};
    assert(poll(&writable, 1, 5000) == 1);
    assert(setsockopt(client, IPPROTO_TCP, TCP_CORK, &cork, sizeof(cork)) == 0);
    int tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0, tunnel) == 0);
    struct arguments running = *args;
    running.tun = tunnel[1];
    struct ng_session s = {.protocol = IPPROTO_TCP, .socket = client};
    s.tcp.version = version; s.tcp.state = TCP_ESTABLISHED; s.tcp.time = time(NULL);
    s.tcp.uid = own_uid; s.tcp.mss = 1200;
    s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.remote_seq = 1000;
    s.tcp.send_window = 16000; s.tcp.recv_window = get_receive_window(&s);
    assert(s.tcp.recv_window == 8192);
    uint8_t data[8192], output[256];
    for (size_t i = 0; i < sizeof(data); i++) data[i] = (uint8_t) i;
    struct tcphdr queued = {.seq = htonl(1000)};
    assert(queue_tcp(&running, &queued, "exact window", &s.tcp, data, sizeof(data)) == 1);
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    s.ev.events = EPOLLERR; s.ev.data.ptr = &s;
    assert(epoll_ctl(epoll_fd, EPOLL_CTL_ADD, client, &s.ev) == 0);
    struct epoll_event ready;
    assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
    assert(epoll_wait(epoll_fd, &ready, 1, 1000) == 1 && (ready.events & EPOLLOUT));
    check_tcp_socket(&running, &ready, epoll_fd);
    size_t off = version == 4 ? 20 : 40;
    tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    const struct tcphdr *ack = (const void *) (output + off);
    assert(s.tcp.forward == NULL && allocations == 0 && s.tcp.remote_seq == 9192);
    assert(s.tcp.recv_window == 0 && ack->window == 0 && ntohl(ack->ack_seq) == 9192);
    assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
    assert(s.ev.events & EPOLLOUT); // Keep waiting for a window update despite the empty queue.

    cork = 0;
    assert(setsockopt(client, IPPROTO_TCP, TCP_CORK, &cork, sizeof(cork)) == 0);
    uint8_t received[8192];
    size_t used = 0;
    while (used < sizeof(received)) {
        struct pollfd readable = {.fd = peer, .events = POLLIN};
        assert(poll(&readable, 1, 5000) == 1);
        ssize_t count = read(peer, received + used, sizeof(received) - used);
        assert(count > 0);
        used += count;
    }
    assert(memcmp(received, data, sizeof(data)) == 0);
    assert(epoll_wait(epoll_fd, &ready, 1, 5000) == 1 && (ready.events & EPOLLOUT));
    check_tcp_socket(&running, &ready, epoll_fd);
    tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s.tcp.recv_window > 0 && ntohs(ack->window) > 0 && ntohl(ack->ack_seq) == 9192);
    assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
    assert(!(s.ev.events & EPOLLOUT) && epoll_wait(epoll_fd, &ready, 1, 0) == 0);
    assert(!is_readable(tunnel[0])); // No repeated update ACKs or writable-event spin.

    queued.seq = htonl(9192); queued.psh = 1;
    assert(queue_tcp(&running, &queued, "resumed input", &s.tcp, data, 5) == 1);
    assert(monitor_tcp_session(&running, &s, epoll_fd) == 0);
    assert(epoll_wait(epoll_fd, &ready, 1, 1000) == 1 && (ready.events & EPOLLOUT));
    check_tcp_socket(&running, &ready, epoll_fd);
    struct pollfd readable = {.fd = peer, .events = POLLIN};
    assert(poll(&readable, 1, 5000) == 1);
    assert(read(peer, received, sizeof(received)) == 5 && memcmp(received, data, 5) == 0);
    tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s.tcp.remote_seq == 9197 && s.tcp.forward == NULL && allocations == 0);
    // Once app input has shut down, a zero window needs no writable monitoring.
    s.tcp.fin_acked = true; s.tcp.recv_window = 0;
    assert(monitor_tcp_session(&running, &s, epoll_fd) == 0 && !(s.ev.events & EPOLLOUT));
    close(epoll_fd); close(client); close(peer); close(listener);
    close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
    real_network = 0;
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
int __real_fcntl(int fd, int cmd, ...);

static const struct tcphdr *half_close_reply(int fd, uint8_t *output, int version) {
    tun_packet(fd, output, version, IPPROTO_TCP);
    return (const void *) (output + (version == 4 ? 20 : 40));
}
#endif

void test_tcp_connect_failure(int version, int transient) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2];
    assert(pipe(tunnel) == 0);
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .tun = tunnel[1]};
    const int errors[] = {ENETUNREACH, EHOSTUNREACH, ECONNREFUSED};
    const int temporary[] = {EAGAIN, ENOBUFS, EINTR};
    const enum failure setup[] = {PROTECT, GETFL, SETFL};
    for (size_t i = 0; i < sizeof(errors) / sizeof(*errors); i++) {
        connect_errno = (transient == 1 ? temporary[i] : errors[i]);
        failure = (transient == 2 ? setup[i] : NONE);
        connect_fd = -1;
        // A stale errno from a local setup failure must not cause a reset.
        errno = ENETUNREACH;
        _Alignas(8) uint8_t packet[2048], output[2048];
        const uint8_t data[] = {1, 2, 3, 4};
        uint32_t seq = (i == 0 ? UINT32_MAX : 1000);
        size_t len = tcp_packet(packet, version, seq, 0, 1, 0, data, sizeof(data));
        size_t off = (version == 4 ? 20 : 40);
        assert(handle_tcp(&args, packet, len, packet + off, own_uid, 1, NULL, -1) == 0);
        assert(ctx.ng_session == NULL && allocations == 0 && sockets == 0);
        if (transient == 2) {
            assert(connect_fd == -1);
        } else {
            assert(connect_fd >= 0);
            assert(__real_fcntl(connect_fd, F_GETFD) == -1 && errno == EBADF);
        }
        if (transient == 0) {
            size_t reply_len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
            const struct tcphdr *reply = (const void *) (output + off);
            assert(reply_len == off + sizeof(*reply));
            assert(reply->rst && reply->ack && !reply->syn && !reply->fin);
            assert(reply->window == 0);
            assert(ntohl(reply->ack_seq) == seq + 1);
            assert(reply->source == htons(443) && reply->dest == htons(12345));
            if (version == 4) {
                const struct iphdr *request = (const void *) packet;
                const struct iphdr *response = (const void *) output;
                assert(response->saddr == request->daddr && response->daddr == request->saddr);
            } else {
                const struct ip6_hdr *request = (const void *) packet;
                const struct ip6_hdr *response = (const void *) output;
                assert(memcmp(&response->ip6_src, &request->ip6_dst, 16) == 0);
                assert(memcmp(&response->ip6_dst, &request->ip6_src, 16) == 0);
            }
        }
        assert(!is_readable(tunnel[0])); // Exactly one reset, or no response for retryable failures.
    }
    failure = NONE;
    connect_errno = 0;
    close(tunnel[0]); close(tunnel[1]);
#endif
}

void test_tcp_dns_counts(int version) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int kind = 0; kind < 4; kind++) {
        int stream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
        assert(pipe(tunnel) == 0);
        real_network = 1;
        struct context ctx = {.sdk = 29};
        struct arguments args = {.ctx = &ctx, .tun = tunnel[1], .rcode = 3};
        struct ng_session s = {.protocol = IPPROTO_TCP, .socket = stream[0]};
        s.tcp.version = version; s.tcp.state = TCP_ESTABLISHED;
        s.tcp.source = htons(12345); s.tcp.dest = htons(53);
        s.tcp.mss = 1500; s.tcp.recv_window = s.tcp.send_window = 16000;
        s.tcp.local_seq = s.tcp.acked = 2000; s.tcp.remote_seq = 1000;
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
        uint8_t frame[64] = {0};
        _Alignas(8) uint8_t output[2048];
        store16(frame, 12); frame[4] = 0x81;
        size_t len = 14;
        if (kind == 0) {
            store16(frame + 6, 2);
            assert(send(stream[1], frame, 5, 0) == 5);
            check_tcp_socket(&args, &event, -1);
            check_tcp_socket(&args, &event, -1);
            assert(!is_readable(tunnel[0]) && s.tcp.local_seq == 2000 && s.tcp.dns_header_used == 5);
            assert(send(stream[1], frame + 5, len - 5, 0) == len - 5);
        } else if (kind == 1) {
            memcpy(frame + 14, frame, 14);
            store16(frame + 20, 2);
            len = 28;
            assert(send(stream[1], frame, len, 0) == len);
        } else if (kind == 2) {
            assert(send(stream[1], frame, len, 0) == len); // Zero questions passes unchanged.
        } else {
            // A blocked single-question A reply must keep its TCP length prefix consistent.
            store16(frame, 33); store16(frame + 6, 1); store16(frame + 8, 1);
            store16(frame + 15, 1); store16(frame + 17, 1);
            frame[19] = 0xC0; frame[20] = 12;
            store16(frame + 21, 1); store16(frame + 23, 1); store16(frame + 29, 4);
            frame[31] = 192; frame[33] = 2; frame[34] = 1;
            len = 35; block_domain = 1;
            assert(send(stream[1], frame, len, 0) == len);
        }
        for (int turn = 0; turn < 8; turn++) {
            check_tcp_socket(&args, &event, -1);
            if (is_readable(tunnel[0])) {
                if (kind == 1 && s.tcp.state != TCP_CLOSING) {
                    half_close_reply(tunnel[0], output, version); // The preceding valid frame is preserved.
                } else
                    break;
            }
        }
        const struct tcphdr *reply = half_close_reply(tunnel[0], output, version);
        if (kind < 2) {
            assert(reply->rst && s.tcp.state == TCP_CLOSING && allocations == 1);
            assert(check_tcp_session(&args, &s, 0, SESSION_MAX) == 0);
            assert(s.socket == -1 && __real_fcntl(stream[0], F_GETFD) == -1 && errno == EBADF);
        } else {
            assert(!reply->rst && s.tcp.state == TCP_ESTABLISHED);
            const uint8_t *dns = output + (version == 4 ? 40 : 60);
            if (kind == 2) assert(memcmp(dns, frame, len) == 0);
            else {
                assert(load16(dns) == 17 && (dns[5] & 15) == 3 && load16(dns + 8) == 0);
                assert(s.tcp.local_seq == 2019 && resolved_calls > 0);
            }
            close(stream[0]);
        }
        assert(allocations == 0 && !is_readable(tunnel[0]));
        block_domain = 0;
        close(stream[1]); close(tunnel[0]); close(tunnel[1]);
    }
#endif
}

#ifndef INVIZIBLE_BASELINE_TESTS
static void dns_filter_frame(uint8_t *frame, uint16_t type, uint16_t id) {
    memset(frame, 0, 35);
    store16(frame, 33); store16(frame + 2, id); frame[4] = 0x81;
    store16(frame + 6, 1); store16(frame + 8, 1);
    store16(frame + 15, 1); store16(frame + 17, 1);
    frame[19] = 0xC0; frame[20] = 12;
    store16(frame + 21, type); store16(frame + 23, 1); store16(frame + 29, 4);
    frame[31] = 192; frame[33] = 2; frame[34] = 1;
}

static size_t collect_dns_test(struct ng_session *s, int tunnel,
                               uint8_t *collected, int acknowledge) {
    size_t total = 0;
    _Alignas(8) uint8_t packet[2048];
    while (is_readable(tunnel)) {
        size_t len = tun_packet(tunnel, packet, s->tcp.version, IPPROTO_TCP);
        size_t off = s->tcp.version == 4 ? 20 : 40;
        const struct tcphdr *tcp = (const void *) (packet + off);
        size_t header = off + tcp->doff * 4;
        assert(!tcp->rst && len >= header && total + len - header <= 65537);
        memcpy(collected + total, packet + header, len - header);
        total += len - header;
        if (acknowledge) {
            s->tcp.acked = s->tcp.local_seq;
            s->tcp.unconfirmed = 0;
        }
    }
    return total;
}

static size_t drain_dns_test(const struct arguments *args, struct ng_session *s,
                             int tunnel, uint8_t *collected, int acknowledge) {
    size_t total = 0;
    for (int turn = 0; turn < 2048 && s->tcp.state == TCP_ESTABLISHED &&
         (is_readable(s->socket) || s->tcp.dns_response_ready); turn++) {
        if (get_send_window(&s->tcp) == 0) break;
        struct epoll_event event = {.events = EPOLLIN | EPOLLOUT, .data.ptr = s};
        check_tcp_socket(args, &event, -1);
        assert(s->tcp.dns_response_size <= 65537);
        total += collect_dns_test(s, tunnel, collected + total, acknowledge);
    }
    return total;
}
#endif

void test_tcp_dns_buffer(int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    bool reuse = kind == 12;
    if (reuse) kind = 1; // A complete response followed by a partial next frame.
    for (int version = 4; version <= 6; version += 2) {
        int stream[2], tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
        // Preserve packet boundaries when one monitor pass flushes several packets.
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        real_network = 1;
        struct context ctx = {.sdk = 29};
        struct arguments args = {.ctx = &ctx, .tun = tunnel[1], .rcode = 3};
        struct packet_buffers buffers = {0};
        args.buffers = reuse ? &buffers : NULL;
        struct ng_session *s = ng_calloc(1, sizeof(*s), "test DNS session");
        ctx.ng_session = s;
        s->protocol = IPPROTO_TCP; s->socket = stream[0];
        s->tcp.version = version; s->tcp.state = TCP_ESTABLISHED;
        s->tcp.source = htons(12345); s->tcp.dest = htons(53);
        s->tcp.mss = kind == 7 || kind == 11 ? 7 : 1500;
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                         version == 4 ? "10.0.0.2" : "2001:db8::1", &s->tcp.saddr) == 1);
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                         version == 4 ? "203.0.113.2" : "2001:db8::2", &s->tcp.daddr) == 1);
        s->tcp.recv_window = s->tcp.send_window = 16000;
        s->tcp.local_seq = s->tcp.acked = 2000; s->tcp.remote_seq = 1000;
        s->tcp.time = time(NULL);
        struct epoll_event event = {.events = EPOLLIN, .data.ptr = s};
        uint8_t *input = calloc(1, 65537), *output = malloc(65537);
        assert(input != NULL && output != NULL);
        dns_filter_frame(input, DNS_QTYPE_A, 1234);
        dns_filter_frame(input + 35, DNS_QTYPE_HTTPS, 5678);
        block_domain = kind <= 2;
        if (kind <= 2) {
            size_t total = 0;
            if (kind == 0) {
                assert(send(stream[1], input, 70, 0) == 70);
                total = drain_dns_test(&args, s, tunnel[0], output, 1);
            } else if (kind == 1) {
                assert(send(stream[1], input, 40, 0) == 40);
                total = drain_dns_test(&args, s, tunnel[0], output, 1);
                assert(total == 19 && s->tcp.dns_response_used == 5);
                assert(send(stream[1], input + 40, 30, 0) == 30);
                total += drain_dns_test(&args, s, tunnel[0], output + total, 1);
            } else {
                for (size_t i = 0; i < 70; i++) {
                    assert(send(stream[1], input + i, 1, 0) == 1);
                    total += drain_dns_test(&args, s, tunnel[0], output + total, 1);
                    assert(total == (i < 34 ? 0 : i < 69 ? 19 : 38));
                }
            }
            assert(total == 38 && load16(output) == 17 && load16(output + 19) == 17);
            assert(load16(output + 2) == 1234 && load16(output + 21) == 5678);
            assert((output[5] & 15) == 3 && (output[24] & 15) == 3);
            assert(load16(output + 8) == 0 && load16(output + 27) == 0);
            assert(resolved_calls == version / 2 - 1); // One A callback per version; HTTPS has none.
            assert(s->tcp.dns_response == NULL && allocations == (reuse ? 2 : 1));
        } else if (kind == 7 || kind == 10 || kind == 11) {
            size_t len = kind == 10 ? 65537 : kind == 11 ? 155 : 35;
            if (kind == 10) {
                memset(input, 0, len); store16(input, UINT16_MAX); input[4] = 0x81;
            } else {
                s->tcp.send_window = 50; // After the first seven bytes, the app window is exhausted.
                if (kind == 11) {
                    // A valid TXT answer needs more than one monitor pass to flush.
                    store16(input, 153); store16(input + 15, 16);
                    store16(input + 21, 16); store16(input + 29, 124);
                    input[31] = 123; memset(input + 32, 'a', 123);
                    int small = 4096;
                    assert(setsockopt(stream[0], SOL_SOCKET, SO_SNDBUF, &small, sizeof(small)) == 0);
                    uint8_t junk[4096] = {0};
                    ssize_t sent;
                    do { sent = send(stream[0], junk, sizeof(junk), 0); } while (sent > 0);
                    assert(sent == -1 && errno == EAGAIN);
                }
            }
            assert(send(stream[1], input, len, 0) == len);
            size_t total = drain_dns_test(&args, s, tunnel[0], output, kind == 10);
            if (kind == 7 || kind == 11) {
                assert(total == 7 && s->tcp.dns_response_ready && allocations == 2);
                int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
                assert(epoll_fd >= 0);
                s->ev.events = EPOLLIN | EPOLLOUT | EPOLLERR; s->ev.data.ptr = s;
                assert(epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev) == 0);
                struct epoll_event ready;
                if (kind == 11) assert(epoll_wait(epoll_fd, &ready, 1, 0) == 0);
                s->tcp.last_keep_alive = get_ms();
                assert(monitor_tcp_session(&args, s, epoll_fd) > 0);
                unsigned int expected_write = 0;
#ifndef INVIZIBLE_BASELINE_TESTS
                // A full upstream buffer also closes the advertised app-input
                // window. Subscribe to its reopening without waiting for it to
                // become writable before flushing DNS output to TUN.
                if (s->tcp.recv_window == 0)
                    expected_write = EPOLLOUT;
#endif
                assert((s->ev.events & (EPOLLIN | EPOLLOUT)) == expected_write);
                _Alignas(8) uint8_t ack[128];
                size_t ack_len = tcp_packet(ack, version, s->tcp.remote_seq, s->tcp.local_seq,
                                            0, 0, NULL, 0);
                size_t ip_len = version == 4 ? 20 : 40;
                ((struct tcphdr *) (ack + ip_len))->dest = htons(53);
                assert(handle_tcp(&args, ack, ack_len, ack + ip_len, own_uid, 1, NULL, epoll_fd) == 1);
                assert(get_send_window(&s->tcp) > 0);
                for (int turn = 0; turn < 8 && s->tcp.dns_response_ready; turn++) {
                    int recheck = monitor_tcp_session(&args, s, epoll_fd);
                    size_t count = collect_dns_test(s, tunnel[0], output + total, 0);
                    assert(count > 0 && count <= TCP_YIELD * s->tcp.mss);
                    total += count;
                    assert((s->ev.events & EPOLLOUT) == expected_write);
                    if (s->tcp.dns_response_ready) {
                        assert(recheck > 0 && !(s->ev.events & EPOLLIN));
                    } else
                        assert(s->ev.events & EPOLLIN);
                    if (kind == 11) assert(epoll_wait(epoll_fd, &ready, 1, 0) == 0);
                }
                close(epoll_fd);
            }
            assert(total == len && memcmp(input, output, len) == 0);
            assert(s->tcp.dns_response == NULL && allocations == 1);
        } else {
            if (kind == 3 || kind == 4) {
                fail_alloc_tag = "tcp dns frame"; fail_alloc_match = kind == 3 ? 1 : 2;
            } else if (kind == 8) {
                fail_alloc_tag = version == 4 ? "tcp write4" : "tcp write 6";
                fail_alloc_match = 1;
            }
            size_t len = kind == 3 || kind == 4 || kind == 8 ? 35 : 5;
            assert(send(stream[1], input, len, 0) == len);
            for (int turn = 0; turn < 8 && s->tcp.state == TCP_ESTABLISHED && is_readable(s->socket); turn++)
                check_tcp_socket(&args, &event, -1);
            if (kind == 5) {
                assert(s->tcp.dns_response_used == 5);
                s->tcp.time = time(NULL) - TCP_IDLE_TIMEOUT - 1;
            } else if (kind == 6) {
                assert(shutdown(stream[1], SHUT_WR) == 0);
                check_tcp_socket(&args, &event, -1);
            }
            fail_alloc_tag = NULL;
            if (kind != 9) {
                if (kind == 3 || kind == 4 || kind == 8) assert(fail_alloc_match == 0);
                assert(check_tcp_session(&args, s, 0, SESSION_MAX) == 0);
                assert(s->tcp.state == TCP_CLOSE && s->socket == -1 && allocations == 1);
                assert(s->tcp.dns_response == NULL);
            } else
                assert(s->tcp.dns_response_used == 5 && allocations == 2);
        }
        clear_packet_buffers(&buffers);
        clear(&ctx);
        assert(ctx.ng_session == NULL && allocations == 0 && sockets == 0);
        assert(__real_fcntl(stream[0], F_GETFD) == -1 && errno == EBADF);
        block_domain = 0;
        free(input); free(output);
        close(stream[1]); close(tunnel[0]); close(tunnel[1]);
    }
#endif
}

void test_tcp_half_close(int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int version = kind == 1 ? 6 : 4;
    int stream[2], tunnel[2];
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    assert(pipe(tunnel) == 0);
    real_network = 1;
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .tun = tunnel[1]};
    struct ng_session s = {.protocol = IPPROTO_TCP, .socket = stream[0]};
    s.tcp.version = version;
    s.tcp.uid = 12345;
    s.tcp.state = TCP_ESTABLISHED;
    s.tcp.mss = 1200;
    s.tcp.local_seq = s.tcp.acked = 2000;
    s.tcp.remote_seq = 1000;
    s.tcp.recv_window = s.tcp.send_window = 16000;
    s.tcp.time = time(NULL);
    s.tcp.source = htons(12345); s.tcp.dest = htons(443);
    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
            version == 4 ? "10.0.0.2" : "2001:db8::1", &s.tcp.saddr) == 1);
    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
            version == 4 ? "203.0.113.2" : "2001:db8::2", &s.tcp.daddr) == 1);
    ctx.ng_session = &s;
    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    assert(epoll_fd >= 0);
    s.ev.events = EPOLLIN | EPOLLERR;
    s.ev.data.ptr = &s;
    assert(epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s.socket, &s.ev) == 0);
    struct epoll_event event = {.events = EPOLLIN, .data.ptr = &s};
    _Alignas(8) uint8_t packet[2048], output[2048], received[64];
    const uint8_t request[] = {'a', 0, 'b', 255, 'c', 'd'};
    size_t off = version == 4 ? 20 : 40;
    size_t len;
    const struct tcphdr *reply;

    if (kind == 9) {
        len = tcp_packet(packet, version, 1000, 2000, 0, 0, request, sizeof(request));
        tcp_input(&args, packet, len, version);
        assert(s.tcp.forward != NULL && allocations == 2);
    }
    if (kind != 3) {
        assert(shutdown(stream[1], SHUT_WR) == 0);
        readable(s.socket);
        check_tcp_socket(&args, &event, epoll_fd);
        reply = half_close_reply(tunnel[0], output, version);
        assert(reply->fin && reply->ack && !reply->rst);
        assert(s.tcp.upstream_eof && s.tcp.state == TCP_FIN_WAIT1 && s.socket == stream[0]);
        assert(__real_fcntl(stream[0], F_GETFD) >= 0);
        assert(s.tcp.local_seq == 2001 && allocations == (kind == 9 ? 2 : 0));
        check_tcp_socket(&args, &event, epoll_fd); // Stale readable events must not resend FIN.
        assert(s.tcp.local_seq == 2001 && !is_readable(tunnel[0]));
        assert(monitor_tcp_session(&args, &s, epoll_fd) == 0);
        assert(!(s.ev.events & EPOLLIN));
        struct epoll_event ready;
        int count = epoll_wait(epoll_fd, &ready, 1, 0);
        assert(count == (kind == 9 ? 1 : 0));
        if (count) assert(ready.events & EPOLLOUT);
    }

    if (kind == 6) {
        failure = SHUTDOWN;
        len = tcp_packet(packet, version, 1000, s.tcp.local_seq, 0, 1, NULL, 0);
        assert(handle_tcp(&args, packet, len, packet + off, -1, 1, NULL, epoll_fd) == 0);
        failure = NONE;
        reply = half_close_reply(tunnel[0], output, version);
        assert(reply->rst && s.tcp.state == TCP_CLOSING && !s.tcp.fin_acked);
    } else if (kind == 8) {
        close(s.socket); s.socket = -1;
        len = tcp_packet(packet, version, 1000, s.tcp.local_seq, 0, 0, request, sizeof(request));
        assert(handle_tcp(&args, packet, len, packet + off, -1, 1, NULL, epoll_fd) == 0);
        assert(strstr(last_log, "data while local closed") != NULL);
        assert(strstr(last_log, "uid 12345") != NULL && strstr(last_log, "uid -1") == NULL);
        reply = half_close_reply(tunnel[0], output, version);
        assert(reply->rst);
    } else {
        if (kind == 9) {
            // EOF must preserve data that was already waiting to be forwarded.
            len = tcp_packet(packet, version, 1006, s.tcp.local_seq, 0, 1, NULL, 0);
        } else if (kind == 2) {
            // FIN and the tail arrive before the missing head, and are retransmitted.
            len = tcp_packet(packet, version, 1003, 2000, 0, 1, request + 3, 3);
            tcp_input(&args, packet, len, version);
            tcp_input(&args, packet, len, version);
            assert(s.tcp.fin_received && !s.tcp.fin_acked && s.tcp.fin_seq == 1006);
            assert(s.tcp.remote_seq == 1000 && allocations == 2 && !is_readable(tunnel[0]));
            len = tcp_packet(packet, version, 1000, s.tcp.local_seq, 0, 0, request, 3);
        } else {
            int fin = kind == 3 || kind == 4;
            len = tcp_packet(packet, version, 1000,
                    kind == 4 ? 2000 : s.tcp.local_seq, 0, fin, request, sizeof(request));
        }
        tcp_input(&args, packet, len, version);
        if (kind == 5 || kind == 7) {
            if (kind == 5)
                s.tcp.time = time(NULL) - TCP_CLOSE_TIMEOUT - 1;
            else {
                len = tcp_packet(packet, version, 1000, s.tcp.local_seq, 0, 0, NULL, 0);
                ((struct tcphdr *) (packet + off))->rst = 1;
                assert(handle_tcp(&args, packet, len, packet + off, -1, 1, NULL, epoll_fd) == 0);
            }
        } else {
            assert(monitor_tcp_session(&args, &s, epoll_fd) == 0);
            assert(s.ev.events & EPOLLOUT);
            if (s.tcp.upstream_eof) assert(!(s.ev.events & EPOLLIN));
            event.events = EPOLLOUT;
            check_tcp_socket(&args, &event, epoll_fd);
            readable(stream[1]);
            assert(recv(stream[1], received, sizeof(received), 0) == sizeof(request));
            assert(memcmp(received, request, sizeof(request)) == 0);
            assert(s.tcp.forward == NULL && allocations == 0);
            reply = half_close_reply(tunnel[0], output, version);
            assert(reply->ack && !reply->rst);
            assert(ntohl(reply->ack_seq) == (s.tcp.fin_received ? 1007 : 1006));
            if (kind == 0 || kind == 1) {
                assert(s.tcp.state == TCP_FIN_WAIT2);
                len = tcp_packet(packet, version, 1006, s.tcp.local_seq, 0, 1, NULL, 0);
                tcp_input(&args, packet, len, version);
                reply = half_close_reply(tunnel[0], output, version);
                assert(reply->ack && !reply->rst && ntohl(reply->ack_seq) == 1007);
            }
            readable(stream[1]);
            assert(recv(stream[1], received, sizeof(received), 0) == 0);
            assert(s.tcp.fin_acked && s.tcp.remote_seq == 1007);

            if (kind == 3) {
                assert(s.tcp.state == TCP_CLOSE_WAIT && s.socket >= 0);
                const uint8_t response[] = {1, 2, 3, 4};
                assert(send(stream[1], response, sizeof(response), 0) == sizeof(response));
                event.events = EPOLLIN;
                readable(s.socket);
                check_tcp_socket(&args, &event, epoll_fd);
                reply = half_close_reply(tunnel[0], output, version);
                assert(!reply->fin && !reply->rst);
                assert(memcmp(output + off + 20, response, sizeof(response)) == 0);
                assert(shutdown(stream[1], SHUT_WR) == 0);
                check_tcp_socket(&args, &event, epoll_fd);
                reply = half_close_reply(tunnel[0], output, version);
                assert(reply->fin && reply->ack && s.tcp.state == TCP_LAST_ACK);
            }
            if (kind == 4) {
                assert(s.tcp.state == TCP_LAST_ACK);
                len = tcp_packet(packet, version, 1000, 2000, 0, 1, request, sizeof(request));
                tcp_input(&args, packet, len, version);
                reply = half_close_reply(tunnel[0], output, version);
                assert(reply->ack && !reply->rst && ntohl(reply->ack_seq) == 1007);
                assert(s.tcp.remote_seq == 1007); // Repeated FIN consumes no extra sequence byte.
            }
            if (kind == 3 || kind == 4) {
                len = tcp_packet(packet, version, 1007, s.tcp.local_seq, 0, 0, NULL, 0);
                tcp_input(&args, packet, len, version);
                assert(s.tcp.state == TCP_CLOSING);
            } else
                assert(s.tcp.state == TCP_CLOSE);
            assert(s.socket == -1);
            assert(__real_fcntl(stream[0], F_GETFD) == -1 && errno == EBADF);
        }
    }
    assert(check_tcp_session(&args, &s, 0, SESSION_MAX) == 0);
    if (kind == 5) {
        reply = half_close_reply(tunnel[0], output, version);
        assert(reply->rst);
    }
    assert(s.tcp.state == TCP_CLOSE && s.socket == -1 && s.tcp.forward == NULL);
    // socketpair descriptors bypass the socket counter: verify kernel ownership
    // before closing the other fixtures, with no intervening descriptor reuse.
    assert(__real_fcntl(stream[0], F_GETFD) == -1 && errno == EBADF);
    assert(allocations == 0 && sockets == 0);
    ctx.ng_session = NULL;
    close(epoll_fd); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
#endif
}

void tcp_flow(int version) {
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx};
    int tunnel[2], stream[2];
    assert(pipe(tunnel) == 0);
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    args.tun = tunnel[1];
    _Alignas(8) uint8_t packet[2048], output[2048], received[64];
    size_t off = (version == 4 ? 20 : 40);
    size_t len = tcp_packet(packet, version, 1000, 0, 1, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    struct ng_session *s = ctx.ng_session;
    assert(s != NULL && s->tcp.state == TCP_LISTEN && s->tcp.mss == 1200);
    assert(s->tcp.send_scale == 2 && s->tcp.send_window == 64000);
    tcp_input(&args, packet, len, version); // Repeated SYN must reuse the session.
    assert(ctx.ng_session == s && s->next == NULL);
    close(s->socket); s->socket = stream[0];
    struct epoll_event ev = {.events = EPOLLOUT, .data.ptr = s};
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    const struct tcphdr *reply = (const void *) (output + off);
    assert(reply->syn && reply->ack && ntohl(reply->ack_seq) == 1001);
    assert(reply->source == htons(443) && reply->dest == htons(12345));
    assert(s->tcp.state == TCP_SYN_RECV);
    printf("TCP%d SYNACK %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    assert(s->tcp.state == TCP_ESTABLISHED);
    const uint8_t request[] = {'a', 0, 'b', 255, 'c', 'd'};
    uint32_t seq = s->tcp.remote_seq;
    // Queue the later half first, then deliver the missing earlier half.
    len = tcp_packet(packet, version, seq + 3, s->tcp.local_seq, 0, 0, request + 3, 3);
    tcp_input(&args, packet, len, version);
    len = tcp_packet(packet, version, seq, s->tcp.local_seq, 0, 0, request, 3);
    tcp_input(&args, packet, len, version);
    check_tcp_socket(&args, &ev, -1);
    readable(stream[1]);
    assert(recv(stream[1], received, sizeof(received), 0) == sizeof(request));
    assert(memcmp(received, request, sizeof(request)) == 0);
    assert(s->tcp.forward == NULL && s->tcp.remote_seq == seq + sizeof(request));
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->ack && ntohl(reply->ack_seq) == seq + sizeof(request));
    printf("TCP%d ACK %08x\n", version, fingerprint(output, len));
    const uint8_t response[] = {5, 4, 0, 3, 2, 1};
    assert(send(stream[1], response, sizeof(response), 0) == sizeof(response));
    ev.events = EPOLLIN;
    readable(s->socket);
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->ack && len == off + 20 + sizeof(response));
    assert(memcmp(output + off + 20, response, sizeof(response)) == 0);
    assert(s->tcp.sent == sizeof(request) && s->tcp.received == sizeof(response));
    printf("TCP%d DATA %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 0, NULL, 0);
    tcp_input(&args, packet, len, version);
    close(stream[1]);
    check_tcp_socket(&args, &ev, -1);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    reply = (const void *) (output + off);
    assert(reply->fin && reply->ack && s->tcp.state == TCP_FIN_WAIT1);
    printf("TCP%d FIN %08x\n", version, fingerprint(output, len));
    len = tcp_packet(packet, version, s->tcp.remote_seq, s->tcp.local_seq, 0, 1, NULL, 0);
    tcp_input(&args, packet, len, version);
    len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s->tcp.state == TCP_CLOSE);
    printf("TCP%d CLOSED %08x\n", version, fingerprint(output, len));
    clear(&ctx);
    close(tunnel[0]); close(tunnel[1]);
    assert(allocations == 0 && sockets == 0);
    cases++;
}

void socks_flow(int version, int authentication, int max_credentials) {
    JNIEnv env = &jni;
    char user[128], password[128];
    if (max_credentials) {
        memset(user, 'u', 127); user[127] = 0;
        memset(password, 'p', 127); password[127] = 0;
    } else {
        strcpy(user, "us\xC3\xA9r"); strcpy(password, "secret");
    }
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "127.0.0.1", 1080, (jstring) user, (jstring) password);
    assert(strcmp(proxy_socks5_username, user) == 0 && strcmp(proxy_socks5_password, password) == 0);
    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx};
    struct ng_session s = {.protocol = IPPROTO_TCP};
    s.tcp.version = version; s.tcp.uid = own_uid; s.tcp.state = TCP_LISTEN;
    s.tcp.socks5 = SOCKS5_HELLO; s.tcp.source = htons(12345); s.tcp.dest = htons(443);
    s.tcp.local_seq = 2000; s.tcp.remote_seq = 1000; s.tcp.recv_window = 16000;
    if (version == 4) {
        inet_pton(AF_INET, "10.0.0.2", &s.tcp.saddr.ip4);
        inet_pton(AF_INET, "203.0.113.2", &s.tcp.daddr.ip4);
    } else {
        inet_pton(AF_INET6, "2001:db8::1", &s.tcp.saddr.ip6);
        inet_pton(AF_INET6, "2001:db8::2", &s.tcp.daddr.ip6);
    }
    int tunnel[2], stream[2];
    assert(pipe(tunnel) == 0 && socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    args.tun = tunnel[1]; s.socket = stream[0];
    struct epoll_event ev = {.events = EPOLLIN, .data.ptr = &s};
    uint8_t reply[] = {5, authentication ? 2 : 0};
    assert(send(stream[1], reply, sizeof(reply), 0) == sizeof(reply));
    check_tcp_socket(&args, &ev, -1);
    uint8_t wire[512];
    readable(stream[1]);
    ssize_t len = recv(stream[1], wire, sizeof(wire), 0);
    if (authentication) {
        size_t ulen = strlen(user), plen = strlen(password);
        assert(s.tcp.socks5 == SOCKS5_AUTH && len == 3 + ulen + plen);
        assert(wire[0] == 1 && wire[1] == ulen && memcmp(wire + 2, user, ulen) == 0);
        assert(wire[ulen + 2] == plen && memcmp(wire + ulen + 3, password, plen) == 0);
        printf("SOCKS%d AUTH %08x\n", version, fingerprint(wire, len));
        const uint8_t auth_ok[] = {1, 0};
        assert(send(stream[1], auth_ok, sizeof(auth_ok), 0) == sizeof(auth_ok));
        check_tcp_socket(&args, &ev, -1);
        readable(stream[1]);
        len = recv(stream[1], wire, sizeof(wire), 0);
    }
    assert(s.tcp.socks5 == SOCKS5_CONNECT && len == (version == 4 ? 10 : 22));
    assert(wire[0] == 5 && wire[1] == 1 && wire[3] == (version == 4 ? 1 : 4));
    assert(load16(wire + len - 2) == 443);
    printf("SOCKS%d CONNECT %08x\n", version, fingerprint(wire, len));
    memset(wire, 0, len); wire[0] = 5; wire[3] = (version == 4 ? 1 : 4);
    assert(send(stream[1], wire, len, 0) == len);
    check_tcp_socket(&args, &ev, -1);
    _Alignas(8) uint8_t output[2048];
    size_t out_len = tun_packet(tunnel[0], output, version, IPPROTO_TCP);
    assert(s.tcp.state == TCP_SYN_RECV && s.tcp.socks5 == SOCKS5_CONNECTED);
    printf("SOCKS%d SYNACK %08x\n", version, fingerprint(output, out_len));
    close(stream[0]); close(stream[1]); close(tunnel[0]); close(tunnel[1]);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "", 0, (jstring) "", (jstring) "");
    assert(string_refs == 0 && exceptions == 0 && allocations == 0);
    cases++;
}
