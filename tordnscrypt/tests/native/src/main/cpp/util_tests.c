#include "test_support.h"

void test_bounded_logging(void) {
    char message[16384];
    memset(message, 'x', sizeof(message) - 1);
    message[sizeof(message) - 1] = 0;
    log_android(ANDROID_LOG_ERROR, "%s", message);
    assert(strlen(last_log) == 1023);
    assert(strncmp(last_log, message, 1023) == 0);
    fail_alloc_tag = "hex";
    fail_alloc_match = 1;
    assert(hex((const uint8_t *) message, 16) == NULL && fail_alloc_match == 0);
    assert(hex((const uint8_t *) message, SIZE_MAX) == NULL);
}
