#include "test_support.h"

void test_lazy_logging(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    int evaluations = 0;
    log_android(ANDROID_LOG_DEBUG, "%d", ++evaluations);
    assert(evaluations == 0);
    int priority = ANDROID_LOG_ERROR;
    log_android(priority++, "enabled %d", ++evaluations);
    assert(priority == ANDROID_LOG_ERROR + 1 && evaluations == 1);
    assert(strcmp(last_log, "enabled 1") == 0);
    for (int version = 4; version <= 6; version += 2) {
        const char *expected = version == 4 ? "203.0.113.2" : "2001:db8::2";
        uint8_t binary[16];
        assert(inet_pton(version == 4 ? AF_INET : AF_INET6, expected, binary) == 1);
        struct log_address address = {.version = version, .address = binary};
        address_formats = 0;
        log_android(ANDROID_LOG_INFO, "%s", format_log_address(&address));
        assert(address_formats == 0 && address.text[0] == 0);
        errno = EPIPE;
        log_android(ANDROID_LOG_ERROR, "%s", format_log_address(&address));
        assert(errno == EPIPE && address_formats == 1 && strcmp(last_log, expected) == 0);
        assert(strcmp(format_log_address(&address), expected) == 0 && address_formats == 1);
    }
#endif
}

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
