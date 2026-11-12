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
    if (strcmp(id, "sessionIndex4") == 0)
        test_session_index_keys(&args, 4);
    else if (strcmp(id, "sessionIndex6") == 0)
        test_session_index_keys(&args, 6);
    else if (strcmp(id, "sessionIndexChurn") == 0)
        test_session_index_churn(&args);
    else if (strcmp(id, "sessionIndexPolicy") == 0)
        test_session_index_policy(&args);
    else if (strcmp(id, "tcpScaledCapacity4") == 0)
        test_tcp_scaled_capacity(&args, 4);
    else if (strcmp(id, "tcpScaledCapacity6") == 0)
        test_tcp_scaled_capacity(&args, 6);
    else if (strcmp(id, "tcpMemoryRescan") == 0)
        test_tcp_memory_rescan(&args, 0);
    else if (strcmp(id, "tcpMonitorCache") == 0)
        test_tcp_monitor_cache(&args, 0);
    else if (strcmp(id, "tcpMonitorDnsOutput") == 0)
        test_tcp_monitor_cache(&args, 1);
    else if (strcmp(id, "tcpMemoryNoWaiters") == 0)
        test_tcp_memory_rescan(&args, 1);
    else if (strcmp(id, "tcpMemoryQueueWaitDnsRelease") == 0)
        test_tcp_memory_rescan(&args, 2);
    else if (strcmp(id, "tcpMemoryDnsWaitQueueRelease") == 0)
        test_tcp_memory_rescan(&args, 3);
    else if (strcmp(id, "tcpMemoryDnsRescan") == 0)
        test_tcp_memory_rescan(&args, 4);
    else if (strcmp(id, "tcpMemorySocketWait") == 0)
        test_tcp_memory_rescan(&args, 5);
    else if (strcmp(id, "tcpMemoryBytes") == 0)
        test_tcp_memory_budget(&args, 0);
    else if (strcmp(id, "tcpMemoryNodes") == 0)
        test_tcp_memory_budget(&args, 1);
    else if (strcmp(id, "tcpMemoryReplacement") == 0)
        test_tcp_memory_budget(&args, 2);
    else if (strcmp(id, "tcpMemoryWakeup") == 0)
        test_tcp_memory_wakeup(&args);
    else if (strcmp(id, "tcpDnsMemoryPrefix") == 0)
        test_tcp_dns_memory(0);
    else if (strcmp(id, "tcpDnsMemoryGrowth") == 0)
        test_tcp_dns_memory(1);
    else if (strcmp(id, "tcpDnsMemoryHangup") == 0)
        test_tcp_dns_memory(2);
    else if (strcmp(id, "uidProcAddresses") == 0)
        test_uid_proc_parser(0);
    else if (strcmp(id, "uidProcMalformed") == 0)
        test_uid_proc_parser(1);
    else if (strcmp(id, "uidProcMatchOrder") == 0)
        test_uid_proc_parser(2);
    else if (strcmp(id, "uidUpdate4") == 0)
        test_uid_indexed_updates(0);
    else if (strcmp(id, "uidUpdate6") == 0)
        test_uid_indexed_updates(1);
    else if (strcmp(id, "uidUpdateFallback") == 0)
        test_uid_indexed_updates(2);
    else if (strcmp(id, "uidUpdateGrowthFailure") == 0)
        test_uid_indexed_growth_failure();
    else if (strcmp(id, "tcpCapacityReuse") == 0)
        test_tcp_capacity_reuse(&args);
    else if (strcmp(id, "tcpWindowReopen4") == 0)
        test_tcp_window_reopen(&args, 4);
    else if (strcmp(id, "tcpWindowReopen6") == 0)
        test_tcp_window_reopen(&args, 6);
    else if (strcmp(id, "tcpProbeDeadlines") == 0)
        test_tcp_probe_deadlines(&args);
    else if (strcmp(id, "tcpWindowProbes") == 0)
        test_tcp_backpressure(&args, 0);
    else if (strcmp(id, "tcpWriteBackpressure") == 0)
        test_tcp_backpressure(&args, 1);
    else if (strcmp(id, "udpPacketLookup") == 0)
        test_udp_packet_lookup(&args, 0);
    else if (strcmp(id, "udpDnsLookupPolicy") == 0)
        test_udp_packet_lookup(&args, 1);
    else if (strcmp(id, "udpLookupStates") == 0)
        test_udp_lookup_states(&args);
    else if (strcmp(id, "tcpDnsBufferReuse") == 0)
        test_tcp_dns_buffer(12);
    else if (strcmp(id, "eventBufferCleanup") == 0)
        test_event_buffer_cleanup(&args);
    else if (strcmp(id, "packetBuffers") == 0)
        test_packet_buffers(&args);
    else if (strcmp(id, "tunBufferReuse") == 0)
        test_tun_buffer_reuse(&args);
    else if (strcmp(id, "tcpBufferReuse") == 0)
        test_tcp_buffer_reuse(&args);
    else if (strcmp(id, "udpBufferReuse") == 0)
        test_udp_buffer_reuse(&args);
    else if (strcmp(id, "icmpBufferReuse") == 0)
        test_icmp_buffer_reuse(&args);
    else if (strcmp(id, "lazyLogging") == 0)
        test_lazy_logging();
    else if (strcmp(id, "lazyTcpLogging") == 0)
        test_lazy_tcp_logging(&args);
    else if (strcmp(id, "lazyUdpLogging") == 0)
        test_lazy_udp_logging(&args);
    else if (strcmp(id, "jniLoadFailures") == 0)
        test_jni_load_failures();
    else if (strcmp(id, "ipChecksumHeader") == 0)
        test_ip_checksums(&args, 0);
    else if (strcmp(id, "ipChecksumUdp4") == 0)
        test_ip_checksums(&args, 1);
    else if (strcmp(id, "ipChecksumUdp6") == 0)
        test_ip_checksums(&args, 2);
    else if (strcmp(id, "ipChecksumTcp4") == 0)
        test_ip_checksums(&args, 3);
    else if (strcmp(id, "ipChecksumTcp6") == 0)
        test_ip_checksums(&args, 4);
    else if (strcmp(id, "ipChecksumIcmp4") == 0)
        test_ip_checksums(&args, 5);
    else if (strcmp(id, "ipChecksumIcmp6") == 0)
        test_ip_checksums(&args, 6);
    else if (strcmp(id, "ipChecksumExtensions") == 0)
        test_ip_checksums(&args, 7);
    else if (strcmp(id, "ipChecksumRouting") == 0)
        test_ip_checksums(&args, 8);
    else if (strcmp(id, "ipChecksumZero") == 0)
        test_ip_checksums(&args, 9);
    else if (strcmp(id, "dnsQuestionCounts") == 0)
        test_dns_question_counts();
    else if (strcmp(id, "dnsTcpFraming") == 0)
        test_dns_tcp_framing();
    else if (strcmp(id, "udpDnsRequest4") == 0)
        test_udp_dns_counts(4, 0);
    else if (strcmp(id, "udpDnsRequest6") == 0)
        test_udp_dns_counts(6, 0);
    else if (strcmp(id, "udpDnsResponse4") == 0)
        test_udp_dns_counts(4, 1);
    else if (strcmp(id, "udpDnsResponse6") == 0)
        test_udp_dns_counts(6, 1);
    else if (strcmp(id, "tcpDnsResponse4") == 0)
        test_tcp_dns_counts(4);
    else if (strcmp(id, "tcpDnsResponse6") == 0)
        test_tcp_dns_counts(6);
    else if (strcmp(id, "tcpDnsCoalesced") == 0)
        test_tcp_dns_buffer(0);
    else if (strcmp(id, "tcpDnsPartialTail") == 0)
        test_tcp_dns_buffer(1);
    else if (strcmp(id, "tcpDnsByteSplits") == 0)
        test_tcp_dns_buffer(2);
    else if (strcmp(id, "tcpDnsAllocFailure") == 0)
        test_tcp_dns_buffer(3);
    else if (strcmp(id, "tcpDnsResizeFailure") == 0)
        test_tcp_dns_buffer(4);
    else if (strcmp(id, "tcpDnsPartialTimeout") == 0)
        test_tcp_dns_buffer(5);
    else if (strcmp(id, "tcpDnsPartialEof") == 0)
        test_tcp_dns_buffer(6);
    else if (strcmp(id, "tcpDnsWindow") == 0)
        test_tcp_dns_buffer(7);
    else if (strcmp(id, "tcpDnsWriteFailure") == 0)
        test_tcp_dns_buffer(8);
    else if (strcmp(id, "tcpDnsTeardown") == 0)
        test_tcp_dns_buffer(9);
    else if (strcmp(id, "tcpDnsMaxFrame") == 0)
        test_tcp_dns_buffer(10);
    else if (strcmp(id, "tcpDnsUpstreamBackpressure") == 0)
        test_tcp_dns_buffer(11);
    else if (strcmp(id, "tcpDestination") == 0)
        test_tcp_destination(&args);
    else if (strcmp(id, "udpDestination") == 0)
        test_udp_destination(&args);
    else if (strcmp(id, "icmpDestination") == 0)
        test_icmp_destination(&args);
    else if (strcmp(id, "nativeInitialization") == 0)
        test_native_initialization();
    else if (strcmp(id, "boundedLogging") == 0)
        test_bounded_logging();
    else if (strcmp(id, "exceptionFailures") == 0)
        test_exception_failures();
    else if (strcmp(id, "atomicFlags") == 0)
        test_atomic_flags();
    else if (strcmp(id, "tcpAllocationFailure") == 0)
        test_tcp_allocation_failure(&args);
    else if (strcmp(id, "tcpQueueBytes") == 0)
        test_tcp_queue_limits(&args, 0);
    else if (strcmp(id, "tcpQueueSegments") == 0)
        test_tcp_queue_limits(&args, 1);
    else if (strcmp(id, "tcpQueueWindow") == 0)
        test_tcp_queue_limits(&args, 2);
    else if (strcmp(id, "tcpGapBytes") == 0)
        test_tcp_gap_recovery(&args, 0);
    else if (strcmp(id, "tcpGapSegments") == 0)
        test_tcp_gap_recovery(&args, 1);
    else if (strcmp(id, "tcpGapOverlap") == 0)
        test_tcp_gap_recovery(&args, 2);
    else if (strcmp(id, "tcpPartialRetransmission") == 0)
        test_tcp_partial_retransmission(&args);
    else if (strcmp(id, "jniPacketFailures") == 0)
        test_jni_allocation_failures(0);
    else if (strcmp(id, "jniAllowedFailures") == 0)
        test_jni_allocation_failures(1);
    else if (strcmp(id, "jniDnsFailures") == 0)
        test_jni_allocation_failures(2);
    else if (strcmp(id, "jniUsageFailures") == 0)
        test_jni_allocation_failures(3);
    else if (strcmp(id, "udpAllocationFailure") == 0)
        test_udp_allocation_failure(&args);
    else if (strcmp(id, "icmpAllocationFailure") == 0)
        test_icmp_allocation_failure(&args);
    else if (strcmp(id, "dnsAllocationFailure") == 0)
        test_dns_allocation_failure(&args);
    else if (strcmp(id, "dhcpAllocationFailure") == 0)
        test_dhcp_allocation_failure(&args);
    else if (strcmp(id, "tunAllocationFailure") == 0)
        test_tun_allocation_failure(&args);
    else if (strcmp(id, "memoryNames") == 0)
        test_names();
    else if (strcmp(id, "memoryDns") == 0)
        test_dns(&args);
    else if (strcmp(id, "memoryPackets") == 0)
        test_packets(&args);
    else if (strcmp(id, "memoryTcp") == 0)
        test_tcp(&args);
    else if (strcmp(id, "tcpSocketCleanup") == 0)
        test_tcp_socket_cleanup(&args);
    else if (strcmp(id, "tcpConnectFailure4") == 0)
        test_tcp_connect_failure(4, 0);
    else if (strcmp(id, "tcpConnectFailure6") == 0)
        test_tcp_connect_failure(6, 0);
    else if (strcmp(id, "tcpConnectTransient4") == 0)
        test_tcp_connect_failure(4, 1);
    else if (strcmp(id, "tcpConnectTransient6") == 0)
        test_tcp_connect_failure(6, 1);
    else if (strcmp(id, "tcpSetupRetry4") == 0)
        test_tcp_connect_failure(4, 2);
    else if (strcmp(id, "tcpSetupRetry6") == 0)
        test_tcp_connect_failure(6, 2);
    else if (strcmp(id, "udpSocketCleanup") == 0)
        test_udp_socket_cleanup(&args);
    else if (strcmp(id, "icmpSocketCleanup") == 0)
        test_icmp_socket_cleanup(&args);
    else if (strcmp(id, "uidFileCleanup") == 0)
        test_uid_file_cleanup();
    else if (strcmp(id, "uidCacheAllocationFailure") == 0)
        test_uid_cache_allocation_failure();
    else if (strcmp(id, "uidCacheRefresh") == 0)
        test_uid_cache_refresh();
    else if (strcmp(id, "uidCacheLimit") == 0)
        test_uid_cache_limit();
    else if (strcmp(id, "uidCacheKeys") == 0)
        test_uid_cache_keys();
    else if (strcmp(id, "dhcpResponseBounds") == 0)
        test_dhcp_response_bounds(&args);
    else if (strcmp(id, "tcpDataCleanup") == 0)
        test_tcp_data_cleanup();
    else if (strcmp(id, "tcpHalfClose4") == 0)
        test_tcp_half_close(0);
    else if (strcmp(id, "tcpHalfClose6") == 0)
        test_tcp_half_close(1);
    else if (strcmp(id, "tcpFinGap") == 0)
        test_tcp_half_close(2);
    else if (strcmp(id, "tcpAppFinFirst") == 0)
        test_tcp_half_close(3);
    else if (strcmp(id, "tcpSimultaneousFin") == 0)
        test_tcp_half_close(4);
    else if (strcmp(id, "tcpHalfCloseTimeout") == 0)
        test_tcp_half_close(5);
    else if (strcmp(id, "tcpShutdownFailure") == 0)
        test_tcp_half_close(6);
    else if (strcmp(id, "tcpHalfCloseReset") == 0)
        test_tcp_half_close(7);
    else if (strcmp(id, "tcpSessionUid") == 0)
        test_tcp_half_close(8);
    else if (strcmp(id, "tcpEofQueued") == 0)
        test_tcp_half_close(9);
    else if (strcmp(id, "tcpClosedDataCleanup") == 0)
        test_tcp_closed_data_cleanup(&args);
    else if (strcmp(id, "closedUdpLimit") == 0)
        test_closed_session_limit(&args, 0);
    else if (strcmp(id, "closedTcpLimit") == 0)
        test_closed_session_limit(&args, 1);
    else if (strcmp(id, "closedMixedLimit") == 0)
        test_closed_session_limit(&args, 2);
    else if (strcmp(id, "eventLoopShutdown") == 0)
        test_event_loop_shutdown(&args);
    else if (strcmp(id, "idleUdpClosed") == 0)
        test_event_loop_idle_cleanup(&args, 0);
    else if (strcmp(id, "idleUdpBlocked") == 0)
        test_event_loop_idle_cleanup(&args, 1);
    else if (strcmp(id, "idleTcpClosed") == 0)
        test_event_loop_idle_cleanup(&args, 2);
    else if (strcmp(id, "idleUdpFinishing") == 0)
        test_event_loop_idle_cleanup(&args, 3);
    else if (strcmp(id, "idleTcpClosing") == 0)
        test_event_loop_idle_cleanup(&args, 4);
    else if (strcmp(id, "idleMixed") == 0)
        test_event_loop_idle_cleanup(&args, 5);
    else if (strcmp(id, "udpBlocked4Limit") == 0)
        test_udp_blocked_limit(&args, 4);
    else if (strcmp(id, "udpBlocked6Limit") == 0)
        test_udp_blocked_limit(&args, 6);
    else if (strcmp(id, "udpBlockedDuplicates") == 0)
        test_udp_blocked_duplicates(&args);
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
#ifndef INVIZIBLE_BASELINE_TESTS
    assert(ctx.tcp_memory.queued == 0 && ctx.tcp_memory.nodes == 0 && ctx.tcp_memory.dns == 0);
    for (size_t i = 0; i < SESSION_BUCKETS; i++) assert(ctx.session_bucket[i] == NULL);
