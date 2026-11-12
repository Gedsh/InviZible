#include "test_support.h"
#include <stdarg.h>

int allocations, sockets, last_socket = -1, udp_calls, resolved_calls;
int tcp_calls, icmp_calls;
int string_refs, string_calls, fail_string_call, exceptions, file_closes;
int capture_packets, capture_dhcp, fake_proc;
const char *proc_contents;
int fail_cache_alloc;
int uid_cache_allocations;
const char *fail_alloc_tag;
int fail_alloc_match, fail_fcntl_call, fcntl_calls;
char last_log[2048];
int address_formats;
int tcp_sendbuf_queries, tcp_outq_queries;

int __real_getsockopt(int fd, int level, int option, void *value, socklen_t *length);
int __wrap_getsockopt(int fd, int level, int option, void *value, socklen_t *length) {
    if (level == SOL_SOCKET && option == SO_SNDBUF)
        tcp_sendbuf_queries++;
    return __real_getsockopt(fd, level, option, value, length);
}

int __real_ioctl(int fd, int request, ...);
int __wrap_ioctl(int fd, int request, ...) {
    va_list ap;
    va_start(ap, request);
    void *value = va_arg(ap, void *);
    va_end(ap);
    if (request == SIOCOUTQ)
        tcp_outq_queries++;
    return __real_ioctl(fd, request, value);
}
int udp_lookups, uid_lookups, policy_checks, deny_address;
int udp_lookup_supplied;
struct ng_session *udp_lookup_session;

#ifndef INVIZIBLE_BASELINE_TESTS
struct ng_session *__real_find_udp_session(const struct arguments *, const uint8_t *, const uint8_t *);
struct ng_session *__wrap_find_udp_session(const struct arguments *args, const uint8_t *pkt,
                                           const uint8_t *payload) {
    udp_lookups++;
    return __real_find_udp_session(args, pkt, payload);
}

jboolean __real_handle_udp_with_lookup(const struct arguments *, const uint8_t *, size_t,
        const uint8_t *, int, struct allowed *, int, const struct udp_session_lookup *);
jboolean __wrap_handle_udp_with_lookup(const struct arguments *args, const uint8_t *pkt,
        size_t length, const uint8_t *payload, int uid, struct allowed *redirect, int epoll_fd,
        const struct udp_session_lookup *lookup) {
    udp_lookup_supplied = lookup != NULL;
    udp_lookup_session = lookup == NULL ? NULL : lookup->session;
    if (capture_packets) {
        assert(payload + sizeof(struct udphdr) <= pkt + length);
        udp_calls++;
        return 1;
    }
    return __real_handle_udp_with_lookup(args, pkt, length, payload, uid, redirect, epoll_fd, lookup);
}
#endif

const char *__real_inet_ntop(int family, const void *address, char *text, socklen_t size);
const char *__wrap_inet_ntop(int family, const void *address, char *text, socklen_t size) {
    address_formats++;
    return __real_inet_ntop(family, address, text, size);
}
int real_exit_reports, exit_reports;
int real_network, real_udp_packets, block_domain, trace_dns;
uint64_t accounted_sent, accounted_received;
enum failure failure;
static struct sockaddr_storage expected_destination;
static socklen_t destination_length;
int destination_calls;
int connect_errno, connect_fd = -1;

void expect_destination(int version, const char *address, uint16_t port) {
    memset(&expected_destination, 0, sizeof(expected_destination));
    if (version == 4) {
        struct sockaddr_in *addr = (void *) &expected_destination;
        addr->sin_family = AF_INET;
        addr->sin_port = htons(port);
        assert(inet_pton(AF_INET, address, &addr->sin_addr) == 1);
        destination_length = sizeof(*addr);
    } else {
        struct sockaddr_in6 *addr = (void *) &expected_destination;
        addr->sin6_family = AF_INET6;
        addr->sin6_port = htons(port);
        assert(inet_pton(AF_INET6, address, &addr->sin6_addr) == 1);
        destination_length = sizeof(*addr);
    }
    destination_calls = 0;
}

static void check_destination(const struct sockaddr *addr, socklen_t len) {
    assert(len == destination_length);
    assert(memcmp(addr, &expected_destination, len) == 0);
    destination_calls++;
}

