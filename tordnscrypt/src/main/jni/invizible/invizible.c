#pragma clang diagnostic push
#pragma ide diagnostic ignored "readability-magic-numbers"
#pragma ide diagnostic ignored "cppcoreguidelines-avoid-magic-numbers"
/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2026 by Garmatin Oleksandr invizible.soft@gmail.com
*/

#include "invizible.h"

// It is assumed that no packets will get lost and that packets arrive in order
// https://android.googlesource.com/platform/frameworks/base.git/+/master/services/core/jni/com_android_server_connectivity_Vpn.cpp

// Global variables

char tor_socks5_addr[INET6_ADDRSTRLEN + 1];
int tor_socks5_port = 0;
char tor_socks5_username[127 + 1];
char tor_socks5_password[127 + 1];
int tor_dns_port = 0;
bool tor_isolate_uid = false;

char proxy_socks5_addr[INET6_ADDRSTRLEN + 1];
int proxy_socks5_port = 0;
char proxy_socks5_username[127 + 1];
char proxy_socks5_password[127 + 1];
int own_uid = -2;
bool compatibility_mode = false;
bool can_filter = true;
bool bypass_lan = true;
atomic_bool tor_connection_available = false;

extern int max_tun_msg;

extern int uid_cache_size;
extern int uid_cache_capacity;
extern struct uid_cache_entry *uid_cache;

// JNI

jclass clsPacket;
jclass clsAllowed;
jclass clsRR;
jclass clsUsage;

static void jniDeleteClasses(JNIEnv *env) {
    jclass *classes[] = {&clsPacket, &clsAllowed, &clsRR, &clsUsage};
    for (size_t i = 0; i < sizeof(classes) / sizeof(classes[0]); i++) {
        if (*classes[i] != NULL) {
            (*env)->DeleteGlobalRef(env, *classes[i]);
            ng_delete_alloc(*classes[i], __FILE__, __LINE__);
            *classes[i] = NULL;
        }
    }
}

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    log_android(ANDROID_LOG_INFO, "JNI load");

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        log_android(ANDROID_LOG_INFO, "JNI load GetEnv failed");
        return -1;
    }

    const char *names[] = {
            "pan/alexander/tordnscrypt/vpn/Packet",
            "pan/alexander/tordnscrypt/vpn/Allowed",
            "pan/alexander/tordnscrypt/vpn/ResourceRecord",
            "pan/alexander/tordnscrypt/vpn/Usage"
    };
    jclass *classes[] = {&clsPacket, &clsAllowed, &clsRR, &clsUsage};
    for (size_t i = 0; i < sizeof(classes) / sizeof(classes[0]); i++) {
        jclass local = jniFindClass(env, names[i]);
        if (local == NULL) {
            jniDeleteClasses(env);
            return JNI_ERR;
        }
        *classes[i] = jniGlobalRef(env, local);
        (*env)->DeleteLocalRef(env, local);
        if (*classes[i] == NULL) {
            jniDeleteClasses(env);
            return JNI_ERR;
        }
        ng_add_alloc(*classes[i], names[i]);
    }

    // Raise file number limit to maximum
    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim))
        log_android(ANDROID_LOG_WARN, "getrlimit error %d: %s", errno, strerror(errno));
    else {
        rlim_t soft = rlim.rlim_cur;
        rlim.rlim_cur = rlim.rlim_max;
        if (setrlimit(RLIMIT_NOFILE, &rlim))
            log_android(ANDROID_LOG_WARN, "setrlimit error %d: %s", errno, strerror(errno));
        else
            log_android(ANDROID_LOG_WARN, "raised file limit from %ju to %ju",
                        (uintmax_t) soft, (uintmax_t) rlim.rlim_cur);
    }

    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    log_android(ANDROID_LOG_INFO, "JNI unload");

    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        log_android(ANDROID_LOG_INFO, "JNI load GetEnv failed");
    else
        jniDeleteClasses(env);
}

// JNI ServiceSinkhole