#endif
    return NULL;
}

#ifndef INVIZIBLE_BASELINE_TESTS
void Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1run(
        JNIEnv *, jobject, jlong, jint, jboolean, jint, jboolean, jboolean, jboolean);

JNIEXPORT jboolean JNICALL
Java_pan_alexander_tordnscrypt_nativetests_InvizibleTest_runArgumentsFailure(
        JNIEnv *env, jobject instance) {
    reset_test_state();
    struct context ctx = {0};
    fail_alloc_tag = "arguments";
    fail_alloc_match = 1;
    real_exit_reports = 1;
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1run(
            env, instance, (jlong) &ctx, -1, 0, 0, 0, 0, 0);
    real_exit_reports = 0;
    fail_alloc_tag = NULL;
    return fail_alloc_match == 0 && allocations == 0 && sockets == 0 &&
            exit_reports == 1 && !(*env)->ExceptionCheck(env);
}

JNIEXPORT jboolean JNICALL
Java_pan_alexander_tordnscrypt_nativetests_InvizibleTest_reportException(
        JNIEnv *env, jclass cls, jthrowable exception) {
    (*env)->Throw(env, exception);
    int found = jniCheckException(env);
    return found && !(*env)->ExceptionCheck(env);
}

void __real_report_exit(const struct arguments *args, const char *fmt, ...);
JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_nativetests_InvizibleTest_reportLongExit(
        JNIEnv *env, jobject instance) {
    struct arguments args = {.env = env, .instance = instance};
    char reason[16384];
    memset(reason, 'x', sizeof(reason) - 1);
    reason[sizeof(reason) - 1] = 0;
    __real_report_exit(&args, "%s", reason);
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_Socks5SettingsTest_validateProxy(
        JNIEnv *env, jclass cls, jstring address, jstring user, jstring password) {
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1validate_1socks5(
            env, NULL, address, user, password);
}

JNIEXPORT jint JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_Socks5SettingsTest_encodedLength(
        JNIEnv *env, jclass cls, jstring value) {
    return (*env)->GetStringUTFLength(env, value);
}

JNIEXPORT void JNICALL
Java_pan_alexander_tordnscrypt_vpn_service_Socks5SettingsTest_configureProxy(
        JNIEnv *env, jclass cls, jstring address, jstring user, jstring password) {
    reset_test_state();
    Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
            env, NULL, address, 1080, user, password);
}

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
