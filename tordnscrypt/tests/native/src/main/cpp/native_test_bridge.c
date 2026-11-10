#include <setjmp.h>
#include <stdio.h>
#include "test_support.h"

static jmp_buf test_checkpoint;
static char test_failure[1024];
static int previous_failure;

// Report native assertions as JUnit failures instead of aborting instrumentation.
void native_test_fail(const char *condition, const char *file, int line) {
    snprintf(test_failure, sizeof(test_failure), "%s:%d: %s", file, line, condition);
    longjmp(test_checkpoint, 1);
}

static const char *run_case(const char *id) {
    if (previous_failure)
        return "A previous native assertion failed; restart instrumentation to reset resources";

    reset_test_state();
    if (setjmp(test_checkpoint)) {
        // An interrupted case may still own descriptors. Do not reuse its state.
        previous_failure = 1;
        return test_failure;
    }

    struct context ctx = {.sdk = 29};
    struct arguments args = {.ctx = &ctx, .tun = -1, .fwd53 = 1, .rcode = 3};
    if (strcmp(id, "memoryNames") == 0)
        test_names();
    else if (strcmp(id, "memoryDns") == 0)
        test_dns(&args);
    else if (strcmp(id, "memoryPackets") == 0)
        test_packets(&args);
    else if (strcmp(id, "memoryTcp") == 0)
        test_tcp(&args);
    else if (strcmp(id, "tcpSocketCleanup") == 0)
        test_tcp_socket_cleanup(&args);
    else if (strcmp(id, "udpSocketCleanup") == 0)
        test_udp_socket_cleanup(&args);
    else if (strcmp(id, "icmpSocketCleanup") == 0)
        test_icmp_socket_cleanup(&args);
    else if (strcmp(id, "uidFileCleanup") == 0)
        test_uid_file_cleanup();
    else if (strcmp(id, "dhcpResponseBounds") == 0)
        test_dhcp_response_bounds(&args);
    else if (strcmp(id, "tcpDataCleanup") == 0)
        test_tcp_data_cleanup();
    else if (strcmp(id, "eventLoopShutdown") == 0)
        test_event_loop_shutdown(&args);
    else if (strcmp(id, "udpShortDns") == 0)
        test_udp_short_dns(&args);
    else if (strcmp(id, "memorySocks5") == 0)
        test_socks5();
    else if (strcmp(id, "udp4Direct") == 0)
        udp_flow(4, 0, 0);
    else if (strcmp(id, "udp4Redirect") == 0)
        udp_flow(4, 1, 0);
    else if (strcmp(id, "udp4Options") == 0)
        udp_flow(4, 0, 1);
    else if (strcmp(id, "udp6Direct") == 0)
        udp_flow(6, 0, 0);
    else if (strcmp(id, "udp6Redirect") == 0)
        udp_flow(6, 1, 0);
    else if (strcmp(id, "udp6Extensions") == 0)
        udp_flow(6, 0, 1);
    else if (strcmp(id, "tcp4") == 0)
        tcp_flow(4);
    else if (strcmp(id, "tcp6") == 0)
        tcp_flow(6);
    else if (strcmp(id, "icmp4") == 0)
        icmp_packets(4);
    else if (strcmp(id, "icmp6") == 0)
        icmp_packets(6);
    else if (strcmp(id, "socks4NoAuth") == 0)
        socks_flow(4, 0, 0);
    else if (strcmp(id, "socks4Auth") == 0)
        socks_flow(4, 1, 0);
    else if (strcmp(id, "socks6Auth") == 0)
        socks_flow(6, 1, 1);
    else if (strcmp(id, "dnsResponses") == 0)
        dns_flows();
    else if (strcmp(id, "dhcpOffer") == 0)
        dhcp_offer();
    else if (strcmp(id, "compatibility") == 0)
        compatibility_suite();
    else
        return "Unknown native regression case";

    assert(allocations == 0 && sockets == 0 && string_refs == 0);
    return NULL;
}

#ifndef INVIZIBLE_BASELINE_TESTS
JNIEXPORT jstring JNICALL
Java_pan_alexander_tordnscrypt_nativetests_NativeTestSupport_runNativeCase(
        JNIEnv *env, jclass cls, jstring name) {
    const char *utf = (*env)->GetStringUTFChars(env, name, NULL);
    if (utf == NULL)
        return NULL;
    char id[64];
    snprintf(id, sizeof(id), "%s", utf);
    (*env)->ReleaseStringUTFChars(env, name, utf);
    const char *error = run_case(id);
    return error ? (*env)->NewStringUTF(env, error) : NULL;
}

JNIEXPORT jboolean JNICALL
Java_pan_alexander_tordnscrypt_nativetests_NativeTestSupport_sanitizersEnabled(
        JNIEnv *env, jclass cls) {
#if __has_feature(address_sanitizer)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}
#define COMPATIBILITY_ENTRY Java_pan_alexander_tordnscrypt_nativetests_NativeBaselineComparisonTest_runCurrentCompatibility
#else
#define COMPATIBILITY_ENTRY Java_pan_alexander_tordnscrypt_nativetests_NativeBaselineComparisonTest_runBaselineCompatibility
#endif

// Capture only deterministic packet fingerprints/callbacks for the comparison.
JNIEXPORT jstring JNICALL COMPATIBILITY_ENTRY(JNIEnv *env, jclass cls, jstring path) {
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    if (utf == NULL)
        return NULL;
    FILE *output = __real_fopen(utf, "w+");
    (*env)->ReleaseStringUTFChars(env, path, utf);
    fflush(stdout);
    int saved_stdout = dup(STDOUT_FILENO);
    const char *error = NULL;
    char *transcript = NULL;
    if (output == NULL || saved_stdout < 0)
        error = "Cannot open compatibility transcript";
    else if (dup2(fileno(output), STDOUT_FILENO) < 0)
        error = "Cannot redirect compatibility transcript";
    else {
        error = run_case("compatibility");
        fflush(stdout);
        if (dup2(saved_stdout, STDOUT_FILENO) < 0)
            error = "Cannot restore stdout";
        if (!error) {
            long length = ftell(output);
            if (length <= 0 || length > 1024 * 1024 || fseek(output, 0, SEEK_SET) != 0)
                error = "Invalid compatibility transcript size";
            else {
                transcript = malloc((size_t) length + 1);
                if (!transcript || fread(transcript, 1, (size_t) length, output) != (size_t) length)
                    error = "Cannot read compatibility transcript";
                else
                    transcript[length] = 0;
            }
        }
    }
    if (saved_stdout >= 0)
        __real_close(saved_stdout);
    if (output)
        __real_fclose(output);
    jstring result = NULL;
    if (error) {
        jclass assertion = (*env)->FindClass(env, "java/lang/AssertionError");
        if (assertion)
            (*env)->ThrowNew(env, assertion, error);
    } else
        result = (*env)->NewStringUTF(env, transcript);
    free(transcript);
    return result;
}