JNIEXPORT jlong JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1init(
        JNIEnv *env, jobject instance, jint sdk) {
    struct context *ctx = ng_calloc(1, sizeof(struct context), "init");
    if (ctx == NULL)
        return 0;
    ctx->sdk = sdk;
    atomic_init(&ctx->stopping, 0);
    ctx->pipefds[0] = ctx->pipefds[1] = -1;

    int error = pthread_mutex_init(&ctx->lock, NULL);
    if (error != 0) {
        log_android(ANDROID_LOG_ERROR, "pthread_mutex_init failed: %s", strerror(error));
        ng_free(ctx, __FILE__, __LINE__);
        return 0;
    }

    // Publish a context only after both wakeup descriptors are usable.
    int ready = (pipe(ctx->pipefds) == 0);
    if (!ready)
        log_android(ANDROID_LOG_ERROR, "Create pipe error %d: %s", errno, strerror(errno));
    for (int i = 0; ready && i < 2; i++) {
        int flags = fcntl(ctx->pipefds[i], F_GETFL, 0);
        ready = (flags >= 0 && fcntl(ctx->pipefds[i], F_SETFL, flags | O_NONBLOCK) >= 0);
        if (!ready)
            log_android(ANDROID_LOG_ERROR, "fcntl pipefds[%d] O_NONBLOCK error %d: %s",
                        i, errno, strerror(errno));
    }
    if (!ready) {
        for (int i = 0; i < 2; i++)
            if (ctx->pipefds[i] >= 0)
                close(ctx->pipefds[i]);
        pthread_mutex_destroy(&ctx->lock);
        ng_free(ctx, __FILE__, __LINE__);
        return 0;
    }

    *tor_socks5_addr = 0;
    tor_socks5_port = 0;
    *tor_socks5_username = 0;
    *tor_socks5_password = 0;
    tor_dns_port = 0;

    *proxy_socks5_addr = 0;
    proxy_socks5_port = 0;
    *proxy_socks5_username = 0;
    *proxy_socks5_password = 0;

    own_uid = getuid();

    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1start(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;

    max_tun_msg = 0;
    ctx->stopping = 0;

    log_android(ANDROID_LOG_WARN, "Starting level %d", LOG_LEVEL);

}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1run(
        JNIEnv *env, jobject instance, jlong context, jint tun, jboolean fwd53, jint rcode,
        jboolean compatibility, jboolean filter, jboolean lan) {
    struct context *ctx = (struct context *) context;

    log_android(ANDROID_LOG_WARN, "Running tun %d fwd53 %d level %d", tun, fwd53, LOG_LEVEL);

    compatibility_mode = compatibility;
    can_filter = filter;
    bypass_lan = lan;

    // Set blocking
    int flags = fcntl(tun, F_GETFL, 0);
    if (flags < 0 || fcntl(tun, F_SETFL, flags & ~O_NONBLOCK) < 0)
        log_android(ANDROID_LOG_ERROR, "fcntl tun ~O_NONBLOCK error %d: %s",
                    errno, strerror(errno));

    // Get arguments
    struct arguments *args = ng_malloc(sizeof(struct arguments), "arguments");
    if (args == NULL) {
        // Reporting failure must not require the allocation that just failed.
        struct arguments failed = {.env = env, .instance = instance};
        report_exit(&failed, "Cannot allocate native tunnel arguments");
        return;
    }
    args->env = env;
    args->instance = instance;
    args->tun = tun;
    args->fwd53 = fwd53;
    args->rcode = rcode;
    args->ctx = ctx;
    handle_events(args);
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1stop(
        JNIEnv *env, jobject instance, jlong context) {
    struct context *ctx = (struct context *) context;
    ctx->stopping = 1;

    log_android(ANDROID_LOG_WARN, "Write pipe wakeup");
    if (write(ctx->pipefds[1], "w", 1) < 0)
        log_android(ANDROID_LOG_WARN, "Write pipe error %d: %s", errno, strerror(errno));
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1clear(
        JNIEnv *env, jobject instance, jlong context) {

    if (context == 0) {
        return;
    }

    struct context *ctx = (struct context *) context;
    clear(ctx);
}

JNIEXPORT jint JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1get_1mtu(JNIEnv *env, jobject instance) {
    return get_mtu();
}

static int validate_socks5_string(JNIEnv *env, jstring value, size_t capacity) {
    if (value == NULL || (size_t) (*env)->GetStringUTFLength(env, value) >= capacity) {
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (cls != NULL) {
            (*env)->ThrowNew(env, cls, "SOCKS5 setting exceeds its encoded byte limit or is null");
            (*env)->DeleteLocalRef(env, cls);
        }
        return 0;
    }
    return 1;
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1validate_1socks5(
        JNIEnv *env, jobject instance, jstring address, jstring username, jstring password) {
    if (!validate_socks5_string(env, address, sizeof(proxy_socks5_addr)) ||
        !validate_socks5_string(env, username, sizeof(proxy_socks5_username)))
        return;
    validate_socks5_string(env, password, sizeof(proxy_socks5_password));
}

static int copy_socks5_string(JNIEnv *env, jstring value, char *dest, size_t capacity) {
    if (!validate_socks5_string(env, value, capacity))
        return 0;

    const char *text = (*env)->GetStringUTFChars(env, value, NULL);
    if (text == NULL)
        return 0; // Preserve the pending JNI exception.
    strcpy(dest, text);
    (*env)->ReleaseStringUTFChars(env, value, text);
    return 1;
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(JNIEnv *env,
                                                                            jobject instance,
                                                                            jstring addr_,
                                                                            jint port,
                                                                            jstring username_,
                                                                            jstring password_,
                                                                            jboolean isolate_uid,
                                                                            jint dnsport) {
    char addr[sizeof(tor_socks5_addr)];
    char username[sizeof(tor_socks5_username)];
    char password[sizeof(tor_socks5_password)];
    if (!copy_socks5_string(env, addr_, addr, sizeof(addr)) ||
        !copy_socks5_string(env, username_, username, sizeof(username)) ||
        !copy_socks5_string(env, password_, password, sizeof(password)))
        return;

    strcpy(tor_socks5_addr, addr);
    tor_socks5_port = port;
    strcpy(tor_socks5_username, username);
    strcpy(tor_socks5_password, password);
    tor_dns_port = dnsport;
    tor_isolate_uid = isolate_uid;

    log_android(ANDROID_LOG_WARN, "TOR SOCKS5 %s:%d user=%s",
                tor_socks5_addr, tor_socks5_port, tor_socks5_username);
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(JNIEnv *env,
                                                                              jobject instance,
                                                                              jstring addr_,
                                                                              jint port,
                                                                              jstring username_,
                                                                              jstring password_) {
    char addr[sizeof(proxy_socks5_addr)];
    char username[sizeof(proxy_socks5_username)];
    char password[sizeof(proxy_socks5_password)];
    if (!copy_socks5_string(env, addr_, addr, sizeof(addr)) ||
        !copy_socks5_string(env, username_, username, sizeof(username)) ||
        !copy_socks5_string(env, password_, password, sizeof(password)))
        return;

    strcpy(proxy_socks5_addr, addr);
    proxy_socks5_port = port;
    strcpy(proxy_socks5_username, username);
    strcpy(proxy_socks5_password, password);

    log_android(ANDROID_LOG_WARN, "PROXY SOCKS5 %s:%d user=%s",
                proxy_socks5_addr, proxy_socks5_port, proxy_socks5_username);
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1internet_1is_1available(JNIEnv *env,
                                                                                   jobject instance,
                                                                                   jboolean available) {
    tor_connection_available = available;
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1done(
        JNIEnv *env, jobject instance, jlong context) {

    if (context == 0) {
        return;
    }

    struct context *ctx = (struct context *) context;
    log_android(ANDROID_LOG_INFO, "Done");

    clear(ctx);

    if (pthread_mutex_destroy(&ctx->lock))
        log_android(ANDROID_LOG_ERROR, "pthread_mutex_destroy failed");

    for (int i = 0; i < 2; i++)
        if (close(ctx->pipefds[i]))
            log_android(ANDROID_LOG_ERROR, "Close pipe error %d: %s", errno, strerror(errno));

    if (uid_cache != NULL)
        ng_free(uid_cache, __FILE__, __LINE__);
    uid_cache_size = 0;
    uid_cache_capacity = 0;
    uid_cache = NULL;

    ng_free(ctx, __FILE__, __LINE__);
}

// JNI Util

JNIEXPORT jstring JNICALL
Java_pan_alexander_tordnscrypt_vpn_VpnUtils_jni_1getprop(JNIEnv *env, jclass type, jstring name_) {
    if (name_ == NULL)
        return NULL;
    const char *name = (*env)->GetStringUTFChars(env, name_, 0);
    if (name == NULL)
        return NULL;
    ng_add_alloc(name, "name");

    char value[PROP_VALUE_MAX + 1] = "";
    __system_property_get(name, value);

    (*env)->ReleaseStringUTFChars(env, name_, name);
    ng_delete_alloc(name, __FILE__, __LINE__);

    return (*env)->NewStringUTF(env, value); // Freed by Java
}

JNIEXPORT jboolean JNICALL
Java_pan_alexander_tordnscrypt_vpn_VpnUtils_is_1numeric_1address(JNIEnv *env, jclass type,
                                                                 jstring ip_) {
    jboolean numeric = 0;
    if (ip_ == NULL)
        return numeric;
    const char *ip = (*env)->GetStringUTFChars(env, ip_, 0);
    if (ip == NULL)
        return numeric;
    ng_add_alloc(ip, "ip");

    struct addrinfo hints;
    memset(&hints, 0, sizeof(struct addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_flags = AI_NUMERICHOST;
    struct addrinfo *result = NULL;
    int err = getaddrinfo(ip, NULL, &hints, &result);
    if (err)
        log_android(ANDROID_LOG_DEBUG, "getaddrinfo(%s) error %d: %s", ip, err, gai_strerror(err));
    else
        numeric = (jboolean) (result != NULL);

    if (result != NULL)
        freeaddrinfo(result);

    (*env)->ReleaseStringUTFChars(env, ip_, ip);
    ng_delete_alloc(ip, __FILE__, __LINE__);
    return numeric;
}

void report_exit(const struct arguments *args, const char *fmt, ...) {
    jclass cls = (*args->env)->GetObjectClass(args->env, args->instance);
    if (cls == NULL) {
        jniCheckException(args->env);
        return;
    }
    ng_add_alloc(cls, "cls");
    jmethodID mid = jniGetMethodID(args->env, cls, "nativeExit", "(Ljava/lang/String;)V");

    jstring jreason = NULL;
    if (mid != NULL && fmt != NULL) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsnprintf(line, sizeof(line), fmt, argptr);
        jreason = (*args->env)->NewStringUTF(args->env, line);
        ng_add_alloc(jreason, "jreason");
        va_end(argptr);
    }

    if (mid != NULL && (fmt == NULL || jreason != NULL))
        (*args->env)->CallVoidMethod(args->env, args->instance, mid, jreason);
    jniCheckException(args->env);

    if (jreason != NULL) {
        (*args->env)->DeleteLocalRef(args->env, jreason);
        ng_delete_alloc(jreason, __FILE__, __LINE__);
    }
    (*args->env)->DeleteLocalRef(args->env, cls);
    ng_delete_alloc(cls, __FILE__, __LINE__);
}

static jmethodID midProtect = NULL;

int protect_socket(const struct arguments *args, int socket, int uid) {

    if (args->ctx->sdk >= 21 && !compatibility_mode && uid != own_uid)
        return 0;

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    if (midProtect == NULL)
        midProtect = jniGetMethodID(args->env, clsService, "protectSocket", "(I)Z");

    jboolean isProtected = JNI_FALSE;
    if (clsService != NULL && midProtect != NULL && !(*args->env)->ExceptionCheck(args->env))
        isProtected = (*args->env)->CallBooleanMethod(
                args->env, args->instance, midProtect, socket);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

    if (!isProtected) {
        log_android(ANDROID_LOG_ERROR, "protect socket failed");
        return -1;
    }

    return 0;
}

// http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
// http://journals.ecs.soton.ac.uk/java/tutorial/native1.1/implementing/index.html

static void jniDeleteLocal(JNIEnv *env, jobject ref) {
    if (ref != NULL) {
        (*env)->DeleteLocalRef(env, ref);
        ng_delete_alloc(ref, __FILE__, __LINE__);
    }
}

static jstring jniNewString(JNIEnv *env, const char *text) {
    if ((*env)->ExceptionCheck(env))
        return NULL;
    return (*env)->NewStringUTF(env, text);
}

jobject jniGlobalRef(JNIEnv *env, jobject cls) {
    jobject gcls = (*env)->NewGlobalRef(env, cls);
    if (gcls == NULL)
        log_android(ANDROID_LOG_ERROR, "Global ref failed (out of memory?)");
    return gcls;
}

jclass jniFindClass(JNIEnv *env, const char *name) {
    jclass cls = (*env)->FindClass(env, name);
    if (cls == NULL)
        log_android(ANDROID_LOG_ERROR, "Class %s not found", name);
    else
        jniCheckException(env);
    return cls;
}

jmethodID jniGetMethodID(JNIEnv *env, jclass cls, const char *name, const char *signature) {
    if (cls == NULL || (*env)->ExceptionCheck(env))
        return NULL;
    jmethodID method = (*env)->GetMethodID(env, cls, name, signature);
    if (method == NULL) {
        log_android(ANDROID_LOG_ERROR, "Method %s %s not found", name, signature);
        jniCheckException(env);
    }
    return method;
}

jfieldID jniGetFieldID(JNIEnv *env, jclass cls, const char *name, const char *type) {
    if (cls == NULL || (*env)->ExceptionCheck(env))
        return NULL;
    jfieldID field = (*env)->GetFieldID(env, cls, name, type);
    if (field == NULL)
        log_android(ANDROID_LOG_ERROR, "Field %s type %s not found", name, type);
    return field;
}

jobject jniNewObject(JNIEnv *env, jclass cls, jmethodID constructor, const char *name) {
    if (cls == NULL || constructor == NULL || (*env)->ExceptionCheck(env))
        return NULL;
    jobject object = (*env)->NewObject(env, cls, constructor);
    if (object == NULL)
        log_android(ANDROID_LOG_ERROR, "Create object %s failed", name);
    else
        jniCheckException(env);
    return object;
}

int jniCheckException(JNIEnv *env) {
    jthrowable ex = (*env)->ExceptionOccurred(env);
    if (ex) {
        ng_add_alloc(ex, "exception");
        (*env)->ExceptionClear(env);

        // Resolve the base method each time: subclasses may override getMessage.
        // Do not recursively use jniGetMethodID while reporting an exception.
        jclass clazz = (*env)->FindClass(env, "java/lang/Throwable");
        ng_add_alloc(clazz, "clsException");
        jmethodID method = (clazz == NULL ? NULL : (*env)->GetMethodID(
                env, clazz, "getMessage", "()Ljava/lang/String;"));
        jstring message = (method == NULL ? NULL :
                (jstring) (*env)->CallObjectMethod(env, ex, method));
        ng_add_alloc(message, "exceptionMessage");
        const char *exception = NULL;
        if (!(*env)->ExceptionCheck(env) && message != NULL)
            exception = (*env)->GetStringUTFChars(env, message, NULL);
        log_android(ANDROID_LOG_ERROR, "JNI Exception %s",
                    exception == NULL ? "(message unavailable)" : exception);
        if (exception != NULL)
            (*env)->ReleaseStringUTFChars(env, message, exception);
        // A secondary lookup, overridden getter or UTF conversion may fail.
        (*env)->ExceptionClear(env);
        if (message != NULL)
            (*env)->DeleteLocalRef(env, message);
        if (clazz != NULL)
            (*env)->DeleteLocalRef(env, clazz);
        (*env)->DeleteLocalRef(env, ex);
        ng_delete_alloc(message, __FILE__, __LINE__);
        ng_delete_alloc(clazz, __FILE__, __LINE__);
        ng_delete_alloc(ex, __FILE__, __LINE__);
        return 1;
    }
    return 0;
}

static jmethodID midLogPacket = NULL;

void log_packet(const struct arguments *args, jobject jpacket) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lpan/alexander/tordnscrypt/vpn/Packet;)V";
    if (midLogPacket == NULL)
        midLogPacket = jniGetMethodID(args->env, clsService, "logPacket", signature);

    if (jpacket != NULL && clsService != NULL && midLogPacket != NULL &&
        !(*args->env)->ExceptionCheck(args->env))
        (*args->env)->CallVoidMethod(args->env, args->instance, midLogPacket, jpacket);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, clsService);
    (*args->env)->DeleteLocalRef(args->env, jpacket);
    ng_delete_alloc(clsService, __FILE__, __LINE__);
    ng_delete_alloc(jpacket, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "log_packet %f", mselapsed);
#endif
}

static jmethodID midDnsResolved = NULL;
static jmethodID midInitRR = NULL;
jfieldID fidQTime = NULL;
jfieldID fidQName = NULL;
jfieldID fidAName = NULL;
jfieldID fidCName = NULL;
jfieldID fidHInfo = NULL;
jfieldID fidResource = NULL;
jfieldID fidRcode = NULL;

void dns_resolved(const struct arguments *args,
                  const char *qname, const char *aname, const char *cname,
                  const char *hinfo, const char *resource, int rcode) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lpan/alexander/tordnscrypt/vpn/ResourceRecord;)V";
    if (midDnsResolved == NULL)
        midDnsResolved = jniGetMethodID(args->env, clsService, "dnsResolved", signature);

    const char *rr = "pan/alexander/tordnscrypt/vpn/ResourceRecord";
    if (midInitRR == NULL)
        midInitRR = jniGetMethodID(args->env, clsRR, "<init>", "()V");

    jobject jrr = jniNewObject(args->env, clsRR, midInitRR, rr);
    ng_add_alloc(jrr, "jrr");
    if (jrr == NULL || clsService == NULL || midDnsResolved == NULL) {
        jniCheckException(args->env);
        jniDeleteLocal(args->env, jrr);
        jniDeleteLocal(args->env, clsService);
        return;
    }

    if (fidRcode == NULL) {
        const char *string = "Ljava/lang/String;";
        fidQTime = jniGetFieldID(args->env, clsRR, "Time", "J");
        fidQName = jniGetFieldID(args->env, clsRR, "QName", string);
        fidAName = jniGetFieldID(args->env, clsRR, "AName", string);
        fidCName = jniGetFieldID(args->env, clsRR, "CName", string);
        fidHInfo = jniGetFieldID(args->env, clsRR, "HInfo", string);
        fidResource = jniGetFieldID(args->env, clsRR, "Resource", string);
        fidRcode = jniGetFieldID(args->env, clsRR, "Rcode", "I");
    }

    jlong jtime = time(NULL) * 1000LL;
    jstring jqname = jniNewString(args->env, qname);
    jstring janame = jniNewString(args->env, aname);
    jstring jcname = jniNewString(args->env, cname);
    jstring jhinfo = jniNewString(args->env, hinfo);
    jstring jresource = jniNewString(args->env, resource);
    ng_add_alloc(jqname, "jqname");
    ng_add_alloc(janame, "janame");
    ng_add_alloc(jcname, "jcname");
    ng_add_alloc(jhinfo, "jhinfo");
    ng_add_alloc(jresource, "jresource");

    if (fidQTime != NULL && fidQName != NULL && fidAName != NULL && fidCName != NULL &&
        fidHInfo != NULL && fidResource != NULL && fidRcode != NULL &&
        jqname != NULL && janame != NULL && jcname != NULL && jhinfo != NULL && jresource != NULL) {
        (*args->env)->SetLongField(args->env, jrr, fidQTime, jtime);
        (*args->env)->SetObjectField(args->env, jrr, fidQName, jqname);
        (*args->env)->SetObjectField(args->env, jrr, fidAName, janame);
        (*args->env)->SetObjectField(args->env, jrr, fidCName, jcname);
        (*args->env)->SetObjectField(args->env, jrr, fidHInfo, jhinfo);
        (*args->env)->SetObjectField(args->env, jrr, fidResource, jresource);
        (*args->env)->SetIntField(args->env, jrr, fidRcode, rcode);

        (*args->env)->CallVoidMethod(args->env, args->instance, midDnsResolved, jrr);
    }
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jresource);
    (*args->env)->DeleteLocalRef(args->env, janame);
    (*args->env)->DeleteLocalRef(args->env, jqname);
    (*args->env)->DeleteLocalRef(args->env, jcname);
    (*args->env)->DeleteLocalRef(args->env, jhinfo);
    (*args->env)->DeleteLocalRef(args->env, jrr);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jresource, __FILE__, __LINE__);
    ng_delete_alloc(janame, __FILE__, __LINE__);
    ng_delete_alloc(jqname, __FILE__, __LINE__);
    ng_delete_alloc(jcname, __FILE__, __LINE__);
    ng_delete_alloc(jhinfo, __FILE__, __LINE__);
    ng_delete_alloc(jrr, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "log_packet %f", mselapsed);
#endif
}

static jmethodID midIsDomainBlocked = NULL;

jboolean is_domain_blocked(const struct arguments *args, const char *name) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Ljava/lang/String;)Z";
    if (midIsDomainBlocked == NULL)
        midIsDomainBlocked = jniGetMethodID(args->env, clsService, "isDomainBlocked", signature);

    jstring jname = jniNewString(args->env, name);
    ng_add_alloc(jname, "jname");

    jboolean jallowed = JNI_TRUE; // Allocation failure must not bypass DNS blocking.
    if (clsService != NULL && midIsDomainBlocked != NULL && jname != NULL)
        jallowed = (*args->env)->CallBooleanMethod(
                args->env, args->instance, midIsDomainBlocked, jname);
    if (jniCheckException(args->env))
        jallowed = JNI_TRUE;

    (*args->env)->DeleteLocalRef(args->env, jname);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jname, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "is_domain_blocked %f", mselapsed);
