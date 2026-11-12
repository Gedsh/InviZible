#include "test_support.h"

#ifndef INVIZIBLE_BASELINE_TESTS
static void index_key(struct ng_session *s, int version, int protocol, uint16_t id) {
    s->protocol = protocol;
    s->socket = -1;
    uint8_t source[16], dest[16];
    memset(source, 0xa5, sizeof(source));
    memset(dest, 0x5a, sizeof(dest));
    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                     version == 4 ? "10.1.10.1" : "2001:db8::1", source) == 1);
    assert(inet_pton(version == 4 ? AF_INET : AF_INET6,
                     version == 4 ? "192.0.2.1" : "2001:db8::2", dest) == 1);
    if (protocol == IPPROTO_TCP) {
        s->tcp.version = version; s->tcp.source = htons(id); s->tcp.dest = htons(443);
        memcpy(&s->tcp.saddr, source, 16); memcpy(&s->tcp.daddr, dest, 16);
    } else if (protocol == IPPROTO_UDP) {
        s->udp.version = version; s->udp.source = htons(id); s->udp.dest = htons(443);
        memcpy(&s->udp.saddr, source, 16); memcpy(&s->udp.daddr, dest, 16);
    } else {
        s->icmp.version = version; s->icmp.id = htons(id);
        memcpy(&s->icmp.saddr, source, 16); memcpy(&s->icmp.daddr, dest, 16);
    }
}

static struct ng_session *index_lookup(const struct context *ctx, const struct ng_session *s) {
    if (s->protocol == IPPROTO_TCP)
        return find_session(ctx, s->tcp.version, s->protocol, &s->tcp.saddr,
                            s->tcp.source, &s->tcp.daddr, s->tcp.dest);
    if (s->protocol == IPPROTO_UDP)
        return find_session(ctx, s->udp.version, s->protocol, &s->udp.saddr,
                            s->udp.source, &s->udp.daddr, s->udp.dest);
    return find_session(ctx, s->icmp.version, s->protocol, &s->icmp.saddr,
                        s->icmp.id, &s->icmp.daddr, 0);
}

static void index_delete(struct context *ctx, struct ng_session *s) {
    struct ng_session **link = &ctx->ng_session;
    while (*link != NULL && *link != s) link = &(*link)->next;
    assert(*link == s);
    unindex_session(ctx, s);
    *link = s->next;
    ng_free(s, __FILE__, __LINE__);
}

static void assert_index_empty(const struct context *ctx) {
    assert(ctx->ng_session == NULL);
    for (size_t i = 0; i < SESSION_BUCKETS; i++) assert(ctx->session_bucket[i] == NULL);
}
#endif

