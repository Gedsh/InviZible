#include "test_support.h"
#include <sys/time.h>

#ifndef INVIZIBLE_BASELINE_TESTS
static bool wall_clock_enabled, wall_time_failure;
static unsigned int wall_clock_fail_mask;
static int wall_clock_calls, wall_time_calls;
int __real_gettimeofday(struct timeval *, void *);
int __wrap_gettimeofday(struct timeval *tv, void *tz) {
    if (!wall_clock_enabled)
        return __real_gettimeofday(tv, tz);
    wall_clock_calls++;
    tv->tv_sec = 1700000000;
    tv->tv_usec = 987654;
    if (wall_clock_fail_mask & (1U << (wall_clock_calls - 1))) {
        // Failed reads must not contribute partially written output to timestamps.
        tv->tv_sec = 123;
        tv->tv_usec = 456;
        errno = EIO;
        return -1;
    }
    return 0;
}
time_t __real_time(time_t *);
time_t __wrap_time(time_t *result) {
    if (!wall_clock_enabled)
        return __real_time(result);
    wall_time_calls++;
    time_t value = wall_time_failure ? (time_t) -1 : (time_t) 1700000123;
    if (result != NULL)
        *result = value;
    if (wall_time_failure)
        errno = EFAULT;
    return value;
}
#endif

// These callbacks are mocked throughout the native test library; their cached
// method/field IDs belong to this JNI table, never to the real device VM.
extern jclass clsPacket, clsAllowed, clsRR, clsUsage;
extern jfieldID fidAllowed, fidRport, fidRcode, fidUsageReceived;
jobject __real_create_packet(const struct arguments *, jint, jint, const char *,
        const char *, jint, const char *, jint, const char *, jint, jboolean);
struct allowed *__real_is_address_allowed(const struct arguments *, jobject);
void __real_dns_resolved(const struct arguments *, const char *, const char *, const char *,
        const char *, const char *, int);
void __real_account_usage(const struct arguments *, jint, jint, const char *, jint, jint,
        jlong, jlong);

enum fault { NO_FAULT, CLASS_REF, OBJECT_REF, STRING_REF, UTF_CHARS, FIELD_ID, INT_CALL, BOOL_FIELD, INT_FIELD };
static enum fault fault;
static int nth, seen, hit, pending, ref_count, ref_index, callbacks, writes;
static int live[64];
static char address[128];
static jlong last_long;
static jint returned_uid;
jint __real_get_uid_q(const struct arguments *, jint, jint, const char *, jint, const char *, jint);

