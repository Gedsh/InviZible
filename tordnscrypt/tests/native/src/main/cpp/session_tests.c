#include "test_support.h"

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