#endif

    return jallowed;
}

static jmethodID midIsRedirectToTor = NULL;

jboolean is_redirect_to_tor(const struct arguments *args, const int uid, const char *daddr,
                            const int dport) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(ILjava/lang/String;I)Z";
    if (midIsRedirectToTor == NULL)
        midIsRedirectToTor = jniGetMethodID(args->env, clsService, "isRedirectToTor", signature);

    jstring jdaddr = jniNewString(args->env, daddr);
    ng_add_alloc(jdaddr, "jdaddr");

    jboolean jredirect_to_tor = JNI_TRUE;
    if (clsService != NULL && midIsRedirectToTor != NULL && jdaddr != NULL)
        jredirect_to_tor = (*args->env)->CallBooleanMethod(
                args->env, args->instance, midIsRedirectToTor, uid, jdaddr, dport);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdaddr);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdaddr, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "is_redirect_to_tor %f", mselapsed);
#endif

    return jredirect_to_tor;
}

static jmethodID midIsRedirectToProxy = NULL;

jboolean is_redirect_to_proxy(const struct arguments *args, const int uid, const char *daddr,
                              const int dport) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(ILjava/lang/String;I)Z";
    if (midIsRedirectToProxy == NULL)
        midIsRedirectToProxy = jniGetMethodID(args->env, clsService, "isRedirectToProxy",
                                              signature);

    jstring jdaddr = jniNewString(args->env, daddr);
    ng_add_alloc(jdaddr, "jdaddr");

    jboolean jredirect_to_proxy = JNI_TRUE;
    if (clsService != NULL && midIsRedirectToProxy != NULL && jdaddr != NULL)
        jredirect_to_proxy = (*args->env)->CallBooleanMethod(
                args->env, args->instance, midIsRedirectToProxy, uid, jdaddr, dport);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdaddr);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdaddr, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "is_redirect_to_proxy %f", mselapsed);
