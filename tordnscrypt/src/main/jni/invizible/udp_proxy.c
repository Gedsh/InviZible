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

/* External SOCKS5 UDP associations. All state belongs to the VPN event thread. */
#include "invizible.h"
#include <linux/errqueue.h>

extern int proxy_socks5_port;
extern char proxy_socks5_addr[], proxy_socks5_username[], proxy_socks5_password[];

enum udp_proxy_stage { PROXY_CONNECT, PROXY_SEND_HELLO, PROXY_HELLO,
    PROXY_SEND_AUTH, PROXY_AUTH, PROXY_SEND_ASSOCIATE, PROXY_ASSOCIATE, PROXY_READY };

#define PROXY_PENDING_PACKETS 4
#define PROXY_PENDING_BYTES (64u * 1024u)
#define PROXY_PRESSURE_IDLE 60 // seconds; only ready associations at capacity.

struct udp_proxy {
    // An unindexed event endpoint, retained until its owner is freed so queued
    // events cannot point at freed memory. It never owns a UDP session itself.
    struct ng_session control;
    struct ng_session *owner;
    struct context *ctx;
    int epoll_fd, family;
    enum udp_proxy_stage stage;
    bool counted;
    size_t used, wanted;
    uint8_t handshake[262];
    struct sockaddr_storage peer;
    struct {
        uint8_t *data;
        size_t length;
    } pending[PROXY_PENDING_PACKETS];
    size_t pending_count, pending_bytes;
};

bool udp_proxy_ready(const struct udp_session *s) {
    return s->proxy->stage == PROXY_READY;
}

static int protected_socket(const struct arguments *args, int family, int type, int uid) {
    int fd = socket(family, type | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (fd >= 0 && protect_socket(args, fd, uid) < 0) {
        close(fd);
        errno = EACCES; // protect_socket reports JNI/policy failure, not a socket errno.
        return -1;
    }
    return fd;
}

void close_udp_proxy(struct ng_session *s) {
    struct udp_proxy *p = s->udp.proxy;
    if (p == NULL) return;
    if (s->socket >= 0) {
        close(s->socket);
        s->socket = -1;
    }
    if (p->control.socket >= 0) {
        close(p->control.socket);
        p->control.socket = -1;
    }
    for (size_t i = 0; i < p->pending_count; i++) {
        if (p->pending[i].data != NULL) {
            ng_free(p->pending[i].data, __FILE__, __LINE__);
            p->pending[i].data = NULL;
        }
    }
    p->pending_count = p->pending_bytes = 0;
    if (p->counted) {
        p->ctx->udp_proxy_count--;
        p->counted = false;
    }
}

void free_udp_proxy(struct ng_session *s) {
    if (s->udp.proxy != NULL) {
        close_udp_proxy(s);
        ng_free(s->udp.proxy, __FILE__, __LINE__);
        s->udp.proxy = NULL;
    }
}

static void log_failure(int uid, const char *reason, int error) {
    if (error != 0)
        log_android(ANDROID_LOG_ERROR, "UDP SOCKS5 uid %d: %s error %d: %s", uid, reason, error, strerror(error));
    else
        log_android(ANDROID_LOG_ERROR, "UDP SOCKS5 uid %d: %s", uid, reason);
}

static bool fail_proxy(const struct arguments *args, struct udp_proxy *p, const char *reason, int error) {
    if (p->owner->udp.state != UDP_ACTIVE) return false;
    log_failure(p->owner->udp.uid, reason, error);
    write_connection_unreach(args, p->owner->udp.error_quote,
            p->owner->udp.error_quote_length,
            error == EACCES || error == EPERM || error == ECONNREFUSED ||
            error == ENETUNREACH || error == ENETDOWN ? error : EHOSTUNREACH);
    p->owner->udp.state = UDP_FINISHING;
    close_udp_proxy(p->owner);
    return false;
}

static bool fail_reply(const struct arguments *args, struct udp_proxy *p,
                       const char *phase, unsigned int code, const char *description, int error) {
    char reason[128];
    snprintf(reason, sizeof(reason), "%s 0x%02x: %s", phase, code, description);
    return fail_proxy(args, p, reason, error);
}

static int socket_error(int fd) {
    int error = 0;
    socklen_t length = sizeof(error);
    return getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &length) < 0 ? errno : error;
}