void *__wrap_ng_malloc(size_t n, const char *tag) {
    if (strncmp(tag, "uid_cache", 9) == 0)
        uid_cache_allocations++;
    if (fail_alloc_tag != NULL && strcmp(tag, fail_alloc_tag) == 0 && --fail_alloc_match == 0)
        return NULL;
    if (fail_cache_alloc && strncmp(tag, "uid_cache", 9) == 0)
        return NULL;
    void *p = malloc(n);
    assert(p != NULL);
    allocations++;
    return p;
}

void *__real_ng_realloc(void *p, size_t n, const char *tag);
void *__wrap_ng_realloc(void *p, size_t n, const char *tag) {
    if (strncmp(tag, "uid_cache", 9) == 0)
        uid_cache_allocations++;
    if (fail_alloc_tag != NULL && strcmp(tag, fail_alloc_tag) == 0 && --fail_alloc_match == 0)
        return NULL;
    if (fail_cache_alloc && strncmp(tag, "uid_cache", 9) == 0)
        return NULL;
    void *resized = __real_ng_realloc(p, n, tag);
    assert(resized != NULL);
    if (p == NULL)
        allocations++;
    return resized;
}

void *__wrap_ng_calloc(size_t n, size_t size, const char *tag) {
    if (fail_alloc_tag != NULL && strcmp(tag, fail_alloc_tag) == 0 && --fail_alloc_match == 0)
        return NULL;
    void *p = calloc(n, size);
    assert(p != NULL);
    allocations++;
    return p;
}

void __wrap_ng_free(void *p, const char *file, int line) {
    if (p != NULL)
        allocations--;
    free(p);
}

int __real_socket(int domain, int type, int protocol);
int __wrap_socket(int domain, int type, int protocol) {
    assert(last_socket == -1);
    last_socket = (real_network ? __real_socket(domain, type, protocol) : open("/dev/null", O_RDWR));
    assert(last_socket >= 0);
    sockets++;
    return last_socket;
}

int __real_close(int fd);
int __wrap_close(int fd) {
    if (fd == last_socket) {
        last_socket = -1;
        sockets--;
    }
    return __real_close(fd);
}

int __real_fcntl(int fd, int cmd, ...);
int __wrap_fcntl(int fd, int cmd, ...) {
    if (++fcntl_calls == fail_fcntl_call || (cmd == F_GETFL && failure == GETFL) ||
        (cmd == F_SETFL && failure == SETFL)) {
        errno = EIO;
        return -1;
    }
    if (real_network) {
        if (cmd == F_GETFL)
            return __real_fcntl(fd, cmd);
        va_list ap;
        va_start(ap, cmd);
        int flags = va_arg(ap, int);
        va_end(ap);
        return __real_fcntl(fd, cmd, flags);
    }
    return 0;
}

int __real_pipe(int descriptors[2]);
int __wrap_pipe(int descriptors[2]) {
    if (failure == PIPE) {
        errno = EMFILE;
        return -1;
    }
    return __real_pipe(descriptors);
}

int __real_pthread_mutex_init(pthread_mutex_t *mutex, const pthread_mutexattr_t *attributes);
int __wrap_pthread_mutex_init(pthread_mutex_t *mutex, const pthread_mutexattr_t *attributes) {
    if (failure == MUTEX)
        return EAGAIN;
    return __real_pthread_mutex_init(mutex, attributes);
}

int __real_connect(int fd, const struct sockaddr *addr, socklen_t len);
int __wrap_connect(int fd, const struct sockaddr *addr, socklen_t len) {
    connect_fd = fd;
    if (destination_length)
        check_destination(addr, len);
    if (real_network)
        return __real_connect(fd, addr, len);
    if (connect_errno) {
        errno = connect_errno;
        return -1;
    }
    errno = (failure == CONNECT ? ECONNREFUSED : EINPROGRESS);
    return -1;
}

ssize_t __real_sendto(int, const void *, size_t, int, const struct sockaddr *, socklen_t);
ssize_t __wrap_sendto(int fd, const void *buffer, size_t length, int flags,
                      const struct sockaddr *addr, socklen_t addrlen) {
    if (destination_length) {
        check_destination(addr, addrlen);
        return (ssize_t) length;
    }
    return __real_sendto(fd, buffer, length, flags, addr, addrlen);
}

int __wrap_protect_socket(const struct arguments *args, int fd, int uid) {
    return (failure == PROTECT ? -1 : 0);
}