#endif

    return jredirect_to_proxy;
}

static jmethodID midGetUidQ = NULL;

jint get_uid_q(const struct arguments *args,
               jint version, jint protocol,
               const char *source, jint sport,
               const char *dest, jint dport) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(IILjava/lang/String;ILjava/lang/String;I)I";
    if (midGetUidQ == NULL)
        midGetUidQ = jniGetMethodID(args->env, clsService, "getUidQ", signature);

    jstring jsource = jniNewString(args->env, source);
    jstring jdest = jniNewString(args->env, dest);
    ng_add_alloc(jsource, "jsource");
    ng_add_alloc(jdest, "jdest");

    jint juid = -1;
    if (clsService != NULL && midGetUidQ != NULL && jsource != NULL && jdest != NULL)
        juid = (*args->env)->CallIntMethod(
                args->env, args->instance, midGetUidQ,
                version, protocol, jsource, sport, jdest, dport);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdest);
    (*args->env)->DeleteLocalRef(args->env, jsource);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdest, __FILE__, __LINE__);
    ng_delete_alloc(jsource, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "get_uid_q %f", mselapsed);
#endif

    return juid;
}

static jmethodID midIsAddressAllowed = NULL;
jfieldID fidRaddr = NULL;
jfieldID fidRport = NULL;
struct allowed allowed;