static int open_failure(struct ng_session *s, const char *reason, int error) {
    log_failure(s->udp.uid, reason, error);
    free_udp_proxy(s);
    errno = error; // Cleanup must not replace the cause returned to the caller.
    return -1;
}

static bool watch_control(struct udp_proxy *p, uint32_t events) {
    p->control.ev.events = events | EPOLLERR | EPOLLRDHUP;
    return epoll_ctl(p->epoll_fd, EPOLL_CTL_MOD, p->control.socket, &p->control.ev) == 0;
}

static void reclaim_capacity(const struct arguments *args) {
    time_t now = time(NULL);
    if (now == (time_t) -1) return;
    struct ng_session *oldest = NULL;
    for (struct ng_session *s = args->ctx->ng_session; s != NULL; s = s->next) {
        if (s->protocol != IPPROTO_UDP || s->udp.proxy == NULL ||
            !s->udp.proxy->counted || s->udp.state != UDP_ACTIVE ||
            s->udp.time == (time_t) -1 || now < s->udp.time) continue;
        // Subtract unsigned values to avoid overflowing time_t on 32-bit devices.
        uintmax_t idle = (uintmax_t) now - (uintmax_t) s->udp.time;
        // Use the ordinary unscaled timeout here; global pressure cleanup stays
        // in the session monitor. Failed/backward clock reads cannot evict flows.
        if (idle > (unsigned int) get_udp_timeout(&s->udp, 0, 1)) {
            s->udp.state = UDP_FINISHING;
            close_udp_proxy(s);
            unindex_session(args->ctx, s);
        } else if (udp_proxy_ready(&s->udp) && idle >= PROXY_PRESSURE_IDLE &&
                   (oldest == NULL || s->udp.time < oldest->udp.time)) {
            oldest = s;
        }
    }
    if (args->ctx->udp_proxy_count >= UDP_PROXY_MAX && oldest != NULL) {
        oldest->udp.state = UDP_FINISHING;
        close_udp_proxy(oldest);
        unindex_session(args->ctx, oldest);
    }
    // Retain records until normal cleanup: epoll may already hold their events.
    // Unindex them so later traffic can negotiate a fresh association immediately.
}

int open_udp_proxy(const struct arguments *args, struct ng_session *s, int epoll_fd) {
    if (args->ctx->udp_proxy_count >= UDP_PROXY_MAX) reclaim_capacity(args);
    if (args->ctx->udp_proxy_count >= UDP_PROXY_MAX) {
        int64_t now = get_ms();
        if (now >= 0 && now >= args->ctx->udp_proxy_limit_log_at) {
            log_android(ANDROID_LOG_ERROR, "UDP SOCKS5 association limit reached (%u/%d); dropping new flows",
                        args->ctx->udp_proxy_count, UDP_PROXY_MAX);
            args->ctx->udp_proxy_limit_log_at = now + 1000;
        }
        errno = ENOBUFS;
        return -1;
    }
    struct udp_proxy *p = ng_calloc(1, sizeof(*p), "udp proxy");
    if (p == NULL) return open_failure(s, "cannot allocate association", ENOMEM);
    p->owner = s; p->ctx = args->ctx; p->epoll_fd = epoll_fd;
    p->family = strchr(proxy_socks5_addr, ':') == NULL ? AF_INET : AF_INET6;
    p->control.protocol = IPPROTO_UDP;
    p->control.udp.proxy = p;
    p->control.udp.state = UDP_ACTIVE;
    p->control.socket = -1;
    s->udp.proxy = p;
    int valid;
    socklen_t length;
    if (p->family == AF_INET) {
        struct sockaddr_in *address = (void *) &p->peer;
        address->sin_family = AF_INET; address->sin_port = htons((uint16_t) proxy_socks5_port);
        valid = inet_pton(AF_INET, proxy_socks5_addr, &address->sin_addr);
        length = sizeof(*address);
    } else {
        struct sockaddr_in6 *address = (void *) &p->peer;
        address->sin6_family = AF_INET6; address->sin6_port = htons((uint16_t) proxy_socks5_port);
        valid = inet_pton(AF_INET6, proxy_socks5_addr, &address->sin6_addr);
        length = sizeof(*address);
    }
    if (valid != 1) return open_failure(s, "invalid proxy address", EINVAL);
    s->socket = protected_socket(args, p->family, SOCK_DGRAM, s->udp.uid);
    if (s->socket < 0) return open_failure(s, "open protected relay socket", errno);
    p->control.socket = protected_socket(args, p->family, SOCK_STREAM, s->udp.uid);
    if (p->control.socket < 0) return open_failure(s, "open protected control socket", errno);
    int result = connect(p->control.socket, (struct sockaddr *) &p->peer, length);
    p->control.ev.events = EPOLLOUT | EPOLLERR | EPOLLRDHUP;
    p->control.ev.data.ptr = &p->control;
    if (result < 0 && errno != EINPROGRESS) return open_failure(s, "control connect", errno);
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, p->control.socket, &p->control.ev) < 0)
        return open_failure(s, "monitor control socket", errno);
    p->counted = true; args->ctx->udp_proxy_count++;
    return s->socket;
}

