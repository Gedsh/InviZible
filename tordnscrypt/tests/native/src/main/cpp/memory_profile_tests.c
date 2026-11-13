// Include the production tracker to inspect its private state without test APIs.
#include "invizible.c"
#include <sched.h>
#include <stdarg.h>

static atomic_int live_allocations;
static atomic_int fail_malloc, fail_calloc, fail_realloc, fail_lock;
static atomic_int disabled_logs, missing_logs, lock_logs;

void *__real_malloc(size_t size);
void *__real_calloc(size_t count, size_t size);
void *__real_realloc(void *ptr, size_t size);
void __real_free(void *ptr);
int __real_pthread_mutex_lock(pthread_mutex_t *mutex);

void *__wrap_malloc(size_t size) {
    if (atomic_exchange(&fail_malloc, 0)) {
        errno = ENOMEM;
        return NULL;
    }
    void *ptr = __real_malloc(size);
    if (ptr != NULL) live_allocations++;
    return ptr;
}

void *__wrap_calloc(size_t count, size_t size) {
    if (atomic_exchange(&fail_calloc, 0)) {
        errno = ENOMEM;
        return NULL;
    }
    void *ptr = __real_calloc(count, size);
    if (ptr != NULL) live_allocations++;
    return ptr;
}

void *__wrap_realloc(void *ptr, size_t size) {
    if (atomic_exchange(&fail_realloc, 0)) {
        errno = ENOMEM;
        return NULL;
    }
    void *result = __real_realloc(ptr, size);
    if (result != NULL && ptr == NULL) live_allocations++;
    return result;
}

void __wrap_free(void *ptr) {
    if (ptr != NULL) live_allocations--;
    __real_free(ptr);
}

int __wrap_pthread_mutex_lock(pthread_mutex_t *mutex) {
    if (mutex == &alock && atomic_exchange(&fail_lock, 0))
        return EBUSY;
    return __real_pthread_mutex_lock(mutex);
}

int __android_log_print(int priority, const char *tag, const char *fmt, ...) {
    // util.c prints the formatted message through "%s".
    va_list args;
    va_start(args, fmt);
    const char *text = va_arg(args, const char *);
    if (strstr(text, "PROFILE_MEMORY disabled") != NULL) disabled_logs++;
    if (strstr(text, "Not found at") != NULL) missing_logs++;
    if (strstr(text, "PROFILE_MEMORY pthread_mutex_lock error") != NULL) lock_logs++;
    va_end(args);
    errno = EIO; // Diagnostic output must not change the caller's errno.
    return 0;
}

#define CHECK(condition) do { if (!(condition)) return #condition; } while (0)

static atomic_int worker_errors;
static atomic_bool start_workers;
static void *allocation_worker(void *unused) {
    while (!atomic_load(&start_workers)) sched_yield();
    for (int i = 0; i < 128; i++) {
        char *first = ng_malloc(32, "concurrent first");
        char *second = ng_calloc(16, 2, "concurrent second");
        if (first == NULL || second == NULL) worker_errors++;
        ng_free(first, __FILE__, __LINE__);
        ng_free(second, __FILE__, __LINE__);
    }
    return NULL;
}

static const char *run_profile_case(int test) {
    CHECK(live_allocations == 0 && alloc == NULL && balance == 0 && allocs == 0);
    tracking_disabled = false;
    disabled_logs = missing_logs = lock_logs = 0;
    fail_malloc = fail_calloc = fail_realloc = fail_lock = 0;
    if (test == 0 || test == 1) {
        char *first = test == 1 ? ng_malloc(16, "existing") : NULL;
        CHECK(test == 0 || first != NULL);
        if (first != NULL) memset(first, 0x5a, 16);
        fail_realloc = 1; // First record or growth of an existing record table.
        errno = ENETUNREACH;
        char *second = ng_malloc(32, "unrecorded");
        CHECK(second != NULL && errno == ENETUNREACH && tracking_disabled);
        CHECK(alloc == NULL && balance == 0 && allocs == 0 && disabled_logs == 1);
        CHECK(first == NULL || first[0] == 0x5a);
        ng_free(first, __FILE__, __LINE__);
        ng_free(second, __FILE__, __LINE__);
        char *later = ng_calloc(1, 8, "after disable");
        CHECK(later != NULL && alloc == NULL);
        ng_free(later, __FILE__, __LINE__);
        CHECK(missing_logs == 0 && disabled_logs == 1);
    } else if (test == 2) {
        fail_malloc = 1;
        CHECK(ng_malloc(16, "failed payload") == NULL && errno == ENOMEM);
        fail_calloc = 1;
        CHECK(ng_calloc(1, 16, "failed payload") == NULL && errno == ENOMEM);
        CHECK(alloc == NULL && !tracking_disabled);
        char *ptr = ng_malloc(16, "original");
        CHECK(ptr != NULL && balance == 1);
        memset(ptr, 0x5a, 16);
        fail_realloc = 1;
        CHECK(ng_realloc(ptr, 64, "failed resize") == NULL && errno == ENOMEM);
        CHECK(ptr[0] == 0x5a && balance == 1 && !tracking_disabled);
        char *resized = ng_realloc(ptr, 64, "successful resize");
        CHECK(resized != NULL && resized[0] == 0x5a && balance == 1);
        ptr = resized;
        ng_free(ptr, __FILE__, __LINE__);
        CHECK(missing_logs == 0);
    } else if (test == 3) {
        char untracked;
        errno = ENETUNREACH;
        ng_delete_alloc(NULL, NULL, 0);
        ng_delete_alloc(&untracked, NULL, 0);
        CHECK(errno == ENETUNREACH && missing_logs == 1 && alloc == NULL);
        ng_add_alloc(NULL, "null");
        char *ptr = ng_malloc(16, "retry");
        CHECK(ptr != NULL && balance == 1 && errno == ENETUNREACH);
        ng_free(ptr, __FILE__, __LINE__);
        CHECK(errno == ENETUNREACH);
    } else if (test == 4) {
        char value;
        errno = ENETUNREACH;
        fail_lock = 1;
        ng_add_alloc(&value, "lock failure");
        CHECK(alloc == NULL && balance == 0 && lock_logs == 1 && errno == ENETUNREACH);
        ng_add_alloc(&value, "retry");
        CHECK(balance == 1);
        fail_lock = 1;
        ng_delete_alloc(&value, __FILE__, __LINE__);
        CHECK(balance == 1 && lock_logs == 2 && errno == ENETUNREACH);
        ng_delete_alloc(&value, __FILE__, __LINE__);
    } else if (test == 5) {
        pthread_t workers[8];
        int created = 0;
        worker_errors = 0;
        start_workers = false;
        for (; created < 8; created++)
            if (pthread_create(&workers[created], NULL, allocation_worker, NULL) != 0)
                break;
        start_workers = true;
        int join_errors = 0;
        for (int i = 0; i < created; i++)
            if (pthread_join(workers[i], NULL) != 0) join_errors++;
        CHECK(created == 8 && join_errors == 0 && worker_errors == 0);
        CHECK(missing_logs == 0 && disabled_logs == 0 && lock_logs == 0);
    } else {
        return "Unknown memory profile case";
    }
    CHECK(live_allocations == 0 && alloc == NULL && balance == 0 && allocs == 0);
    return NULL;
}

JNIEXPORT jstring JNICALL
Java_pan_alexander_tordnscrypt_nativetests_MemoryProfileTest_runProfileCase(
        JNIEnv *env, jclass cls, jint test) {
    const char *failure = run_profile_case(test);
    return failure == NULL ? NULL : (*env)->NewStringUTF(env, failure);
}