struct allowed *is_address_allowed(const struct arguments *args, jobject jpacket) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    if (jpacket == NULL) {
        jniCheckException(args->env);
        return NULL;
    }

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lpan/alexander/tordnscrypt/vpn/Packet;)Lpan/alexander/tordnscrypt/vpn/Allowed;";
    if (midIsAddressAllowed == NULL)
        midIsAddressAllowed = jniGetMethodID(args->env, clsService, "isAddressAllowed", signature);
    if (clsService == NULL || midIsAddressAllowed == NULL) {
        jniCheckException(args->env);
        jniDeleteLocal(args->env, jpacket);
        jniDeleteLocal(args->env, clsService);
        return NULL;
    }

    jobject jallowed = (*args->env)->CallObjectMethod(
            args->env, args->instance, midIsAddressAllowed, jpacket);
    ng_add_alloc(jallowed, "jallowed");
    int exceptionOccurred = jniCheckException(args->env);

    if (jallowed != NULL && exceptionOccurred == 0) {
        if (fidRport == NULL) {
            const char *string = "Ljava/lang/String;";
            fidRaddr = jniGetFieldID(args->env, clsAllowed, "raddr", string);
            fidRport = jniGetFieldID(args->env, clsAllowed, "rport", "I");
        }

        jstring jraddr = NULL;
        if (fidRaddr == NULL || fidRport == NULL || (*args->env)->ExceptionCheck(args->env))
            exceptionOccurred = 1;
        else
            jraddr = (*args->env)->GetObjectField(args->env, jallowed, fidRaddr);
        ng_add_alloc(jraddr, "jraddr");
        if ((*args->env)->ExceptionCheck(args->env))
            exceptionOccurred = 1;
        if (jraddr == NULL)
            *allowed.raddr = 0;
        else if (!exceptionOccurred) {
            const char *raddr = (*args->env)->GetStringUTFChars(args->env, jraddr, NULL);
            ng_add_alloc(raddr, "raddr");
            if (raddr == NULL)
                exceptionOccurred = 1;
            else {
                if (strlen(raddr) >= sizeof(allowed.raddr))
                    exceptionOccurred = 1;
                else
                    strcpy(allowed.raddr, raddr);
                (*args->env)->ReleaseStringUTFChars(args->env, jraddr, raddr);
                ng_delete_alloc(raddr, __FILE__, __LINE__);
            }
        }
        if (!exceptionOccurred)
            allowed.rport = (uint16_t) (*args->env)->GetIntField(args->env, jallowed, fidRport);

        (*args->env)->DeleteLocalRef(args->env, jraddr);
        ng_delete_alloc(jraddr, __FILE__, __LINE__);
    }


    exceptionOccurred |= jniCheckException(args->env);
    (*args->env)->DeleteLocalRef(args->env, jpacket);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    (*args->env)->DeleteLocalRef(args->env, jallowed);
    ng_delete_alloc(jpacket, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);
    ng_delete_alloc(jallowed, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "is_address_allowed %f", mselapsed);