static jobject new_ref(void) {
    assert(++ref_index < 64);
    live[ref_index] = 1;
    ref_count++;
    return (jobject) &live[ref_index];
}
static int fails(enum fault operation) {
    assert(!pending);
    if (fault == operation && ++seen == nth) {
        pending = hit = 1;
        return 1;
    }
    return 0;
}
static void JNICALL release_ref(JNIEnv *env, jobject ref) {
    if (ref == NULL)
        return;
    int *entry = (int *) ref;
    assert(entry > live && entry < live + 64 && *entry == 1);
    *entry = 0;
    ref_count--;
}
static jclass JNICALL object_class(JNIEnv *env, jobject object) {
    return fails(CLASS_REF) ? NULL : (jclass) new_ref();
}
static jclass JNICALL throwable_class(JNIEnv *env, const char *name) {
    assert(!pending);
    return (jclass) new_ref();
}
static jmethodID JNICALL method_id(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    assert(!pending && cls != NULL);
    return (jmethodID) (uintptr_t) (strcmp(name, "getMessage") == 0 ? 1 : 2);
}
static jfieldID JNICALL field_id(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    assert(cls != NULL);
    return fails(FIELD_ID) ? NULL : (jfieldID) 1;
}
static jobject JNICALL new_object(JNIEnv *env, jclass cls, jmethodID method, ...) {
    assert(cls != NULL && method != NULL);
    return fails(OBJECT_REF) ? NULL : new_ref();
}
static jstring JNICALL new_string(JNIEnv *env, const char *text) {
    assert(text != NULL);
    return fails(STRING_REF) ? NULL : (jstring) new_ref();
}
static jobject JNICALL object_field(JNIEnv *env, jobject object, jfieldID field) {
    assert(!pending && object != NULL && field != NULL);
    return new_ref();
}
static jint JNICALL int_field(JNIEnv *env, jobject object, jfieldID field) {
    assert(!pending && object != NULL && field != NULL);
    fails(INT_FIELD);
    return 5354;
}
static jboolean JNICALL bool_field(JNIEnv *env, jobject object, jfieldID field) {
    assert(!pending && object != NULL && field != NULL);
    fails(BOOL_FIELD);
    return JNI_TRUE;
}
static const char *JNICALL utf_chars(JNIEnv *env, jstring string, jboolean *copy) {
    assert(string != NULL);
    if (fails(UTF_CHARS))
        return NULL;
    string_refs++;
    return address;
}
static void JNICALL release_utf(JNIEnv *env, jstring string, const char *text) {
    assert(string != NULL && text == address && string_refs > 0);
    string_refs--;
}
static jobject JNICALL call_object(JNIEnv *env, jobject object, jmethodID method, ...) {
    assert(!pending && object != NULL && method != NULL);
    if (method == (jmethodID) 1)
        return NULL; // Throwable.getMessage: avoid further allocations in the reporter.
    callbacks++;
    return new_ref();
}
static void JNICALL call_void(JNIEnv *env, jobject object, jmethodID method, ...) {
    assert(!pending && object != NULL && method != NULL);
    callbacks++;
}
static jint JNICALL call_int(JNIEnv *env, jobject object, jmethodID method, ...) {
    assert(!pending && object != NULL && method != NULL);
    callbacks++;
    fails(INT_CALL);
    // A throwing JNI call's result is untrusted, even if it resembles a valid UID.
    return returned_uid;
}
static jthrowable JNICALL exception(JNIEnv *env) {
    return pending ? (jthrowable) new_ref() : NULL;
}
static jboolean JNICALL exception_check(JNIEnv *env) { return pending; }
static void JNICALL exception_clear(JNIEnv *env) { pending = 0; }
static void set_field(jobject object, jfieldID field) {
    assert(!pending && object != NULL && field != NULL);
    writes++;
}
static void JNICALL set_long(JNIEnv *env, jobject object, jfieldID field, jlong value) {
    set_field(object, field);
    last_long = value;
}
static void JNICALL set_int(JNIEnv *env, jobject object, jfieldID field, jint value) { set_field(object, field); }
static void JNICALL set_bool(JNIEnv *env, jobject object, jfieldID field, jboolean value) { set_field(object, field); }
static void JNICALL set_object(JNIEnv *env, jobject object, jfieldID field, jobject value) {
    assert(value != NULL);
    set_field(object, field);
}
static const struct JNINativeInterface mock_jni = {
    .GetObjectClass = object_class, .FindClass = throwable_class,
    .GetMethodID = method_id, .GetFieldID = field_id, .NewObject = new_object,
    .NewStringUTF = new_string, .GetObjectField = object_field, .GetIntField = int_field,
    .GetBooleanField = bool_field,
    .GetStringUTFChars = utf_chars, .ReleaseStringUTFChars = release_utf,
    .CallObjectMethod = call_object, .CallVoidMethod = call_void,
    .CallIntMethod = call_int,
    .ExceptionOccurred = exception, .ExceptionCheck = exception_check,
    .ExceptionClear = exception_clear, .DeleteLocalRef = release_ref,
    .SetLongField = set_long, .SetIntField = set_int,
    .SetBooleanField = set_bool, .SetObjectField = set_object,
};

static void exercise(int kind, enum fault operation, int occurrence) {
    fault = operation;
    nth = occurrence;
    seen = hit = pending = ref_count = ref_index = callbacks = writes = 0;
    memset(live, 0, sizeof(live));
    strcpy(address, "127.0.0.1");
    // Force field lookup retries, including after a partially initialized cache.
    fidAllowed = fidRport = fidRcode = fidUsageReceived = NULL;
    JNIEnv env = &mock_jni;
    struct arguments args = {.env = &env, .instance = (jobject) 1};
    if (kind == 0) {
        jobject packet = __real_create_packet(&args, 4, 6, "", "127.0.0.1", 1234,
                "127.0.0.1", 80, "", 1, 1);
        assert((packet == NULL) == (hit != 0));
        release_ref(&env, packet);
        assert(hit ? writes == 0 : writes == 11);
    } else if (kind == 1 || kind == 4) {
        struct allowed *result = __real_is_address_allowed(&args, new_ref());
        assert((result == NULL) == (hit != 0));
        if (result != NULL)
            assert(strcmp(result->raddr, address) == 0 && result->rport == 5354);
#ifdef INVIZIBLE_UDP_PROXY
        if (result != NULL) assert(result->udp_proxy == (kind == 4));
#endif
    } else if (kind == 2) {
        __real_dns_resolved(&args, "test", "test", "", "", "192.0.2.1", 0);
        assert(hit ? writes == 0 : writes == 7);
        assert(hit ? callbacks == 0 : callbacks == 1);
    } else {
        __real_account_usage(&args, 4, 6, "192.0.2.1", 80, 1, 10, 20);
        assert(hit ? writes == 0 : writes == 8);
        assert(hit ? callbacks == 0 : callbacks == 1);
    }
    assert(!pending && ref_count == 0 && string_refs == 0);
    assert(operation == NO_FAULT || hit);
}

