#include "test_support.h"

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

enum fault { NO_FAULT, CLASS_REF, OBJECT_REF, STRING_REF, UTF_CHARS, FIELD_ID };
static enum fault fault;
static int nth, seen, hit, pending, ref_count, ref_index, callbacks, writes;
static int live[64];
static char address[128];

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
    return 5354;
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
static jthrowable JNICALL exception(JNIEnv *env) {
    return pending ? (jthrowable) new_ref() : NULL;
}
static jboolean JNICALL exception_check(JNIEnv *env) { return pending; }
static void JNICALL exception_clear(JNIEnv *env) { pending = 0; }
static void set_field(jobject object, jfieldID field) {
    assert(!pending && object != NULL && field != NULL);
    writes++;
}
static void JNICALL set_long(JNIEnv *env, jobject object, jfieldID field, jlong value) { set_field(object, field); }
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
    .GetStringUTFChars = utf_chars, .ReleaseStringUTFChars = release_utf,
    .CallObjectMethod = call_object, .CallVoidMethod = call_void,
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
    } else if (kind == 1) {
        struct allowed *result = __real_is_address_allowed(&args, new_ref());
        assert((result == NULL) == (hit != 0));
        if (result != NULL)
            assert(strcmp(result->raddr, address) == 0 && result->rport == 5354);
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