#endif

    return (jallowed == NULL || exceptionOccurred > 0 ? NULL : &allowed);
}

static jmethodID midConnectionUnavailable = NULL;

int check_tor_connection(const struct arguments *args) {

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    if (midConnectionUnavailable == NULL)
        midConnectionUnavailable = jniGetMethodID(args->env, clsService,
                                                  "suspectTorConnectionUnavailable", "()Z");

    jboolean checking = JNI_FALSE;
    if (clsService != NULL && midConnectionUnavailable != NULL &&
        !(*args->env)->ExceptionCheck(args->env))
        checking = (*args->env)->CallBooleanMethod(args->env, args->instance,
                                                  midConnectionUnavailable);
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

    return checking;
}

jmethodID midInitPacket = NULL;

jfieldID fidTime = NULL;
jfieldID fidVersion = NULL;
jfieldID fidProtocol = NULL;
jfieldID fidFlags = NULL;
jfieldID fidSaddr = NULL;
jfieldID fidSport = NULL;
jfieldID fidDaddr = NULL;
jfieldID fidDport = NULL;
jfieldID fidData = NULL;
jfieldID fidUid = NULL;
jfieldID fidAllowed = NULL;

jobject create_packet(const struct arguments *args,
                      jint version,
                      jint protocol,
                      const char *flags,
                      const char *source,
                      jint sport,
                      const char *dest,
                      jint dport,
                      const char *data,
                      jint uid,
                      jboolean allowed) {
    JNIEnv *env = args->env;

#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    /*
        jbyte b[] = {1,2,3};
        jbyteArray ret = env->NewByteArray(3);
        env->SetByteArrayRegion (ret, 0, 3, b);
     */

    const char *packet = "pan/alexander/tordnscrypt/vpn/Packet";
    if (midInitPacket == NULL)
        midInitPacket = jniGetMethodID(env, clsPacket, "<init>", "()V");
    jobject jpacket = jniNewObject(env, clsPacket, midInitPacket, packet);
    ng_add_alloc(jpacket, "jpacket");
    if (jpacket == NULL) {
        jniCheckException(env);
        return NULL;
    }

    if (fidAllowed == NULL) {
        const char *string = "Ljava/lang/String;";
        fidTime = jniGetFieldID(env, clsPacket, "time", "J");
        fidVersion = jniGetFieldID(env, clsPacket, "version", "I");
        fidProtocol = jniGetFieldID(env, clsPacket, "protocol", "I");
        fidFlags = jniGetFieldID(env, clsPacket, "flags", string);
        fidSaddr = jniGetFieldID(env, clsPacket, "saddr", string);
        fidSport = jniGetFieldID(env, clsPacket, "sport", "I");
        fidDaddr = jniGetFieldID(env, clsPacket, "daddr", string);
        fidDport = jniGetFieldID(env, clsPacket, "dport", "I");
        fidData = jniGetFieldID(env, clsPacket, "data", string);
        fidUid = jniGetFieldID(env, clsPacket, "uid", "I");
        fidAllowed = jniGetFieldID(env, clsPacket, "allowed", "Z");
    }

    struct timeval tv;
    gettimeofday(&tv, NULL);
    jlong t = tv.tv_sec * 1000LL + tv.tv_usec / 1000;
    jstring jflags = jniNewString(env, flags);
    jstring jsource = jniNewString(env, source);
    jstring jdest = jniNewString(env, dest);
    jstring jdata = jniNewString(env, data);
    ng_add_alloc(jflags, "jflags");
    ng_add_alloc(jsource, "jsource");
    ng_add_alloc(jdest, "jdest");
    ng_add_alloc(jdata, "jdata");

    int complete = fidTime != NULL && fidVersion != NULL && fidProtocol != NULL &&
            fidFlags != NULL && fidSaddr != NULL && fidSport != NULL && fidDaddr != NULL &&
            fidDport != NULL && fidData != NULL && fidUid != NULL && fidAllowed != NULL &&
            jflags != NULL && jsource != NULL && jdest != NULL && jdata != NULL;
    if (complete) {
        (*env)->SetLongField(env, jpacket, fidTime, t);
        (*env)->SetIntField(env, jpacket, fidVersion, version);
        (*env)->SetIntField(env, jpacket, fidProtocol, protocol);
        (*env)->SetObjectField(env, jpacket, fidFlags, jflags);
        (*env)->SetObjectField(env, jpacket, fidSaddr, jsource);
        (*env)->SetIntField(env, jpacket, fidSport, sport);
        (*env)->SetObjectField(env, jpacket, fidDaddr, jdest);
        (*env)->SetIntField(env, jpacket, fidDport, dport);
        (*env)->SetObjectField(env, jpacket, fidData, jdata);
        (*env)->SetIntField(env, jpacket, fidUid, uid);
        (*env)->SetBooleanField(env, jpacket, fidAllowed, allowed);
    }
    complete = !jniCheckException(env) && complete;

    (*env)->DeleteLocalRef(env, jdata);
    (*env)->DeleteLocalRef(env, jdest);
    (*env)->DeleteLocalRef(env, jsource);
    (*env)->DeleteLocalRef(env, jflags);
    ng_delete_alloc(jdata, __FILE__, __LINE__);
    ng_delete_alloc(jdest, __FILE__, __LINE__);
    ng_delete_alloc(jsource, __FILE__, __LINE__);
    ng_delete_alloc(jflags, __FILE__, __LINE__);
    if (!complete) {
        jniDeleteLocal(env, jpacket);
        return NULL;
    }
    // Caller needs to delete reference to packet

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "create_packet %f", mselapsed);
#endif

    return jpacket;
}