static size_t udp_header(const struct udp_session *s, uint8_t *header) {
    memset(header, 0, 4);
    header[3] = s->version == 4 ? 1 : 4;
    size_t address = s->version == 4 ? 4 : 16;
    memcpy(header + 4, &s->daddr, address);
    memcpy(header + 4 + address, &s->dest, 2);
    return 6 + address;
}

static void relay_mtu(const struct arguments *args, struct udp_proxy *p, uint32_t mtu,
                      const uint8_t *payload, size_t length) {
    size_t header = (p->family == AF_INET ? 20u : 40u) + 8u +
                    (p->owner->udp.version == 4 ? 10u : 22u);
    if (mtu <= header) return;
    uint32_t limit = mtu - (uint32_t) header;
    uint32_t transport_limit = (p->family == AF_INET ? UDP4_MAXMSG : UDP6_MAXMSG) -
                               (p->owner->udp.version == 4 ? 10u : 22u);
    if (limit > transport_limit) limit = transport_limit;
    write_udp_mtu(args, p->owner->udp.error_quote, p->owner->udp.error_quote_length,
                  payload, length, limit);
}

// A non-NULL payload identifies a synchronous failed send. Other callers can
// reflect queued MTU errors only when their SOCKS framing and payload match.
static bool relay_errors(const struct arguments *args, struct udp_proxy *p,
                         const uint8_t *payload, size_t length) {
    uint32_t mtu = 0;
    int version = p->family == AF_INET ? 4 : 6;
    uint8_t expected[22]; size_t header = udp_header(&p->owner->udp, expected);
    for (int i = 0; i < 8; i++) {
        union {
            struct cmsghdr alignment;
            uint8_t bytes[CMSG_SPACE(sizeof(struct sock_extended_err) + sizeof(struct sockaddr_in6))];
        } control;
        uint8_t data[134]; // SOCKS header plus a bounded prefix of the original payload.
        struct iovec iov = {.iov_base = data, .iov_len = sizeof(data)};
        struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1,
                .msg_control = control.bytes, .msg_controllen = sizeof(control.bytes)};
        ssize_t bytes = recvmsg(p->owner->socket, &message, MSG_ERRQUEUE | MSG_DONTWAIT);
        if (bytes < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (message.msg_flags & MSG_CTRUNC) continue;
        for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message); cmsg != NULL; cmsg = CMSG_NXTHDR(&message, cmsg)) {
            size_t available = message.msg_controllen - (size_t) ((uint8_t *) cmsg - control.bytes);
            if (cmsg->cmsg_level != (version == 4 ? IPPROTO_IP : IPPROTO_IPV6) ||
                cmsg->cmsg_type != (version == 4 ? IP_RECVERR : IPV6_RECVERR) ||
                cmsg->cmsg_len < CMSG_LEN(sizeof(struct sock_extended_err)) || cmsg->cmsg_len > available) continue;
            struct sock_extended_err error; memcpy(&error, CMSG_DATA(cmsg), sizeof(error));
            if (error.ee_origin != SO_EE_ORIGIN_LOCAL &&
                error.ee_origin != (version == 4 ? SO_EE_ORIGIN_ICMP : SO_EE_ORIGIN_ICMP6)) continue;
            if (error.ee_errno == 0) continue;
            if (error.ee_errno != EMSGSIZE) return fail_proxy(args, p, "relay socket", (int) error.ee_errno);
            if (payload != NULL) {
                if (error.ee_info != 0 && (mtu == 0 || error.ee_info < mtu)) mtu = error.ee_info;
            } else if ((size_t) bytes > header && memcmp(data, expected, header) == 0) {
                relay_mtu(args, p, error.ee_info, data + header, (size_t) bytes - header);
            }
        }
    }
    int error = socket_error(p->owner->socket);
    if (error != 0 && error != EMSGSIZE) return fail_proxy(args, p, "relay socket", error);
    if (payload != NULL) {
        if (mtu == 0) {
            int value = 0; socklen_t size = sizeof(value);
            if (getsockopt(p->owner->socket, version == 4 ? IPPROTO_IP : IPPROTO_IPV6,
                           version == 4 ? IP_MTU : IPV6_MTU, &value, &size) == 0 && value > 0)
                mtu = (uint32_t) value;
        }
        relay_mtu(args, p, mtu, payload, length);
    }
    return true;
}

