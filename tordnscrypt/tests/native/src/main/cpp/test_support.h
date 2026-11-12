#ifndef INVIZIBLE_TEST_SUPPORT_H
#define INVIZIBLE_TEST_SUPPORT_H

#include "invizible.h"
#include <assert.h>

#ifdef INVIZIBLE_COMBINED_TCP_SEGMENTS
#define TCP_SEGMENT_ALLOCS 1
#else
#define TCP_SEGMENT_ALLOCS 2
#endif
struct segment *new_test_segment(uint16_t length);
extern unsigned int tcp_sequence_compares;
void test_tcp_queue_storage(const struct arguments *args, int kind);

// Turn native assertions into JUnit failures through the shared JNI checkpoint.
void native_test_fail(const char *condition, const char *file, int line)
        __attribute__((noreturn));
#undef assert
#define assert(condition) ((condition) ? (void) 0 : native_test_fail(#condition, __FILE__, __LINE__))

enum failure { NONE, PROTECT, GETFL, SETFL, CONNECT, PIPE, MUTEX, SHUTDOWN };
extern enum failure failure;
extern int allocations, sockets, last_socket, udp_calls, resolved_calls;
extern int tcp_calls, icmp_calls;
extern int string_refs, string_calls, fail_string_call, exceptions, file_closes;
extern int capture_packets, capture_dhcp, fake_proc;
extern const char *proc_contents;
extern int fail_cache_alloc;
extern const char *fail_alloc_tag;
extern int fail_alloc_match, fail_fcntl_call, fcntl_calls;
extern char last_log[2048];
extern int address_formats;
extern int tcp_sendbuf_queries, tcp_outq_queries;
extern int tcp_capacity_fd, tcp_capacity_available;
void test_tcp_scaled_capacity(const struct arguments *args, int version);
void test_tcp_capacity_reuse(const struct arguments *args);
void test_tcp_memory_budget(const struct arguments *args, int kind);
void test_tcp_memory_wakeup(const struct arguments *args);
void test_tcp_memory_rescan(struct arguments *args, int kind);
void test_tcp_monitor_cache(struct arguments *args, int kind);
void test_tcp_dns_memory(int kind);
extern int udp_lookups, uid_lookups, policy_checks, deny_address;
extern int udp_lookup_supplied;
extern struct ng_session *udp_lookup_session;
void test_udp_packet_lookup(struct arguments *args, int kind);
void test_udp_lookup_states(struct arguments *args);
extern int real_exit_reports, exit_reports;
extern int uid_cache_size;
#ifndef INVIZIBLE_BASELINE_TESTS
extern int uid_cache_capacity;
#endif
extern int uid_cache_allocations;
void test_uid_proc_parser(int kind);
void test_uid_indexed_updates(int kind);
void test_uid_indexed_growth_failure(void);
extern struct uid_cache_entry *uid_cache;
// Older baseline exports do not define the cache cap; only current code runs these cases.
#ifndef UID_CACHE_MAX
#define UID_CACHE_MAX 4096
#endif
#ifndef UDP_BLOCKED_MAX
#define UDP_BLOCKED_MAX 1024
#endif
extern int real_network, real_udp_packets, block_domain, trace_dns;
extern uint64_t accounted_sent, accounted_received;
extern unsigned int cases;
extern int destination_calls;
extern int connect_errno, connect_fd;
void expect_destination(int version, const char *address, uint16_t port);
extern const struct JNINativeInterface jni;

extern int own_uid, tor_dns_port, tor_socks5_port, proxy_socks5_port;
extern bool bypass_lan, tor_isolate_uid;
#ifdef INVIZIBLE_ATOMIC_FLAGS
extern atomic_bool tor_connection_available;
#else
extern bool tor_connection_available;
#endif
extern char tor_socks5_addr[INET6_ADDRSTRLEN + 1], proxy_socks5_addr[INET6_ADDRSTRLEN + 1];
extern char tor_socks5_username[128], tor_socks5_password[128];
extern char proxy_socks5_username[128], proxy_socks5_password[128];
void Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1proxy(
        JNIEnv *, jobject, jstring, jint, jstring, jstring);
void Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1socks5_1for_1tor(
        JNIEnv *, jobject, jstring, jint, jstring, jstring, jboolean, jint);
void Java_pan_alexander_tordnscrypt_vpn_service_ServiceVPN_jni_1validate_1socks5(
        JNIEnv *, jobject, jstring, jstring, jstring);

