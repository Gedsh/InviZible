#include "test_support.h"

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
            memory_context->ng_session = memory_sessions;
            assert(epoll_ctl(fd, EPOLL_CTL_ADD, memory_sessions->socket, &memory_sessions->ev) == 0);
            tcp_test_clock += EPOLL_MIN_CHECK + 1;
        } else if (memory_polls == 2) {
            // Cleanup ran after the blocked flow's monitor pass. It must cause
            // one short rescan, even without TUN or socket readiness events.
            assert(timeout == EPOLL_MIN_CHECK && memory_sessions->tcp.recv_window == 0);
            assert(memory_context->tcp_memory.queued < TCP_MEMORY_QUEUE_LIMIT);
            memory_context->tcp_memory.queued = 0; // Remove simulated unrelated occupancy.
            tcp_test_clock += timeout + 1;
        } else {
            assert(memory_polls == 3 && timeout > EPOLL_MIN_CHECK);
            assert(memory_sessions->tcp.recv_window > 0);
            memory_context->stopping = 1;
        }
        return 0;
    }
    if (probe_context != NULL) {
        probe_polls++;
        if (probe_polls == 1) {
            assert(probe_context->ng_session == NULL);
            probe_context->ng_session = probe_sessions;
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
        idle_context->ng_session = idle_sessions;
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
        idle_context->stopping = 1;
    }
    return 0;
}

void test_tcp_memory_rescan(struct arguments *args) {
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
    blocked->tcp.memory_generation = args->ctx->tcp_memory.generation;
    args->ctx->tcp_memory.queued = TCP_MEMORY_QUEUE_LIMIT;
    blocked->next = closing;
    blocked->ev.events = EPOLLERR | EPOLLIN; blocked->ev.data.ptr = blocked;
    memory_sessions = blocked; memory_context = args->ctx; memory_polls = 0;
    tcp_test_clock = 100000;
    struct arguments *owned = ng_malloc(sizeof(*owned), "memory loop arguments");
    assert(owned != NULL);
    *owned = *args; owned->tun = tunnel[0];
    handle_events(owned);
    assert(memory_polls == 3);
    memory_context = NULL; memory_sessions = NULL; tcp_test_clock = 0;
    uint8_t packet[128];
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
        idle_context->ng_session = idle_sessions;
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
    assert(allocations == SESSION_CLOSED_MAX + 3); // Records plus loop arguments.
    assert(accounted_sent == cache_uid && accounted_received == 2 * cache_uid);
    assert(sockets == 0);
    assert(__real_fcntl(cache_closing_fd, F_GETFD) == -1 && errno == EBADF);
    if (idle_polls == 2) {
        // A second burst must replace old cached records and stay bounded.
        for (int i = 0; i < 64; i++) {
            struct ng_session *s = closed_record(0);
            s->next = idle_context->ng_session;
            idle_context->ng_session = s;
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