int __real_shutdown(int fd, int how);
int __wrap_shutdown(int fd, int how) {
    if (failure == SHUTDOWN) {
        errno = EIO;
        return -1;
    }
    return __real_shutdown(fd, how);
}

ssize_t __real_write_udp(const struct arguments *, const struct udp_session *, uint8_t *, size_t);
ssize_t __wrap_write_udp(const struct arguments *args, const struct udp_session *s,
                         uint8_t *data, size_t len) {
    if (real_udp_packets)
        return __real_write_udp(args, s, data, len);
    if (capture_dhcp) {
        const uint8_t lease[] = {0, 0, 14, 16};
        assert(len == 500);
        assert(data[sizeof(dhcp_packet)] == 53);
        assert(data[sizeof(dhcp_packet) + 15] == 51);
        assert(memcmp(data + sizeof(dhcp_packet) + 17, lease, sizeof(lease)) == 0);
        capture_dhcp = 0;
    }
    return (ssize_t) len;
}

void __wrap_dns_resolved(const struct arguments *args, const char *qname,
                         const char *aname, const char *cname, const char *hinfo,
                         const char *resource, int rcode) {
    assert(strlen(qname) <= DNS_QNAME_MAX);
    assert(strlen(aname) <= DNS_QNAME_MAX);
    assert(strlen(cname) <= DNS_QNAME_MAX);
    if (trace_dns)
        printf("DNS %s %s %s %s %s %d\n", qname, aname, cname, hinfo, resource, rcode);
    resolved_calls++;
}

jboolean __wrap_is_domain_blocked(const struct arguments *args, const char *name) {
    return block_domain;
}

jboolean __real_handle_udp(const struct arguments *, const uint8_t *, size_t,
                           const uint8_t *, int, struct allowed *, int);
jboolean __wrap_handle_udp(const struct arguments *args, const uint8_t *pkt, size_t len,
                           const uint8_t *payload, int uid, struct allowed *r, int epoll_fd) {
    if (capture_packets) {
        assert(payload + sizeof(struct udphdr) <= pkt + len);
        udp_calls++;
        return 1;
    }
    return __real_handle_udp(args, pkt, len, payload, uid, r, epoll_fd);
}

jint __wrap_get_uid_q(const struct arguments *args, jint version, jint protocol,
                      const char *source, jint sport, const char *dest, jint dport) {
    uid_lookups++;
    return own_uid;
}

jboolean __real_handle_tcp(const struct arguments *, const uint8_t *, size_t,
                           const uint8_t *, int, int, struct allowed *, int);
jboolean __wrap_handle_tcp(const struct arguments *args, const uint8_t *pkt, size_t len,
                           const uint8_t *payload, int uid, int allowed, struct allowed *r, int epoll_fd) {
    if (capture_packets) { tcp_calls++; return 1; }
    return __real_handle_tcp(args, pkt, len, payload, uid, allowed, r, epoll_fd);
}

jboolean __real_handle_icmp(const struct arguments *, const uint8_t *, size_t, const uint8_t *, int, int);
jboolean __wrap_handle_icmp(const struct arguments *args, const uint8_t *pkt, size_t len,
                            const uint8_t *payload, int uid, int epoll_fd) {
    if (capture_packets) { icmp_calls++; return 1; }
    return __real_handle_icmp(args, pkt, len, payload, uid, epoll_fd);
}

jobject __wrap_create_packet(const struct arguments *args, jint version, jint protocol,
                             const char *flags, const char *source, jint sport,
                             const char *dest, jint dport, const char *data, jint uid,
                             jboolean allowed) {
    return NULL;
}

struct allowed *__wrap_is_address_allowed(const struct arguments *args, jobject packet) {
    static struct allowed allowed = {0};
    policy_checks++;
    return deny_address ? NULL : &allowed;
}

void __real_report_exit(const struct arguments *args, const char *fmt, ...);
void __wrap_report_exit(const struct arguments *args, const char *fmt, ...) {
    exit_reports++;
    if (real_exit_reports) {
        char reason[1024];
        va_list ap;
        va_start(ap, fmt);
        if (fmt != NULL)
            vsnprintf(reason, sizeof(reason), fmt, ap);
        va_end(ap);
        __real_report_exit(args, fmt == NULL ? NULL : "%s", reason);
    }
}