void test_session_index_keys(struct arguments *args, int version) {
#ifndef INVIZIBLE_BASELINE_TESTS
    struct ng_session tcp = {0}, udp = {0}, icmp = {0};
    int icmp_protocol = version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
    index_key(&tcp, version, IPPROTO_TCP, 12345);
    index_key(&udp, version, IPPROTO_UDP, 12345);
    index_key(&icmp, version, icmp_protocol, 12345);
    add_session(args->ctx, &tcp); add_session(args->ctx, &udp); add_session(args->ctx, &icmp);
    assert(index_lookup(args->ctx, &tcp) == &tcp && index_lookup(args->ctx, &udp) == &udp);
    assert(index_lookup(args->ctx, &icmp) == &icmp);
    uint8_t unaligned_source[17], unaligned_dest[17];
    memcpy(unaligned_source + 1, &tcp.tcp.saddr, 16);
    memcpy(unaligned_dest + 1, &tcp.tcp.daddr, 16);
    assert(find_session(args->ctx, version, IPPROTO_TCP, unaligned_source + 1, tcp.tcp.source,
                         unaligned_dest + 1, tcp.tcp.dest) == &tcp);
    struct ng_session miss = tcp;
    miss.tcp.source ^= htons(1); assert(index_lookup(args->ctx, &miss) == NULL);
    miss = tcp; miss.tcp.dest ^= htons(1); assert(index_lookup(args->ctx, &miss) == NULL);
    miss = tcp; ((uint8_t *) &miss.tcp.saddr)[version == 4 ? 3 : 15] ^= 1;
    assert(index_lookup(args->ctx, &miss) == NULL);
    miss = tcp; ((uint8_t *) &miss.tcp.daddr)[version == 4 ? 3 : 15] ^= 1;
    assert(index_lookup(args->ctx, &miss) == NULL);
    miss = tcp; miss.tcp.version = version == 4 ? 6 : 4;
    assert(index_lookup(args->ctx, &miss) == NULL);
    if (version == 4) {
        miss = tcp;
        memset((uint8_t *) &miss.tcp.saddr + 4, 0, 12);
        memset((uint8_t *) &miss.tcp.daddr + 4, 0, 12);
        assert(index_lookup(args->ctx, &miss) == &tcp); // IPv4 union padding is not part of the key.
    }
    const int tcp_states[] = {TCP_LISTEN, TCP_ESTABLISHED, TCP_CLOSING, TCP_CLOSE};
    for (size_t i = 0; i < sizeof(tcp_states) / sizeof(*tcp_states); i++) {
        tcp.tcp.state = tcp_states[i]; assert(index_lookup(args->ctx, &tcp) == &tcp);
    }
    const int udp_states[] = {UDP_ACTIVE, UDP_FINISHING, UDP_CLOSED, UDP_BLOCKED};
    for (size_t i = 0; i < sizeof(udp_states) / sizeof(*udp_states); i++) {
        udp.udp.state = udp_states[i]; assert(index_lookup(args->ctx, &udp) == &udp);
    }
    struct ng_session duplicate = tcp;
    add_session(args->ctx, &duplicate);
    assert(index_lookup(args->ctx, &tcp) == &duplicate);
    unindex_session(args->ctx, &duplicate); args->ctx->ng_session = duplicate.next;
    assert(index_lookup(args->ctx, &tcp) == &tcp);
    struct ng_session stopped = icmp;
    stopped.icmp.stop = 1; add_session(args->ctx, &stopped);
    assert(index_lookup(args->ctx, &icmp) == &icmp); // Skip a newer stopped echo session.
    icmp.icmp.stop = 1; assert(index_lookup(args->ctx, &icmp) == NULL);
    miss = icmp; miss.icmp.id ^= htons(1); assert(index_lookup(args->ctx, &miss) == NULL);
    icmp.icmp.stop = 0;
    // Reuse a blocked record for another tuple only after unlinking its old key.
    struct ng_session old = udp;
    unindex_session(args->ctx, &udp);
    icmp.next = udp.next; // Remove UDP from the cleanup chain before reinserting.
    udp.udp.source = htons(54321);
    add_session(args->ctx, &udp);
    assert(index_lookup(args->ctx, &old) == NULL && index_lookup(args->ctx, &udp) == &udp);
    set_test_sessions(args->ctx, NULL); // Stack records have no owned payload/socket.
    assert_index_empty(args->ctx);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_session_index_policy(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    struct ng_session *closed = ng_calloc(1, sizeof(*closed), "closed index anchor");
    assert(closed != NULL); index_key(closed, 4, IPPROTO_TCP, 1234);
    closed->tcp.state = TCP_CLOSE; add_session(args->ctx, closed);
    struct ng_session key[2] = {0};
    for (int i = 0; i < 2; i++) {
        struct ng_session *blocked = ng_calloc(1, sizeof(*blocked), "blocked index policy");
        assert(blocked != NULL); index_key(blocked, i == 0 ? 4 : 6, IPPROTO_UDP, 1235 + i);
        blocked->udp.state = UDP_BLOCKED; key[i] = *blocked;
        add_session(args->ctx, blocked);
    }
    check_allowed(args);
    assert(args->ctx->ng_session == closed && closed->next == NULL);
    for (int i = 0; i < 2; i++) assert(index_lookup(args->ctx, &key[i]) == NULL);
    assert(index_lookup(args->ctx, closed) == closed);
    assert_test_session_index(args->ctx);
    clear(args->ctx); assert_index_empty(args->ctx);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_session_index_churn(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    const int count = 2560;
    struct ng_session **records = calloc(count, sizeof(*records));
    assert(records != NULL);
    for (int i = 0; i < count; i++) {
        records[i] = ng_calloc(1, sizeof(**records), "indexed session");
        assert(records[i] != NULL);
        int version = i % 2 == 0 ? 4 : 6;
        int protocol = i % 3 == 0 ? IPPROTO_TCP : i % 3 == 1 ? IPPROTO_UDP
                                        : version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6;
        index_key(records[i], version, protocol, 1000 + i);
        add_session(args->ctx, records[i]);
    }
    unsigned int total = 0, max_bucket = 0;
    for (size_t bucket = 0; bucket < SESSION_BUCKETS; bucket++) {
        unsigned int length = 0;
        for (struct ng_session *s = args->ctx->session_bucket[bucket]; s; s = s->hash_next) {
            assert(++length <= count); total++;
        }
        if (length > max_bucket) max_bucket = length;
    }
    assert(total == count && max_bucket > 1 && max_bucket <= 32);
    for (int i = 0; i < count; i++) assert(index_lookup(args->ctx, records[i]) == records[i]);
    // Remove every other key, checking collision-chain unlinking and reuse of
    // reclaimed allocations. Surviving keys must keep their original records.
    for (int i = 0; i < count; i += 2) {
        struct ng_session key = *records[i];
        index_delete(args->ctx, records[i]); records[i] = NULL;
        assert(index_lookup(args->ctx, &key) == NULL);
    }
    for (int i = 1; i < count; i += 2) assert(index_lookup(args->ctx, records[i]) == records[i]);
    for (int i = 0; i < count; i += 2) {
        records[i] = ng_calloc(1, sizeof(**records), "reused indexed session");
        assert(records[i] != NULL);
        int protocol = i % 3 == 0 ? IPPROTO_TCP : i % 3 == 1 ? IPPROTO_UDP : IPPROTO_ICMP;
        index_key(records[i], 4, protocol, 1000 + i);
        add_session(args->ctx, records[i]);
    }
    for (int i = 0; i < count; i++) assert(index_lookup(args->ctx, records[i]) == records[i]);
    clear(args->ctx); assert_index_empty(args->ctx);
    assert(allocations == 0);
    struct ng_session key = {0}; index_key(&key, 4, IPPROTO_TCP, 1000);
    assert(index_lookup(args->ctx, &key) == NULL);
    struct ng_session *fresh = ng_calloc(1, sizeof(*fresh), "restarted index");
    assert(fresh != NULL); index_key(fresh, 4, IPPROTO_TCP, 1000);
    add_session(args->ctx, fresh); assert(index_lookup(args->ctx, &key) == fresh);
    clear(args->ctx); clear(args->ctx); assert_index_empty(args->ctx);
    free(records);
    assert(allocations == 0 && sockets == 0);
#endif
}

static struct context *idle_context;
static struct ng_session *idle_sessions;
static long long idle_clock;
static int idle_polls, idle_timeout;
static int closed_cache_test;
static void poll_closed_cache(void);
long long tcp_test_clock;
#ifndef INVIZIBLE_BASELINE_TESTS
static struct arguments *buffer_loop_args;
static int buffer_loop_kind;
static struct context *probe_context;
static struct ng_session *probe_sessions;
static int probe_polls;
static struct context *memory_context;
static struct ng_session *memory_sessions;
static int memory_polls;
static int memory_kind;
#endif

long long __real_get_ms(void);
long long __wrap_get_ms(void) {
    if (tcp_test_clock != 0)
        return tcp_test_clock;
    if (idle_context != NULL) {
        idle_clock += EPOLL_MIN_CHECK + 1;
        return idle_clock;
    }
    return __real_get_ms();
}

int __real_epoll_wait(int fd, struct epoll_event *events, int count, int timeout);
int __wrap_epoll_wait(int fd, struct epoll_event *events, int count, int timeout) {
#ifndef INVIZIBLE_BASELINE_TESTS
    if (memory_context != NULL) {
        memory_polls++;
        if (memory_polls == 1) {
            assert(memory_context->ng_session == NULL);
            set_test_sessions(memory_context, memory_sessions);
            assert(epoll_ctl(fd, EPOLL_CTL_ADD, memory_sessions->socket, &memory_sessions->ev) == 0);
            tcp_test_clock += EPOLL_MIN_CHECK + 1;
        } else if (memory_polls == 2) {
            bool rescan = memory_kind == 0 || memory_kind == 4;
            assert(rescan ? timeout == EPOLL_MIN_CHECK : timeout > EPOLL_MIN_CHECK);
            memory_context->tcp_memory.queued = 0; // Remove simulated unrelated occupancy.
            memory_context->tcp_memory.dns = 0;
            if (!rescan)
                memory_context->stopping = 1;
            tcp_test_clock += timeout + 1;
        } else {
            assert(memory_polls == 3 && timeout > EPOLL_MIN_CHECK);
            assert(memory_kind == 0 ? memory_sessions->tcp.recv_window > 0
                                     : !memory_sessions->tcp.dns_waiting);
            memory_context->stopping = 1;
        }
        return 0;
    }
    if (probe_context != NULL) {
        probe_polls++;
        if (probe_polls == 1) {
            assert(probe_context->ng_session == NULL);
            set_test_sessions(probe_context, probe_sessions);
            tcp_test_clock += EPOLL_MIN_CHECK + 1;
            for (struct ng_session *s = probe_sessions; s != NULL; s = s->next)
                s->tcp.last_keep_alive = tcp_test_clock;
        } else if (probe_polls == 2) {
            assert(timeout == 1000);
            tcp_test_clock += timeout;
        } else if (probe_polls == 3) {
            assert(timeout == 2000);
            tcp_test_clock += timeout;
        } else {
            assert(probe_polls == 4 && timeout == 1000);
            // The stop pipe still interrupts a long probe wait immediately.
            assert(write(probe_context->pipefds[1], "w", 1) == 1);
            probe_context->stopping = 1;
            int ready = __real_epoll_wait(fd, events, count, 0);
            assert(ready == 1 && (events[0].events & EPOLLIN));
            return ready;
        }
        return 0;
    }
    if (buffer_loop_args != NULL) {
        assert(buffer_loop_args->buffers != NULL);
        assert(get_packet_buffer(buffer_loop_args, PACKET_INPUT, 4096, "loop input") != NULL);
        struct tcp_session tcp = {.version = 4, .recv_window = 4096};
        assert(write_ack(buffer_loop_args, &tcp) >= 0);
        assert(allocations == 3); // Loop arguments plus the two scratch buffers.
        if (buffer_loop_kind == 0) {
            buffer_loop_args->ctx->stopping = 1;
            return 0;
        }
        errno = EIO;
        return -1;
    }
#endif
    if (idle_context == NULL)
        return __real_epoll_wait(fd, events, count, timeout);

    idle_polls++;
    if (closed_cache_test) {
        poll_closed_cache();
        return 0;
    }
    if (idle_polls == 1) {
        // Introduce sessions after startup's policy check, as traffic would.
        assert(idle_context->ng_session == NULL);
        set_test_sessions(idle_context, idle_sessions);
        idle_sessions = NULL;
    } else if (idle_polls == 2) {
        idle_timeout = timeout;
        assert(idle_context->ng_session != NULL);
        // Simulate expiry without waiting minutes on the device.
        for (struct ng_session *s = idle_context->ng_session; s != NULL; s = s->next) {
            if (s->protocol == IPPROTO_TCP)
                s->tcp.time = time(NULL) - TCP_KEEP_TIMEOUT - 1;
            else
                s->udp.time = time(NULL) - UDP_KEEP_TIMEOUT - 1;
        }
    } else {
        assert(idle_polls == 3 && idle_context->ng_session == NULL);
        assert_test_session_index(idle_context);
        idle_context->stopping = 1;
    }
    return 0;
}

void test_tcp_memory_rescan(struct arguments *args, int kind) {
#ifndef INVIZIBLE_BASELINE_TESTS
    real_network = 1;
    int tunnel[2], stream[2];
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, stream) == 0);
    assert(pipe(args->ctx->pipefds) == 0);
    struct ng_session *blocked = ng_calloc(1, sizeof(*blocked), "memory blocked session");
    struct ng_session *closing = ng_calloc(1, sizeof(*closing), "memory closing session");
    assert(blocked != NULL && closing != NULL);
    blocked->protocol = closing->protocol = IPPROTO_TCP;
    blocked->socket = stream[0]; closing->socket = -1;
    blocked->tcp.version = closing->tcp.version = 4;
    blocked->tcp.state = TCP_ESTABLISHED;
    blocked->tcp.time = time(NULL); blocked->tcp.send_window = 16000;
    blocked->tcp.memory = &args->ctx->tcp_memory;
    closing->tcp.recv_window = 16000;
    struct tcphdr header = {.seq = htonl(0)};
    assert(queue_tcp(args, &header, "cleanup releases budget", &closing->tcp, (void *) "a", 1) == 1);
    closing->tcp.state = TCP_CLOSING;
    blocked->tcp.memory_waiting = true;
    args->ctx->tcp_memory.queued = TCP_MEMORY_QUEUE_LIMIT;
    if (kind == 1 || kind == 3 || kind == 4 || kind == 5) {
        blocked->tcp.memory_waiting = false;
        blocked->tcp.recv_window = kind == 5 ? 0 : 16000;
        args->ctx->tcp_memory.queued = closing->tcp.queue_bytes + sizeof(struct segment);
    }
    if (kind == 2 || kind == 4) {
        clear_tcp_data(&closing->tcp);
        closing->tcp.dns_response = ng_malloc(1, "cleanup DNS release");
        assert(closing->tcp.dns_response != NULL);
        closing->tcp.dns_response_capacity = 1;
    }
    if (kind == 3 || kind == 4) {
        blocked->tcp.dns_waiting = true;
        blocked->tcp.dns_memory_generation = args->ctx->tcp_memory.dns_generation;
        args->ctx->tcp_memory.dns = TCP_MEMORY_DNS_LIMIT;
    }
    if (kind == 2) {
        args->ctx->tcp_memory.dns = 1;
        args->ctx->tcp_memory.queued = TCP_MEMORY_QUEUE_LIMIT;
    }
    blocked->tcp.memory_generation = args->ctx->tcp_memory.generation;
    if (kind == 5) {
        tcp_capacity_fd = blocked->socket;
        tcp_capacity_available = 0;
    }
    blocked->next = closing;
    blocked->ev.events = EPOLLERR | EPOLLIN; blocked->ev.data.ptr = blocked;
    memory_sessions = blocked; memory_context = args->ctx; memory_polls = 0; memory_kind = kind;
    tcp_test_clock = 100000;
    struct arguments *owned = ng_malloc(sizeof(*owned), "memory loop arguments");
    assert(owned != NULL);
    *owned = *args; owned->tun = tunnel[0];
    handle_events(owned);
    assert(memory_polls == (kind == 0 || kind == 4 ? 3 : 2));
    memory_context = NULL; memory_sessions = NULL; tcp_test_clock = 0;
    tcp_capacity_fd = -1;
    uint8_t packet[128];
    if (kind == 0)
        tun_packet(tunnel[1], packet, 4, IPPROTO_TCP);
    assert(!is_readable(tunnel[1]));
    clear(args->ctx);
    assert(args->ctx->tcp_memory.queued == 0 && args->ctx->tcp_memory.nodes == 0);
    close(stream[1]); close(tunnel[0]); close(tunnel[1]);
    close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_tcp_probe_deadlines(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int tunnel[2], streams[2][2];
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    assert(pipe(args->ctx->pipefds) == 0);
    tcp_test_clock = 100000;
    probe_sessions = NULL;
    for (int i = 0; i < 2; i++) {
        assert(socketpair(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0, streams[i]) == 0);
        struct ng_session *s = ng_calloc(1, sizeof(*s), "probe deadline session");
        assert(s != NULL);
        s->protocol = IPPROTO_TCP; s->socket = streams[i][0];
        s->tcp.version = 4; s->tcp.state = TCP_ESTABLISHED; s->tcp.time = time(NULL);
        s->tcp.recv_window = 16000; // Only the app's receive side is blocked in this fixture.
        s->tcp.last_keep_alive = tcp_test_clock;
        s->tcp.window_probe_interval = i == 0 ? 1000 : 4000;
        s->ev.events = EPOLLERR; s->ev.data.ptr = s;
        s->next = probe_sessions; probe_sessions = s;
    }
    args->ctx->stopping = 0;
    struct arguments *owned = ng_malloc(sizeof(*owned), "probe loop arguments");
    assert(owned != NULL);
    *owned = *args; owned->tun = tunnel[0];
    probe_context = args->ctx; probe_polls = 0;
    handle_events(owned);
    assert(probe_polls == 4);
    probe_context = NULL; probe_sessions = NULL; tcp_test_clock = 0;
    clear(args->ctx);
    close(streams[0][1]); close(streams[1][1]);
    close(tunnel[0]); close(tunnel[1]);
    close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_event_buffer_cleanup(struct arguments *args) {
#ifndef INVIZIBLE_BASELINE_TESTS
    for (int kind = 0; kind < 2; kind++) {
        int tunnel[2];
        assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
        assert(pipe(args->ctx->pipefds) == 0);
        args->ctx->stopping = 0;
        struct arguments *owned = ng_malloc(sizeof(*owned), "test arguments");
        *owned = *args;
        owned->tun = tunnel[0];
        buffer_loop_kind = kind;
        buffer_loop_args = owned;
        handle_events(owned);
        buffer_loop_args = NULL;
        assert(allocations == 0 && sockets == 0 && args->ctx->ng_session == NULL);
        assert(exit_reports == kind);
        close(tunnel[0]); close(tunnel[1]);
        close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
    }
#endif
}

static struct ng_session *lingering_session(int protocol, int state, int age) {
    struct ng_session *s = ng_calloc(1, sizeof(*s), "test lingering session");
    s->protocol = protocol;
    s->socket = -1;
    s->ev.events = EPOLLERR;
    if (protocol == IPPROTO_TCP) {
        s->tcp.version = 4;
        s->tcp.state = state;
        s->tcp.time = time(NULL) - age;
        if (state == TCP_CLOSING)
            s->socket = socket(AF_INET, SOCK_STREAM, 0);
    } else {
        s->udp.version = 4;
        s->udp.state = state;
        s->udp.time = time(NULL) - age;
        if (state == UDP_FINISHING)
            s->socket = socket(AF_INET, SOCK_DGRAM, 0);
    }
    return s;
}

#ifdef SESSION_CLOSED_MAX
static struct ng_session *cache_active, *cache_blocked;
static int cache_kind, cache_uid, cache_closing_fd;
int __real_fcntl(int fd, int cmd, ...);

static struct ng_session *closed_record(int closing) {
    int tcp = cache_kind == 1 || (cache_kind == 2 && cache_uid % 2);
    struct ng_session *s = lingering_session(tcp ? IPPROTO_TCP : IPPROTO_UDP,
            tcp ? (closing ? TCP_CLOSING : TCP_CLOSE) : (closing ? UDP_FINISHING : UDP_CLOSED), 0);
    if (tcp) {
        s->tcp.uid = ++cache_uid;
        s->tcp.sent = 1;
        s->tcp.received = 2;
        s->tcp.forward = ng_calloc(1, sizeof(struct segment), "test closed segment");
        s->tcp.forward->len = 1;
        s->tcp.forward->data = ng_malloc(1, "test closed data");
    } else {
        s->udp.uid = ++cache_uid;
        s->udp.sent = 1;
        s->udp.received = 2;
    }
    return s;
}

static void poll_closed_cache(void) {
    if (idle_polls == 1) {
        assert(idle_context->ng_session == NULL);
        set_test_sessions(idle_context, idle_sessions);
        idle_sessions = NULL;
        return;
    }
    assert(idle_polls == 2 || idle_polls == 3);
    int closed = 0, active = 0, blocked = 0;
    for (struct ng_session *s = idle_context->ng_session; s != NULL; s = s->next) {
        if (s == cache_active) {
            assert(s->udp.state == UDP_ACTIVE && __real_fcntl(s->socket, F_GETFD) >= 0);
            active++;
        } else if (s == cache_blocked) {
            assert(s->udp.state == UDP_BLOCKED);
            blocked++;
        } else {
            int uid;
            if (s->protocol == IPPROTO_TCP) {
                assert(s->tcp.state == TCP_CLOSE && s->tcp.forward == NULL);
                assert(s->tcp.sent == 0 && s->tcp.received == 0);
                uid = s->tcp.uid;
            } else {
                assert(s->udp.state == UDP_CLOSED);
                assert(s->udp.sent == 0 && s->udp.received == 0);
                uid = s->udp.uid;
            }
            assert(s->socket == -1 && uid > cache_uid - SESSION_CLOSED_MAX);
            closed++;
        }
    }
    assert(closed == SESSION_CLOSED_MAX && active == 1 && blocked == 1);
    assert_test_session_index(idle_context);
    assert(allocations == SESSION_CLOSED_MAX + 3); // Records plus loop arguments.
    assert(accounted_sent == cache_uid && accounted_received == 2 * cache_uid);
    assert(sockets == 0);
    assert(__real_fcntl(cache_closing_fd, F_GETFD) == -1 && errno == EBADF);
    if (idle_polls == 2) {
        // A second burst must replace old cached records and stay bounded.
        for (int i = 0; i < 64; i++) {
            struct ng_session *s = closed_record(0);
            s->next = idle_context->ng_session;
            set_test_sessions(idle_context, s);
        }
    } else
        idle_context->stopping = 1;
}
#else
static void poll_closed_cache(void) {}
#endif

void test_closed_session_limit(struct arguments *args, int kind) {
#ifdef SESSION_CLOSED_MAX
    int tun[2];
    assert(pipe(tun) == 0 && pipe(args->ctx->pipefds) == 0);
    cache_kind = kind;
    cache_uid = 0;
    cache_active = lingering_session(IPPROTO_UDP, UDP_ACTIVE, 0);
    cache_active->udp.dest = htons(5000);
    cache_active->socket = dup(tun[0]);
    assert(cache_active->socket >= 0);
    cache_blocked = lingering_session(IPPROTO_UDP, UDP_BLOCKED, 0);
    cache_active->next = cache_blocked;
    idle_sessions = cache_active;
    for (int i = 0; i < SESSION_CLOSED_MAX + 64; i++) {
        struct ng_session *s = closed_record(0);
        s->next = idle_sessions;
        idle_sessions = s;
    }
    struct ng_session *closing = closed_record(1);
    cache_closing_fd = closing->socket;
    closing->next = idle_sessions;
    idle_sessions = closing;

    struct arguments *owned = ng_malloc(sizeof(*owned), "test arguments");
    *owned = *args;
    owned->tun = tun[0];
    idle_context = args->ctx;
    idle_clock = idle_polls = 0;
    closed_cache_test = 1;
    handle_events(owned);
    closed_cache_test = 0;
    idle_context = NULL;
    assert(idle_polls == 3);
    clear(args->ctx);
    close(tun[0]); close(tun[1]);
    close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
    assert(allocations == 0 && sockets == 0);
#endif
}

void test_event_loop_idle_cleanup(struct arguments *args, int kind) {
    int max_timeout = 6000;
    if (kind == 0)
        idle_sessions = lingering_session(IPPROTO_UDP, UDP_CLOSED, UDP_KEEP_TIMEOUT - 5);
    else if (kind == 1)
        idle_sessions = lingering_session(IPPROTO_UDP, UDP_BLOCKED, UDP_KEEP_TIMEOUT - 5);
    else if (kind == 2)
        idle_sessions = lingering_session(IPPROTO_TCP, TCP_CLOSE, TCP_KEEP_TIMEOUT - 5);
    else if (kind == 3) {
        idle_sessions = lingering_session(IPPROTO_UDP, UDP_FINISHING, 0);
        max_timeout = (UDP_KEEP_TIMEOUT + 1) * 1000;
    } else if (kind == 4) {
        idle_sessions = lingering_session(IPPROTO_TCP, TCP_CLOSING, 0);
        max_timeout = (TCP_KEEP_TIMEOUT + 1) * 1000;
    } else {
        idle_sessions = lingering_session(IPPROTO_TCP, TCP_CLOSE, 0);
        idle_sessions->next = lingering_session(IPPROTO_UDP, UDP_CLOSED, UDP_KEEP_TIMEOUT - 5);
    }

    int tun[2];
    assert(pipe(tun) == 0 && pipe(args->ctx->pipefds) == 0);
    struct arguments *owned = ng_malloc(sizeof(*owned), "test arguments");
    *owned = *args;
    owned->tun = tun[0];
    idle_context = args->ctx;
    idle_clock = idle_polls = 0;
    idle_timeout = -1;
    handle_events(owned);
    idle_context = NULL;
    assert(idle_polls == 3 && idle_timeout > 0 && idle_timeout <= max_timeout);
    assert(args->ctx->ng_session == NULL && allocations == 0 && sockets == 0);
    close(tun[0]); close(tun[1]);
    close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
}

void test_event_loop_shutdown(struct arguments *args) {
    // Run shutdown with an already stopped loop; ASan checks the final log.
    assert(pipe(args->ctx->pipefds) == 0);
    args->ctx->stopping = 1;
    struct arguments *owned = ng_malloc(sizeof(*owned), "test arguments");
    *owned = *args;
    handle_events(owned);
    close(args->ctx->pipefds[0]); close(args->ctx->pipefds[1]);
    assert(allocations == 0);
}