int send_udp_proxy(const struct arguments *args, struct ng_session *s, const uint8_t *data, size_t length) {
    struct udp_proxy *p = s->udp.proxy;
    size_t overhead = s->udp.version == 4 ? 10 : 22;
    size_t wire_max = p->family == AF_INET ? UDP4_MAXMSG : UDP6_MAXMSG;
    if (length > wire_max - overhead || length > (s->udp.version == 4 ? UDP4_MAXMSG : UDP6_MAXMSG)) {
        if (p->stage == PROXY_READY)
            write_udp_mtu(args, s->udp.error_quote, s->udp.error_quote_length, data, length,
                          (uint32_t) (wire_max - overhead));
        return 0;
    }
    if (p->stage != PROXY_READY) {
        if (p->pending_count == PROXY_PENDING_PACKETS || length > PROXY_PENDING_BYTES - p->pending_bytes)
            return 0; // Keep the oldest datagrams in order; discard new overflow.
        uint8_t *copy = ng_malloc(length ? length : 1, "udp proxy pending");
        if (copy == NULL) { fail_proxy(args, p, "cannot buffer handshake datagram", ENOMEM); return 0; }
        if (length != 0) memcpy(copy, data, length);
        p->pending[p->pending_count].data = copy;
        p->pending[p->pending_count++].length = length;
        p->pending_bytes += length;
        return 1;
    }
    uint8_t header[22]; size_t header_length = udp_header(&s->udp, header);
    struct iovec iov[2] = {{.iov_base = header, .iov_len = header_length},
                          {.iov_base = (void *) data, .iov_len = length}};
    struct msghdr message = {.msg_iov = iov, .msg_iovlen = 2};
    ssize_t sent = sendmsg(s->socket, &message, MSG_NOSIGNAL);
    if (sent == (ssize_t) (header_length + length)) { s->udp.sent += length; return 1; }
    if (sent < 0 && errno == EMSGSIZE) { relay_errors(args, p, data, length); return 0; }
    if (sent < 0 && (errno == EAGAIN || errno == EINTR)) return 0;
    fail_proxy(args, p, "relay send", sent < 0 ? errno : EIO);
    return 0;
}