int __real_socket(int domain, int type, int protocol);
int __real_close(int fd);
FILE *__real_fopen(const char *path, const char *mode);
int __real_fclose(FILE *file);
void reset_test_state(void);
void set_test_sessions(struct context *ctx, struct ng_session *head);
void assert_test_session_index(const struct context *ctx);
void test_session_index_keys(struct arguments *args, int version);
void test_session_index_churn(struct arguments *args);
void test_session_index_policy(struct arguments *args);
uint32_t fingerprint(const void *data, size_t len);
uint16_t load16(const uint8_t *p);
void store16(uint8_t *p, uint16_t value);
void readable_at(int fd, int line);
#define readable(fd) readable_at(fd, __LINE__)
size_t tun_packet(int fd, uint8_t *packet, int version, int protocol);
size_t ip_header(uint8_t *packet, int version, int protocol, size_t data_len,
                 const char *destination);
void packet_checksums(uint8_t *packet, size_t length, size_t payload, int protocol);
void test_ip_checksums(const struct arguments *args, int kind);

// Entry points grouped by the production source they exercise.
void test_socks5(void); // invizible.c
void test_native_initialization(void);
void test_jni_load_failures(void);
void test_tcp_destination(const struct arguments *args);
void test_udp_destination(const struct arguments *args);
void test_icmp_destination(const struct arguments *args);
void test_bounded_logging(void);
void test_lazy_logging(void);
void test_lazy_tcp_logging(const struct arguments *args);
void test_lazy_udp_logging(const struct arguments *args);
void test_packet_buffers(const struct arguments *args);
void test_tun_buffer_reuse(const struct arguments *args);
void test_tcp_buffer_reuse(const struct arguments *args);
extern long long tcp_test_clock;
void test_tcp_backpressure(const struct arguments *args, int kind);
void test_tcp_window_reopen(const struct arguments *args, int version);
void test_tcp_probe_deadlines(struct arguments *args);
void test_udp_buffer_reuse(const struct arguments *args);
void test_icmp_buffer_reuse(const struct arguments *args);
void test_event_buffer_cleanup(struct arguments *args);
void test_exception_failures(void);
void test_atomic_flags(void);
void test_tcp_allocation_failure(const struct arguments *args);
void test_tcp_queue_limits(const struct arguments *args, int kind);
void test_tcp_gap_recovery(const struct arguments *args, int kind);
void test_tcp_partial_retransmission(const struct arguments *args);
void test_jni_allocation_failures(int kind);
void test_udp_allocation_failure(const struct arguments *args);
void test_icmp_allocation_failure(const struct arguments *args);
void test_dns_allocation_failure(const struct arguments *args);
void test_dhcp_allocation_failure(const struct arguments *args);
void test_tun_allocation_failure(const struct arguments *args);
void test_names(void); // dns.c
void test_dns(const struct arguments *args);
void test_dns_question_counts(void);
void test_dns_tcp_framing(void);
void test_udp_dns_counts(int version, int response);
void test_tcp_dns_counts(int version);
void test_tcp_dns_buffer(int kind);
void dns_flows(void);
void test_packets(struct arguments *args); // ip.c
void test_uid_file_cleanup(void);
void test_uid_cache_allocation_failure(void);
void test_uid_cache_refresh(void);
void test_uid_cache_limit(void);
void test_uid_cache_keys(void);
void test_udp_short_dns(struct arguments *args); // udp.c
void test_udp_socket_cleanup(const struct arguments *args);
void test_udp_blocked_limit(struct arguments *args, int version);
void test_udp_blocked_duplicates(struct arguments *args);
void udp_flow(int version, int redirect, int extension);
void test_tcp(const struct arguments *args); // tcp.c
void test_tcp_socket_cleanup(const struct arguments *args);
void test_tcp_connect_failure(int version, int transient);
void test_tcp_data_cleanup(void);
void test_tcp_closed_data_cleanup(const struct arguments *args);
void test_tcp_half_close(int kind);
void tcp_flow(int version);
void socks_flow(int version, int authentication, int max_credentials);
void test_icmp_socket_cleanup(const struct arguments *args); // icmp.c
void icmp_packets(int version);
void test_dhcp_response_bounds(const struct arguments *args); // dhcp.c
void dhcp_offer(void);
void test_event_loop_shutdown(struct arguments *args); // session.c
void test_closed_session_limit(struct arguments *args, int kind);
void test_event_loop_idle_cleanup(struct arguments *args, int kind);
void compatibility_suite(void); // Multiple sources; valid-input comparison only.

#endif