void test_jni_udp_proxy(void) {
#if defined(INVIZIBLE_UDP_PROXY) && !defined(INVIZIBLE_BASELINE_TESTS)
    extern char proxy_socks5_addr[];
    extern int proxy_socks5_port;
    extern jfieldID fidUdpProxy;
    jclass saved = clsAllowed; clsAllowed = (jclass) 1;
    strcpy(proxy_socks5_addr, "127.0.0.1"); proxy_socks5_port = 1080;
    for (int i = 0; i < 3; i++) {
        fidUdpProxy = NULL;
        exercise(4, FIELD_ID, i + 1);
        exercise(4, NO_FAULT, 0);
    }
    exercise(4, BOOL_FIELD, 1);
    exercise(4, INT_FIELD, 1); // A preceding field exception must skip the boolean read.
    exercise(4, NO_FAULT, 0);
    proxy_socks5_addr[0] = 0; proxy_socks5_port = 0;
    exercise(1, NO_FAULT, 0); // A cached true flag must not affect the ordinary path.
    clsAllowed = saved;
#endif
}

void test_jni_uid_exceptions(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    JNIEnv env = &mock_jni;
    struct arguments args = {.env = &env, .instance = (jobject) 1};
    const jint results[] = {0, 12345, -1};
    for (size_t i = 0; i < sizeof(results) / sizeof(*results); i++) {
        returned_uid = results[i];
        for (int throwing = 1; throwing >= 0; throwing--) {
            fault = throwing ? INT_CALL : NO_FAULT;
            nth = 1;
            seen = hit = pending = ref_count = ref_index = callbacks = 0;
            memset(live, 0, sizeof(live));
            assert(__real_get_uid_q(&args, 4, IPPROTO_TCP, "10.0.0.2", 1234,
                                   "203.0.113.2", 443) == (throwing ? -1 : returned_uid));
            assert(callbacks == 1 && hit == throwing);
            assert(!pending && ref_count == 0 && string_refs == 0 && allocations == 0);
        }
    }
#endif
}

void test_jni_wall_clock(void) {
#ifndef INVIZIBLE_BASELINE_TESTS
    jclass saved = clsPacket;
    clsPacket = (jclass) 1;
    wall_clock_enabled = true;
#ifdef PROFILE_JNI
    const int packet_call = 2, normal_calls = 3, scenarios = 5;
#else
    const int packet_call = 1, normal_calls = 1, scenarios = 3;
#endif
    for (int scenario = 0; scenario < scenarios; scenario++) {
        wall_clock_fail_mask = 0;
        if (scenario == 1 || scenario == 2)
            wall_clock_fail_mask = 1U << (packet_call - 1);
        else if (scenario == 3)
            wall_clock_fail_mask = 1U;
        else if (scenario == 4)
            wall_clock_fail_mask = 4U;
        wall_time_failure = scenario == 2;
        wall_clock_calls = wall_time_calls = 0;
        exercise(0, NO_FAULT, 0); // A valid packet, all fields written, no leaked refs.
        assert(wall_clock_calls == (scenario == 3 ? 2 : normal_calls));
        assert(wall_time_calls == (scenario == 1 || scenario == 2 ? 1 : 0));
        assert(last_long == (scenario == 1 ? INT64_C(1700000123000) :
                            scenario == 2 ? 0 : INT64_C(1700000000987)));
        if (scenario == 1 || scenario == 2) {
            char expected[80];
            snprintf(expected, sizeof(expected), "gettimeofday packet error %d:", EIO);
            assert(strstr(last_log, expected) != NULL);
        }
        wall_clock_fail_mask = 0;
        wall_time_failure = false;
        wall_clock_calls = wall_time_calls = 0;
        exercise(0, NO_FAULT, 0);
        assert(last_long == INT64_C(1700000000987));
        assert(wall_clock_calls == normal_calls && wall_time_calls == 0);
    }
    wall_clock_enabled = false;
    clsPacket = saved;
#endif
}