static bool finish_associate(const struct arguments *args, struct udp_proxy *p) {
    uint8_t *b = p->handshake;
    // Version, reply code and reserved byte were checked in the four-byte prefix.
    struct sockaddr_storage relay = {0}; socklen_t length; int family;
    if (b[3] == 1) {
        struct sockaddr_in *a = (void *) &relay;
        a->sin_family = family = AF_INET;
        memcpy(&a->sin_addr, b + 4, 4); memcpy(&a->sin_port, b + 8, 2);
        if (a->sin_port == 0) return fail_proxy(args, p, "zero relay port", EPROTO);
        if (a->sin_addr.s_addr == 0 && p->family == AF_INET)
            a->sin_addr = ((struct sockaddr_in *) &p->peer)->sin_addr;
        if (a->sin_addr.s_addr == 0) return fail_proxy(args, p, "unusable wildcard relay address", EPROTO);
        length = sizeof(*a);
    } else if (b[3] == 4) {
        struct sockaddr_in6 *a = (void *) &relay;
        a->sin6_family = family = AF_INET6;
        memcpy(&a->sin6_addr, b + 4, 16); memcpy(&a->sin6_port, b + 20, 2);
        if (a->sin6_port == 0) return fail_proxy(args, p, "zero relay port", EPROTO);
        if (IN6_IS_ADDR_UNSPECIFIED(&a->sin6_addr) && p->family == AF_INET6)
            a->sin6_addr = ((struct sockaddr_in6 *) &p->peer)->sin6_addr;
        if (IN6_IS_ADDR_UNSPECIFIED(&a->sin6_addr))
            return fail_proxy(args, p, "unusable wildcard relay address", EPROTO);
        length = sizeof(*a);
        if (IN6_IS_ADDR_V4MAPPED(&a->sin6_addr)) {
            // A mapped endpoint uses IPv4 on the wire, including for MTU/error accounting.
            struct sockaddr_in mapped = {.sin_family = AF_INET, .sin_port = a->sin6_port};
            memcpy(&mapped.sin_addr, &a->sin6_addr.s6_addr[12], 4);
            memset(&relay, 0, sizeof(relay)); memcpy(&relay, &mapped, sizeof(mapped));
            family = AF_INET; length = sizeof(mapped);
        }
    } else return fail_proxy(args, p, "unsupported relay address type", EAFNOSUPPORT);
    if (family != p->family) {
        int fd = protected_socket(args, family, SOCK_DGRAM, p->owner->udp.uid);
        if (fd < 0) return fail_proxy(args, p, "open protected relay socket", errno);
        close(p->owner->socket); p->owner->socket = fd; p->family = family;
    }
    // Connected UDP rejects packets from anyone except the negotiated relay.
    enable_socket_errors(p->owner->socket, family == AF_INET ? 4 : 6);
    if (connect(p->owner->socket, (struct sockaddr *) &relay, length) < 0)
        return fail_proxy(args, p, "relay connect", errno);
    struct ng_session *s = p->owner;
    s->ev.events = EPOLLIN | EPOLLERR; s->ev.data.ptr = s;
    if (epoll_ctl(p->epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev) < 0)
        return fail_proxy(args, p, "monitor relay socket", errno);
    p->stage = PROXY_READY; s->udp.time = time(NULL);
    if (!watch_control(p, EPOLLIN)) return fail_proxy(args, p, "monitor ready control socket", errno);
    for (size_t i = 0; i < p->pending_count && s->udp.state == UDP_ACTIVE; i++) {
        uint8_t *data = p->pending[i].data; size_t size = p->pending[i].length;
        // Detach this entry: a fatal send closes the association and frees the rest.
        p->pending[i].data = NULL;
        send_udp_proxy(args, s, data, size);
        ng_free(data, __FILE__, __LINE__);
    }
    p->pending_count = p->pending_bytes = 0;
    return s->udp.state == UDP_ACTIVE;
}