void __wrap_account_usage(const struct arguments *args, jint version, jint protocol,
                           const char *daddr, jint dport, jint uid, jlong sent, jlong received) {
    accounted_sent += sent;
    accounted_received += received;
}

FILE *__real_fopen(const char *, const char *);
FILE *__wrap_fopen(const char *path, const char *mode) {
    static char proc[] = "header\ninvalid entry\n";
    const char *contents = (proc_contents == NULL ? proc : proc_contents);
    return (fake_proc ? fmemopen((void *) contents, strlen(contents), "r") : __real_fopen(path, mode));
}

int __real_fclose(FILE *file);
int __wrap_fclose(FILE *file) {
    file_closes++;
    return __real_fclose(file);
}

int __android_log_print(int prio, const char *tag, const char *fmt, ...) {
    va_list capture;
    va_start(capture, fmt);
    vsnprintf(last_log, sizeof(last_log), fmt, capture);
    va_end(capture);
    if (real_network) {
        va_list ap;
        va_start(ap, fmt);
        vfprintf(stderr, fmt, ap);
        va_end(ap);
        fputc('\n', stderr);
    }
    return 0;
}
int __system_property_get(const char *key, char *value) { *value = 0; return 0; }

// The x86 NEON emulation header has stricter alignment than ARM NEON loads.
// Keep checksum reads instrumented without depending on that host emulation.
uint16_t __wrap_calc_checksum(uint16_t start, const uint8_t *data, size_t len) {
    uint32_t sum = start;
    while (len >= 2) {
        sum += (uint16_t) ((uint16_t) data[1] << 8 | data[0]);
        data += 2;
        len -= 2;
    }
    if (len != 0)
        sum += *data;
    while (sum >> 16)
        sum = (sum & 0xFFFF) + (sum >> 16);
    return (uint16_t) sum;
}

static jsize JNICALL utf_length(JNIEnv *env, jstring value) {
    return (jsize) strlen((const char *) value);
}

static const char *JNICALL utf_chars(JNIEnv *env, jstring value, jboolean *copy) {
    if (++string_calls == fail_string_call)
        return NULL;
    string_refs++;
    return (const char *) value;
}

static void JNICALL release_chars(JNIEnv *env, jstring value, const char *chars) {
    string_refs--;
}

static jclass JNICALL find_class(JNIEnv *env, const char *name) { return (jclass) name; }
static jint JNICALL throw_new(JNIEnv *env, jclass cls, const char *message) {
    exceptions++;
    return 0;
}
static void JNICALL delete_ref(JNIEnv *env, jobject ref) {}

const struct JNINativeInterface jni = {
    .GetStringUTFLength = utf_length,
    .GetStringUTFChars = utf_chars,
    .ReleaseStringUTFChars = release_chars,
    .FindClass = find_class,
    .ThrowNew = throw_new,
    .DeleteLocalRef = delete_ref,
};

unsigned int cases;

uint32_t fingerprint(const void *data, size_t len) {
    const uint8_t *bytes = data;
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < len; i++)
        hash = (hash ^ bytes[i]) * 16777619u;
    return hash;
}

uint16_t load16(const uint8_t *p) {
    return (uint16_t) ((uint16_t) p[0] << 8 | p[1]);
}

void store16(uint8_t *p, uint16_t value) {
    p[0] = (uint8_t) (value >> 8);
    p[1] = (uint8_t) value;
}

void readable_at(int fd, int line) {
    struct pollfd pollfd = {.fd = fd, .events = POLLIN};
    int ready = poll(&pollfd, 1, 5000);
    if (ready != 1 || !(pollfd.revents & POLLIN))
        fprintf(stderr, "No input on descriptor %d at test line %d (poll=%d events=%d errno=%d)\n",
                fd, line, ready, pollfd.revents, errno);
    assert(ready == 1 && (pollfd.revents & POLLIN));
}

size_t tun_packet(int fd, uint8_t *packet, int version, int protocol) {
    readable(fd);
    ssize_t len = read(fd, packet, 2048);
    assert(len > 0 && packet[0] >> 4 == version);
    if (version == 4) {
        const struct iphdr *ip = (const void *) packet;
        assert(ntohs(ip->tot_len) == len && ip->protocol == protocol);
    } else {
        const struct ip6_hdr *ip = (const void *) packet;
        assert(ntohs(ip->ip6_plen) + 40 == len && ip->ip6_nxt == protocol);
    }
    return (size_t) len;
}

