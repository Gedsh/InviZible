#include "test_support.h"

static struct context *idle_context;
static struct ng_session *idle_sessions;
static long long idle_clock;
static int idle_polls, idle_timeout;
static int closed_cache_test;
static void poll_closed_cache(void);

long long __real_get_ms(void);
long long __wrap_get_ms(void) {
    if (idle_context != NULL) {
        idle_clock += EPOLL_MIN_CHECK + 1;
        return idle_clock;
    }
    return __real_get_ms();
}

int __real_epoll_wait(int fd, struct epoll_event *events, int count, int timeout);
int __wrap_epoll_wait(int fd, struct epoll_event *events, int count, int timeout) {
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