static bool next_handshake(const struct arguments *args, struct udp_proxy *p, int error) {
    uint8_t *b = p->handshake;
    if (p->stage == PROXY_HELLO) {
        bool auth = *proxy_socks5_username || *proxy_socks5_password;
        if (b[0] != 5) return fail_proxy(args, p, "invalid method reply version", EPROTO);
        if (b[1] != (auth ? 2 : 0))
            return fail_reply(args, p, "authentication method", b[1],
                    b[1] == 0xff ? "no acceptable authentication method" : "method was not offered",
                    b[1] == 0xff ? EACCES : EPROTO);
        if (error != 0) return fail_proxy(args, p, "control socket", error);
        if (auth) {
            size_t ulen = strlen(proxy_socks5_username), plen = strlen(proxy_socks5_password);
            if (ulen == 0 || ulen > 127 || plen == 0 || plen > 127)
                return fail_proxy(args, p, "invalid username/password lengths", EINVAL);
            b[0] = 1; b[1] = (uint8_t) ulen;
            memcpy(b + 2, proxy_socks5_username, ulen); b[2 + ulen] = (uint8_t) plen;
            memcpy(b + 3 + ulen, proxy_socks5_password, plen);
            p->wanted = 3 + ulen + plen; p->stage = PROXY_SEND_AUTH;
        } else p->stage = PROXY_SEND_ASSOCIATE;
    } else if (p->stage == PROXY_AUTH) {
        if (b[0] != 1) return fail_proxy(args, p, "invalid authentication reply version", EPROTO);
        if (b[1] != 0)
            return fail_reply(args, p, "authentication status", b[1], "username/password rejected", EACCES);
        if (error != 0) return fail_proxy(args, p, "control socket", error);
        p->stage = PROXY_SEND_ASSOCIATE;
    } else if (p->stage == PROXY_ASSOCIATE) {
        if (error != 0) return fail_proxy(args, p, "control socket", error);
        return finish_associate(args, p);
    }
    if (p->stage == PROXY_SEND_ASSOCIATE) {
        memset(b, 0, 10); b[0] = 5; b[1] = 3; b[3] = 1;
        p->wanted = 10; // Unknown external UDP tuple: RFC 1928 all-zero address/port.
    }
    p->used = 0;
    return watch_control(p, EPOLLOUT) || fail_proxy(args, p, "monitor control request", errno);
}

static void control_event(const struct arguments *args, struct udp_proxy *p, uint32_t events) {
    int error = 0;
    if (p->stage == PROXY_CONNECT || (events & EPOLLERR)) {
        error = socket_error(p->control.socket);
        bool reading = p->stage == PROXY_HELLO || p->stage == PROXY_AUTH || p->stage == PROXY_ASSOCIATE;
        if (error != 0 && (!reading || !(events & EPOLLIN))) {
            fail_proxy(args, p, "control socket", error); return;
        }
    }
    // A proxy may send a rejection and immediately close. Read its reply first.
    if ((events & (EPOLLHUP | EPOLLRDHUP)) && !(events & EPOLLIN)) {
        fail_proxy(args, p, "control connection closed", 0); return;
    }
    if (p->stage == PROXY_READY) {
        if (events & EPOLLIN) {
            uint8_t byte;
            ssize_t count = recv(p->control.socket, &byte, 1, 0);
            if (count == 0) fail_proxy(args, p, "control connection closed", 0);
            else if (count > 0) fail_proxy(args, p, "unexpected control data", EPROTO);
            else if (errno != EAGAIN && errno != EINTR) fail_proxy(args, p, "control receive", errno);
        }
        return;
    }
    if (p->stage == PROXY_CONNECT) {
        p->stage = PROXY_SEND_HELLO; p->used = 0; p->wanted = 3;
        p->handshake[0] = 5; p->handshake[1] = 1;
        p->handshake[2] = *proxy_socks5_username || *proxy_socks5_password ? 2 : 0;
    }
    bool sending = p->stage == PROXY_SEND_HELLO || p->stage == PROXY_SEND_AUTH || p->stage == PROXY_SEND_ASSOCIATE;
    ssize_t bytes = sending
            ? send(p->control.socket, p->handshake + p->used, p->wanted - p->used, MSG_NOSIGNAL)
            : recv(p->control.socket, p->handshake + p->used, p->wanted - p->used, 0);
    // SO_ERROR clears the pending error. Drain only the current reply on this
    // terminal path, retaining its cause if the buffered bytes are incomplete.
    // Positive reads bound this loop by the fixed handshake buffer size.
    while (error != 0 && bytes > 0 && p->used + (size_t) bytes < p->wanted) {
        p->used += (size_t) bytes;
        bytes = recv(p->control.socket, p->handshake + p->used, p->wanted - p->used, 0);
    }
    if (error != 0 && bytes <= 0) { fail_proxy(args, p, "control socket", error); return; }
    if (bytes < 0 && (errno == EAGAIN || errno == EINTR)) return;
    if (bytes <= 0) {
        fail_proxy(args, p, bytes == 0 ? "control connection closed during handshake" :
                   sending ? "control send" : "control receive", bytes < 0 ? errno : 0);
        return;
    }
    p->used += (size_t) bytes;
    if (p->used != p->wanted) return;
    if (sending) {
        p->stage = p->stage == PROXY_SEND_HELLO ? PROXY_HELLO :
                   p->stage == PROXY_SEND_AUTH ? PROXY_AUTH : PROXY_ASSOCIATE;
        p->used = 0; p->wanted = p->stage == PROXY_ASSOCIATE ? 4 : 2;
        if (!watch_control(p, EPOLLIN)) fail_proxy(args, p, "monitor control reply", errno);
    } else if (p->stage == PROXY_ASSOCIATE && p->wanted == 4) {
        if (p->handshake[0] != 5 || p->handshake[2] != 0) {
            fail_proxy(args, p, "invalid UDP ASSOCIATE reply header", EPROTO); return;
        }
        unsigned int code = p->handshake[1];
        if (code != 0) {
            static const char *const reasons[] = {"success", "general server failure",
                "not allowed by proxy rules", "network unreachable", "host unreachable",
                "connection refused", "TTL expired", "UDP ASSOCIATE unsupported", "address type unsupported"};
            static const int errors[] = {0, EIO, EACCES, ENETUNREACH, EHOSTUNREACH,
                ECONNREFUSED, ETIMEDOUT, EOPNOTSUPP, EAFNOSUPPORT};
            fail_reply(args, p, "UDP ASSOCIATE reply", code,
                       code < 9 ? reasons[code] : "unassigned reply code", code < 9 ? errors[code] : EPROTO);
            return;
        }
        if (error != 0) { fail_proxy(args, p, "control socket", error); return; }
        p->wanted = p->handshake[3] == 1 ? 10 : p->handshake[3] == 4 ? 22 : 0;
        if (p->wanted == 0)
            fail_reply(args, p, "relay address type", p->handshake[3], "numeric IPv4/IPv6 required", EAFNOSUPPORT);
    } else {
        next_handshake(args, p, error); // A buffered rejection takes precedence over reset.
    }
}