// Independent, byte-oriented network-order reference; do not reuse production SIMD code.
static uint32_t reference_sum(uint32_t sum, const uint8_t *data, size_t length) {
    while (length >= 2) {
        sum += (uint32_t) data[0] << 8 | data[1];
        data += 2; length -= 2;
    }
    if (length) sum += (uint32_t) data[0] << 8;
    while (sum >> 16) sum = (sum & 0xFFFF) + (sum >> 16);
    return sum;
}

void packet_checksums(uint8_t *packet, size_t length, size_t payload, int protocol) {
    int version = packet[0] >> 4;
    size_t offset = protocol == IPPROTO_UDP ? 6 : protocol == IPPROTO_TCP ? 16 : 2;
    assert(payload <= length && length - payload >= offset + 2);
    store16(packet + payload + offset, 0);
    uint32_t sum = 0;
    size_t len = length - payload;
    if (protocol != IPPROTO_ICMP) {
        sum = reference_sum(0, packet + (version == 4 ? 12 : 8), version == 4 ? 8 : 32);
        sum += (uint32_t) (len >> 16) + (len & 0xFFFF) + protocol;
    }
    uint16_t checksum = (uint16_t) ~reference_sum(sum, packet + payload, len);
    if (protocol == IPPROTO_UDP && checksum == 0) checksum = UINT16_MAX;
    store16(packet + payload + offset, checksum);
    if (version == 4) {
        size_t header = (size_t) (packet[0] & 15) * 4;
        assert(header >= 20 && header <= length);
        store16(packet + 10, 0);
        store16(packet + 10, (uint16_t) ~reference_sum(0, packet, header));
    }
}

size_t ip_header(uint8_t *packet, int version, int protocol, size_t data_len,
                         const char *destination) {
    size_t off = (version == 4 ? 20 : 40);
    memset(packet, 0, off + data_len);
    if (version == 4) {
        struct iphdr *ip = (void *) packet;
        ip->version = 4; ip->ihl = 5; ip->ttl = 64;
        ip->protocol = protocol; ip->tot_len = htons(off + data_len);
        assert(inet_pton(AF_INET, "10.0.0.2", &ip->saddr) == 1);
        assert(inet_pton(AF_INET, destination, &ip->daddr) == 1);
    } else {
        struct ip6_hdr *ip = (void *) packet;
        ip->ip6_vfc = 0x60; ip->ip6_plen = htons(data_len);
        ip->ip6_nxt = protocol; ip->ip6_hlim = 64;
        assert(inet_pton(AF_INET6, "2001:db8::1", &ip->ip6_src) == 1);
        assert(inet_pton(AF_INET6, destination, &ip->ip6_dst) == 1);
    }
    return off;
}

void reset_test_state(void) {
    tcp_sendbuf_queries = tcp_outq_queries = 0;
    udp_lookups = uid_lookups = policy_checks = deny_address = 0;
    udp_lookup_supplied = 0;
    udp_lookup_session = NULL;
    address_formats = 0;
    destination_length = destination_calls = 0;
    connect_errno = 0;
    connect_fd = -1;
    allocations = sockets = udp_calls = resolved_calls = 0;
    tcp_calls = icmp_calls = 0;
    string_refs = string_calls = fail_string_call = exceptions = file_closes = 0;
    capture_packets = capture_dhcp = fake_proc = 0;
    proc_contents = NULL;
    fail_cache_alloc = 0;
    uid_cache_allocations = 0;
    fail_alloc_tag = NULL;
    fail_alloc_match = fail_fcntl_call = fcntl_calls = 0;
    last_log[0] = 0;
    real_exit_reports = exit_reports = 0;
    real_network = real_udp_packets = block_domain = trace_dns = 0;
    accounted_sent = accounted_received = 0;
    failure = NONE;
    last_socket = -1;
    cases = 0;
    tor_socks5_addr[0] = tor_socks5_username[0] = tor_socks5_password[0] = 0;
    proxy_socks5_addr[0] = proxy_socks5_username[0] = proxy_socks5_password[0] = 0;
    tor_socks5_port = proxy_socks5_port = tor_dns_port = 0;
    tor_isolate_uid = tor_connection_available = false;
    bypass_lan = true;
    own_uid = getuid();
    srand(1);
}
