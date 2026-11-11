#include "test_support.h"
#include <sched.h>

jlong Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(JNIEnv *, jobject, jint);
void Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1done(JNIEnv *, jobject, jlong);

static int descriptor_count(void) {
    DIR *directory = opendir("/proc/self/fd");
    assert(directory != NULL);
    int count = 0;
    while (readdir(directory) != NULL)
        count++;
    closedir(directory);
    return count;
}

void test_native_initialization(void) {
    JNIEnv env = &jni;
    int descriptors = descriptor_count();
    fail_alloc_tag = "init";
    fail_alloc_match = 1;
    assert(Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(&env, NULL, 29) == 0);
    fail_alloc_tag = NULL;
    for (failure = PIPE; failure <= MUTEX; failure++) {
        assert(Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(&env, NULL, 29) == 0);
        assert(allocations == 0 && descriptor_count() == descriptors);
    }
    failure = NONE;
    for (int step = 1; step <= 4; step++) {
        fcntl_calls = 0;
        fail_fcntl_call = step;
        assert(Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(&env, NULL, 29) == 0);
        assert(allocations == 0 && descriptor_count() == descriptors);
    }
    fail_fcntl_call = 0;
    real_network = 1; // Check actual descriptor flags on the successful retry.
    jlong context = Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(&env, NULL, 29);
    assert(context != 0);
    struct context *ctx = (struct context *) context;
    assert(fcntl(ctx->pipefds[0], F_GETFL) & O_NONBLOCK);
    assert(fcntl(ctx->pipefds[1], F_GETFL) & O_NONBLOCK);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1done(&env, NULL, context);
    assert(allocations == 0 && descriptor_count() == descriptors);
}

static int exception_pending, exception_mode, exception_refs;
static jthrowable JNICALL pending_exception(JNIEnv *env) {
    if (!exception_pending)
        return NULL;
    exception_refs++;
    return (jthrowable) "exception";
}
static jboolean JNICALL has_exception(JNIEnv *env) { return exception_pending; }
static void JNICALL clear_exception(JNIEnv *env) { exception_pending = 0; }
static jclass JNICALL exception_class(JNIEnv *env, const char *name) {
    if (exception_mode == 1) {
        exception_pending = 1;
        return NULL;
    }
    exception_refs++;
    return (jclass) "class";
}
static jmethodID JNICALL message_method(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    assert(!exception_pending);
    if (exception_mode == 2) {
        exception_pending = 1;
        return NULL;
    }
    return (jmethodID) "method";
}
static jobject JNICALL exception_message(JNIEnv *env, jobject ex, jmethodID method, ...) {
    assert(!exception_pending);
    if (exception_mode == 3)
        exception_pending = 1;
    if (exception_mode <= 3)
        return NULL;
    exception_refs++;
    return (jobject) "message";
}
static const char *JNICALL message_chars(JNIEnv *env, jstring message, jboolean *copy) {
    assert(!exception_pending && message != NULL);
    if (exception_mode == 4) {
        exception_pending = 1;
        return NULL;
    }
    string_refs++;
    return "message";
}
static void JNICALL delete_exception_ref(JNIEnv *env, jobject ref) {
    assert(ref != NULL);
    exception_refs--;
}

void test_exception_failures(void) {
    struct JNINativeInterface table = jni;
    table.ExceptionOccurred = pending_exception;
    table.ExceptionCheck = has_exception;
    table.ExceptionClear = clear_exception;
    table.FindClass = exception_class;
    table.GetMethodID = message_method;
    table.CallObjectMethod = exception_message;
    table.GetStringUTFChars = message_chars;
    table.DeleteLocalRef = delete_exception_ref;
    JNIEnv env = &table;
    for (exception_mode = 0; exception_mode <= 5; exception_mode++) {
        exception_pending = 1;
        assert(jniCheckException(&env) == 1);
        assert(!exception_pending && exception_refs == 0 && string_refs == 0);
        assert(jniCheckException(&env) == 0);
    }
}

#ifdef INVIZIBLE_ATOMIC_FLAGS
static void *publish_flags(void *data) {
    struct context *ctx = data;
    tor_connection_available = true;
    ctx->stopping = 1;
    return NULL;
}
#endif

void test_atomic_flags(void) {
#ifdef INVIZIBLE_ATOMIC_FLAGS
    _Static_assert(_Generic(&((struct context *) 0)->stopping, atomic_int *: 1, default: 0),
                   "stop flag must be atomic");
    struct context ctx = {0};
    atomic_init(&ctx.stopping, 0);
    tor_connection_available = false;
    pthread_t thread;
    assert(pthread_create(&thread, NULL, publish_flags, &ctx) == 0);
    int observed = 0;
    for (int i = 0; i < 100000; i++) {
        if (ctx.stopping) {
            observed = tor_connection_available;
            break;
        }
        sched_yield();
    }
    assert(pthread_join(thread, NULL) == 0);
    assert(observed);
#endif
}

void test_socks5(void) {
    JNIEnv env = &jni;
    char ascii[128], unicode[255];
    memset(ascii, 'a', 127); ascii[127] = 0;
    for (int i = 0; i < 127; i++) {
        unicode[i * 2] = (char) 0xC3;
        unicode[i * 2 + 1] = (char) 0xA9;
    }
    unicode[254] = 0; // 127 characters, 254 encoded bytes.
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "127.0.0.1", 1080, (jstring) ascii, (jstring) "");
    assert(strcmp(proxy_socks5_username, ascii) == 0);
    assert(string_refs == 0 && exceptions == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "::1", 1080, (jstring) unicode, (jstring) "");
    assert(exceptions == 1 && string_refs == 0);
    assert(strcmp(proxy_socks5_addr, "127.0.0.1") == 0);
    assert(strcmp(proxy_socks5_username, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(
            &env, NULL, (jstring) "127.0.0.1", 9050, (jstring) "", (jstring) ascii, 0, 5400);
    assert(strcmp(tor_socks5_password, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(
            &env, NULL, (jstring) "::1", 9050, (jstring) "", (jstring) unicode, 0, 5400);
    assert(exceptions == 2 && strcmp(tor_socks5_password, ascii) == 0);
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, NULL, 1080, (jstring) "", (jstring) "");
    assert(exceptions == 3 && string_refs == 0);
    fail_string_call = string_calls + 2;
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "::1", 1080, (jstring) "user", (jstring) "pass");
    assert(string_refs == 0 && strcmp(proxy_socks5_username, ascii) == 0);
    fail_string_call = 0;
    // Restore settings so socket tests do not request proxy routing.
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            &env, NULL, (jstring) "", 0, (jstring) "", (jstring) "");
}