void check_udp_proxy(const struct arguments *args, struct ng_session *event_session,
                     const struct epoll_event *event) {
    struct udp_proxy *p = event_session->udp.proxy;
    struct ng_session *s = p->owner;
    if (s->udp.state != UDP_ACTIVE || s->socket < 0) { close_udp_proxy(s); return; }
    if (event_session == &p->control) { control_event(args, p, event->events); return; }
    if ((event->events & EPOLLERR) && !relay_errors(args, p, NULL, 0)) return;
    if (event->events & EPOLLHUP) { fail_proxy(args, p, "relay socket closed", 0); return; }
    if (!(event->events & EPOLLIN)) return;
    size_t max = p->family == AF_INET ? UDP4_MAXMSG : UDP6_MAXMSG;
    uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, max, "udp proxy recv");
    if (buffer == NULL) { fail_proxy(args, p, "cannot allocate receive buffer", ENOMEM); return; }
    // Bound all receive attempts, including malformed frames, so other flows get
    // a turn. Level-triggered epoll reports any remaining datagrams next time.
    for (int i = 0; i < 8; i++) {
        struct iovec iov = {.iov_base = buffer, .iov_len = max};
        struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1};
        ssize_t bytes = recvmsg(s->socket, &message, 0);
        if (bytes < 0) {
            if (errno == EMSGSIZE) relay_errors(args, p, NULL, 0);
            else if (errno != EAGAIN && errno != EINTR) fail_proxy(args, p, "relay receive", errno);
            break;
        }
        if (!(message.msg_flags & MSG_TRUNC) && bytes >= 4 &&
            buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0) {
            size_t address = buffer[3] == 1 ? 4 : buffer[3] == 4 ? 16 : 0;
            size_t header = 6 + address;
            if (address == (s->udp.version == 4 ? 4 : 16) && (size_t) bytes >= header &&
                (size_t) bytes - header <= (s->udp.version == 4 ? UDP4_MAXMSG : UDP6_MAXMSG) &&
                memcmp(buffer + 4, &s->udp.daddr, address) == 0 &&
                memcmp(buffer + 4 + address, &s->udp.dest, 2) == 0) {
                s->udp.time = time(NULL);
                forward_udp_reply(args, s, buffer + header, (size_t) bytes - header);
                if (s->udp.state != UDP_ACTIVE) { close_udp_proxy(s); break; }
            }
        }
    }
    release_packet_buffer(args, buffer);
}
