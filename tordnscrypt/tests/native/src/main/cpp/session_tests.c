#include "test_support.h"

static struct context *idle_context;
static struct ng_session *idle_sessions;
static long long idle_clock;
static int idle_polls, idle_timeout;

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