jmethodID midAccountUsage = NULL;
jmethodID midInitUsage = NULL;
jfieldID fidUsageTime = NULL;
jfieldID fidUsageVersion = NULL;
jfieldID fidUsageProtocol = NULL;
jfieldID fidUsageDAddr = NULL;
jfieldID fidUsageDPort = NULL;
jfieldID fidUsageUid = NULL;
jfieldID fidUsageSent = NULL;
jfieldID fidUsageReceived = NULL;

void account_usage(const struct arguments *args, jint version, jint protocol,
                   const char *daddr, jint dport, jint uid, jlong sent, jlong received) {
#ifdef PROFILE_JNI
    float mselapsed;
    struct timeval start, end;
    gettimeofday(&start, NULL);
#endif

    jclass clsService = (*args->env)->GetObjectClass(args->env, args->instance);
    ng_add_alloc(clsService, "clsService");

    const char *signature = "(Lpan/alexander/tordnscrypt/vpn/Usage;)V";
    if (midAccountUsage == NULL)
        midAccountUsage = jniGetMethodID(args->env, clsService, "accountUsage", signature);

    const char *usage = "pan/alexander/tordnscrypt/vpn/Usage";
    if (midInitUsage == NULL)
        midInitUsage = jniGetMethodID(args->env, clsUsage, "<init>", "()V");

    jobject jusage = jniNewObject(args->env, clsUsage, midInitUsage, usage);
    ng_add_alloc(jusage, "jusage");
    if (jusage == NULL || clsService == NULL || midAccountUsage == NULL) {
        jniCheckException(args->env);
        jniDeleteLocal(args->env, jusage);
        jniDeleteLocal(args->env, clsService);
        return;
    }

    if (fidUsageReceived == NULL) {
        const char *string = "Ljava/lang/String;";
        fidUsageTime = jniGetFieldID(args->env, clsUsage, "Time", "J");
        fidUsageVersion = jniGetFieldID(args->env, clsUsage, "Version", "I");
        fidUsageProtocol = jniGetFieldID(args->env, clsUsage, "Protocol", "I");
        fidUsageDAddr = jniGetFieldID(args->env, clsUsage, "DAddr", string);
        fidUsageDPort = jniGetFieldID(args->env, clsUsage, "DPort", "I");
        fidUsageUid = jniGetFieldID(args->env, clsUsage, "Uid", "I");
        fidUsageSent = jniGetFieldID(args->env, clsUsage, "Sent", "J");
        fidUsageReceived = jniGetFieldID(args->env, clsUsage, "Received", "J");
    }

    jlong jtime = time(NULL) * 1000LL;
    jstring jdaddr = jniNewString(args->env, daddr);
    ng_add_alloc(jdaddr, "jdaddr");

    if (fidUsageTime != NULL && fidUsageVersion != NULL && fidUsageProtocol != NULL &&
        fidUsageDAddr != NULL && fidUsageDPort != NULL && fidUsageUid != NULL &&
        fidUsageSent != NULL && fidUsageReceived != NULL && jdaddr != NULL) {
        (*args->env)->SetLongField(args->env, jusage, fidUsageTime, jtime);
        (*args->env)->SetIntField(args->env, jusage, fidUsageVersion, version);
        (*args->env)->SetIntField(args->env, jusage, fidUsageProtocol, protocol);
        (*args->env)->SetObjectField(args->env, jusage, fidUsageDAddr, jdaddr);
        (*args->env)->SetIntField(args->env, jusage, fidUsageDPort, dport);
        (*args->env)->SetIntField(args->env, jusage, fidUsageUid, uid);
        (*args->env)->SetLongField(args->env, jusage, fidUsageSent, sent);
        (*args->env)->SetLongField(args->env, jusage, fidUsageReceived, received);

        (*args->env)->CallVoidMethod(args->env, args->instance, midAccountUsage, jusage);
    }
    jniCheckException(args->env);

    (*args->env)->DeleteLocalRef(args->env, jdaddr);
    (*args->env)->DeleteLocalRef(args->env, jusage);
    (*args->env)->DeleteLocalRef(args->env, clsService);
    ng_delete_alloc(jdaddr, __FILE__, __LINE__);
    ng_delete_alloc(jusage, __FILE__, __LINE__);
    ng_delete_alloc(clsService, __FILE__, __LINE__);

#ifdef PROFILE_JNI
    gettimeofday(&end, NULL);
    mselapsed = (end.tv_sec - start.tv_sec) * 1000.0 +
                (end.tv_usec - start.tv_usec) / 1000.0;
    if (mselapsed > PROFILE_JNI)
        log_android(ANDROID_LOG_WARN, "log_packet %f", mselapsed);
#endif
}