void test_jni_allocation_failures(int kind) {
    jclass saved[] = {clsPacket, clsAllowed, clsRR, clsUsage};
    clsPacket = clsAllowed = clsRR = clsUsage = (jclass) 1;
    // Fail every individual class, object, string, field, and UTF acquisition.
    const int counts[][6] = {
        {0, 0, 1, 4, 0, 11}, {0, 1, 0, 0, 1, 2},
        {0, 1, 1, 5, 0, 7}, {0, 1, 1, 1, 0, 8}
    };
    for (enum fault operation = CLASS_REF; operation <= FIELD_ID; operation++) {
        for (int i = 1; i <= counts[kind][operation]; i++) {
            exercise(kind, operation, i);
            exercise(kind, NO_FAULT, 0); // Every failure must permit the next call to recover.
        }
    }
    if (kind == 1) {
        JNIEnv env = &mock_jni;
        struct arguments args = {.env = &env, .instance = (jobject) 1};
        memset(address, 'x', sizeof(address) - 1);
        address[sizeof(address) - 1] = 0;
        assert(__real_is_address_allowed(&args, new_ref()) == NULL);
        assert(!pending && ref_count == 0 && string_refs == 0);
    }
    clsPacket = saved[0]; clsAllowed = saved[1]; clsRR = saved[2]; clsUsage = saved[3];
}

jint invizible_app_JNI_OnLoad(JavaVM *, void *);
void invizible_app_JNI_OnUnload(JavaVM *, void *);
static int global_refs, fail_get_env;
static jclass JNICALL load_class(JNIEnv *env, const char *name) {
    return fails(CLASS_REF) ? NULL : (jclass) new_ref();
}
static jobject JNICALL load_global(JNIEnv *env, jobject local) {
    assert(local != NULL);
    if (fails(OBJECT_REF))
        return NULL;
    global_refs++;
    return new_ref();
}
static void JNICALL release_global(JNIEnv *env, jobject global) {
    assert(global != NULL && global_refs > 0);
    global_refs--;
    release_ref(env, global);
}
static const struct JNINativeInterface load_jni = {
    .FindClass = load_class, .NewGlobalRef = load_global,
    .DeleteLocalRef = release_ref, .DeleteGlobalRef = release_global,
    .ExceptionOccurred = exception, .ExceptionCheck = exception_check
};
static JNIEnv load_env = &load_jni;
static jint JNICALL load_get_env(JavaVM *vm, void **env, jint version) {
    assert(version == JNI_VERSION_1_6);
    if (fail_get_env)
        return JNI_EVERSION;
    *env = &load_env;
    return JNI_OK;
}

void test_jni_load_failures(void) {
    jclass saved[] = {clsPacket, clsAllowed, clsRR, clsUsage};
    clsPacket = clsAllowed = clsRR = clsUsage = NULL;
    const struct JNIInvokeInterface table = {.GetEnv = load_get_env};
    JavaVM vm = &table;
    fail_get_env = 1;
    assert(invizible_app_JNI_OnLoad(&vm, NULL) == JNI_ERR);
    fail_get_env = 0;
    for (int operation = 0; operation < 2; operation++) {
        for (int occurrence = 1; occurrence <= 4; occurrence++) {
            fault = operation == 0 ? CLASS_REF : OBJECT_REF;
            nth = occurrence;
            seen = hit = pending = ref_count = ref_index = global_refs = 0;
            memset(live, 0, sizeof(live));
            assert(invizible_app_JNI_OnLoad(&vm, NULL) == JNI_ERR);
            assert(hit && pending && ref_count == 0 && global_refs == 0);
            assert(clsPacket == NULL && clsAllowed == NULL && clsRR == NULL && clsUsage == NULL);
            // Model Java handling the failed load before retrying it.
            pending = 0;
            fault = NO_FAULT;
            assert(invizible_app_JNI_OnLoad(&vm, NULL) == JNI_VERSION_1_6);
            assert(!pending && ref_count == 4 && global_refs == 4);
            invizible_app_JNI_OnUnload(&vm, NULL);
            assert(ref_count == 0 && global_refs == 0);
            assert(clsPacket == NULL && clsAllowed == NULL && clsRR == NULL && clsUsage == NULL);
        }
    }
    clsPacket = saved[0]; clsAllowed = saved[1]; clsRR = saved[2]; clsUsage = saved[3];
}