#ifdef PROFILE_MEMORY
struct alloc_record {
    const char *tag;
    time_t time;
    const void *ptr;
};

static size_t allocs = 0;
static size_t balance = 0;
static struct alloc_record *alloc = NULL;
static pthread_mutex_t alock = PTHREAD_MUTEX_INITIALIZER;
static bool tracking_disabled = false;

// Called with alock held. Partial records cannot describe a reliable balance.
static void disable_memory_tracking(void) {
    tracking_disabled = true;
    free(alloc);
    alloc = NULL;
    allocs = balance = 0;
    log_android(ANDROID_LOG_ERROR, "PROFILE_MEMORY disabled: cannot grow allocation records");
}

static void record_allocation(const void *ptr, const char *tag) {
    if (tracking_disabled)
        return;

    size_t c = 0;
    for (; c < allocs; c++)
        if (alloc[c].ptr == NULL)
            break;

    if (c == allocs) {
        if (allocs >= SIZE_MAX / sizeof(*alloc)) {
            disable_memory_tracking();
            return;
        }
        struct alloc_record *records = realloc(alloc, sizeof(*alloc) * (allocs + 1));
        if (records == NULL) {
            disable_memory_tracking();
            return;
        }
        alloc = records;
        allocs++;
    }

    alloc[c].tag = tag;
    alloc[c].time = time(NULL);
    alloc[c].ptr = ptr;
    balance++;
}
#endif

void ng_add_alloc(const char *ptr, const char *tag) {
#ifdef PROFILE_MEMORY
    if (ptr == NULL)
        return;

    int saved_errno = errno;
    int error = pthread_mutex_lock(&alock);
    if (error != 0) {
        log_android(ANDROID_LOG_ERROR, "PROFILE_MEMORY pthread_mutex_lock error %d", error);
        errno = saved_errno;
        return;
    }

    record_allocation(ptr, tag);

    error = pthread_mutex_unlock(&alock);
    if (error != 0)
        log_android(ANDROID_LOG_ERROR, "PROFILE_MEMORY pthread_mutex_unlock error %d", error);
    errno = saved_errno;
#endif
}

void ng_delete_alloc(const char *ptr, const char *file, int line) {
#ifdef PROFILE_MEMORY
    if (ptr == NULL)
        return;

    int saved_errno = errno;
    int error = pthread_mutex_lock(&alock);
    if (error != 0) {
        log_android(ANDROID_LOG_ERROR, "PROFILE_MEMORY pthread_mutex_lock error %d", error);
        errno = saved_errno;
        return;
    }

    if (!tracking_disabled) {
        bool found = false;
        for (size_t c = 0; c < allocs; c++)
            if (alloc[c].ptr == ptr) {
                found = true;
                alloc[c].tag = "[free]";
                alloc[c].ptr = NULL;
                break;
            }

        if (found)
            balance--;

        log_android(found ? ANDROID_LOG_DEBUG : ANDROID_LOG_ERROR,
                    "alloc/free balance %zu records %zu found %d", balance, allocs, found);
        if (!found)
            log_android(ANDROID_LOG_ERROR, "Not found at %s:%d", file == NULL ? "unknown" : file, line);

        if (balance == 0) {
            free(alloc);
            alloc = NULL;
            allocs = 0;
        }
    }

    error = pthread_mutex_unlock(&alock);
    if (error != 0)
        log_android(ANDROID_LOG_ERROR, "PROFILE_MEMORY pthread_mutex_unlock error %d", error);
    errno = saved_errno;
#endif
}

void *ng_malloc(size_t __byte_count, const char *tag) {
    void *ptr = malloc(__byte_count);
    ng_add_alloc(ptr, tag);
    return ptr;
}

void *ng_calloc(size_t __item_count, size_t __item_size, const char *tag) {
    void *ptr = calloc(__item_count, __item_size);
    ng_add_alloc(ptr, tag);
    return ptr;
}

void *ng_realloc(void *__ptr, size_t __byte_count, const char *tag) {
    ng_delete_alloc(__ptr, NULL, 0);
    void *ptr = realloc(__ptr, __byte_count);
    // A failed nonzero resize leaves the original allocation owned by the caller.
    ng_add_alloc(ptr == NULL && __byte_count != 0 ? __ptr : ptr, tag);
    return ptr;
}

void ng_free(void *__ptr, const char *file, int line) {
    ng_delete_alloc(__ptr, file, line);
    free(__ptr);
}

#pragma clang diagnostic pop
