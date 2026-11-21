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

extern int proxy_socks5_port;
extern char proxy_socks5_addr[], proxy_socks5_username[], proxy_socks5_password[];

enum udp_proxy_stage { PROXY_CONNECT, PROXY_SEND_HELLO, PROXY_HELLO,
    PROXY_SEND_AUTH, PROXY_AUTH, PROXY_SEND_ASSOCIATE, PROXY_ASSOCIATE, PROXY_READY };

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
    uint8_t *pending; // At most the first datagram; later handshake traffic is dropped.
    size_t pending_length;
};

bool udp_proxy_ready(const struct udp_session *s) {
    return s->proxy->stage == PROXY_READY;
}

static int protected_socket(const struct arguments *args, int family, int type, int uid) {
    int fd = socket(family, type | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
    if (fd >= 0 && protect_socket(args, fd, uid) < 0) {
        close(fd);
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
    if (p->pending != NULL) {
        ng_free(p->pending, __FILE__, __LINE__);
        p->pending = NULL;
    }
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

static void fail_proxy(const struct arguments *args, struct udp_proxy *p, const char *reason) {
    log_android(ANDROID_LOG_WARN, "UDP SOCKS5 uid %d: %s", p->owner->udp.uid, reason);
    write_connection_unreach(args, p->owner->udp.error_quote,
                              p->owner->udp.error_quote_length, EHOSTUNREACH);
    p->owner->udp.state = UDP_FINISHING;
    close_udp_proxy(p->owner);
}

static bool watch_control(struct udp_proxy *p, uint32_t events) {
    p->control.ev.events = events | EPOLLERR | EPOLLRDHUP;
    return epoll_ctl(p->epoll_fd, EPOLL_CTL_MOD, p->control.socket, &p->control.ev) == 0;
}

int open_udp_proxy(const struct arguments *args, struct ng_session *s, int epoll_fd) {
    if (args->ctx->udp_proxy_count >= UDP_PROXY_MAX) return -1;
    struct udp_proxy *p = ng_calloc(1, sizeof(*p), "udp proxy");
    if (p == NULL) return -1;
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
    if (valid == 1) {
        s->socket = protected_socket(args, p->family, SOCK_DGRAM, s->udp.uid);
        if (s->socket >= 0)
            p->control.socket = protected_socket(args, p->family, SOCK_STREAM, s->udp.uid);
    }
    if (s->socket < 0 || p->control.socket < 0) {
        free_udp_proxy(s);
        s->socket = -1;
        return -1;
    }
    int result = connect(p->control.socket, (struct sockaddr *) &p->peer, length);
    p->control.ev.events = EPOLLOUT | EPOLLERR | EPOLLRDHUP;
    p->control.ev.data.ptr = &p->control;
    if ((result < 0 && errno != EINPROGRESS) ||
        epoll_ctl(epoll_fd, EPOLL_CTL_ADD, p->control.socket, &p->control.ev) < 0) {
        free_udp_proxy(s);
        return -1;
    }
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

int send_udp_proxy(const struct arguments *args, struct ng_session *s, const uint8_t *data, size_t length) {
    struct udp_proxy *p = s->udp.proxy;
    size_t overhead = s->udp.version == 4 ? 10 : 22;
    size_t wire_max = p->family == AF_INET ? UDP4_MAXMSG : UDP6_MAXMSG;
    if (length > wire_max - overhead || length > (s->udp.version == 4 ? UDP4_MAXMSG : UDP6_MAXMSG))
        return 0;
    if (p->stage != PROXY_READY) {
        if (p->pending == NULL) {
            p->pending = ng_malloc(length ? length : 1, "udp proxy pending");
            if (p->pending == NULL) { fail_proxy(args, p, "cannot buffer first datagram"); return 0; }
            memcpy(p->pending, data, length); p->pending_length = length;
        }
        return 1;
    }
    uint8_t header[22]; size_t header_length = udp_header(&s->udp, header);
    struct iovec iov[2] = {{.iov_base = header, .iov_len = header_length},
                          {.iov_base = (void *) data, .iov_len = length}};
    struct msghdr message = {.msg_iov = iov, .msg_iovlen = 2};
    ssize_t sent = sendmsg(s->socket, &message, MSG_NOSIGNAL);
    if (sent == (ssize_t) (header_length + length)) { s->udp.sent += length; return 1; }
    if (sent < 0 && (errno == EAGAIN || errno == EINTR || errno == EMSGSIZE)) return 0;
    fail_proxy(args, p, "relay send failed");
    return 0;
}

static bool finish_associate(const struct arguments *args, struct udp_proxy *p) {
    uint8_t *b = p->handshake;
    if (b[0] != 5 || b[1] != 0 || b[2] != 0) return false;
    struct sockaddr_storage relay = {0}; socklen_t length; int family;
    if (b[3] == 1) {
        struct sockaddr_in *a = (void *) &relay;
        a->sin_family = family = AF_INET;
        memcpy(&a->sin_addr, b + 4, 4); memcpy(&a->sin_port, b + 8, 2);
        if (a->sin_port == 0) return false;
        if (a->sin_addr.s_addr == 0 && p->family == AF_INET)
            a->sin_addr = ((struct sockaddr_in *) &p->peer)->sin_addr;
        if (a->sin_addr.s_addr == 0) return false;
        length = sizeof(*a);
    } else if (b[3] == 4) {
        struct sockaddr_in6 *a = (void *) &relay;
        a->sin6_family = family = AF_INET6;
        memcpy(&a->sin6_addr, b + 4, 16); memcpy(&a->sin6_port, b + 20, 2);
        if (a->sin6_port == 0) return false;
        if (IN6_IS_ADDR_UNSPECIFIED(&a->sin6_addr) && p->family == AF_INET6)
            a->sin6_addr = ((struct sockaddr_in6 *) &p->peer)->sin6_addr;
        if (IN6_IS_ADDR_UNSPECIFIED(&a->sin6_addr)) return false;
        length = sizeof(*a);
    } else return false; // No blocking name resolution on the VPN event thread.
    if (family != p->family) {
        int fd = protected_socket(args, family, SOCK_DGRAM, p->owner->udp.uid);
        if (fd < 0) return false;
        close(p->owner->socket); p->owner->socket = fd; p->family = family;
    }
    // Connected UDP rejects packets from anyone except the negotiated relay.
    if (connect(p->owner->socket, (struct sockaddr *) &relay, length) < 0) return false;
    struct ng_session *s = p->owner;
    s->ev.events = EPOLLIN | EPOLLERR; s->ev.data.ptr = s;
    if (epoll_ctl(p->epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev) < 0) return false;
    p->stage = PROXY_READY; s->udp.time = time(NULL);
    if (!watch_control(p, EPOLLIN)) return false;
    if (p->pending != NULL) {
        uint8_t *data = p->pending; size_t size = p->pending_length;
        p->pending = NULL;
        send_udp_proxy(args, s, data, size);
        ng_free(data, __FILE__, __LINE__);
    }
    return s->udp.state == UDP_ACTIVE;
}

static bool next_handshake(const struct arguments *args, struct udp_proxy *p) {
    uint8_t *b = p->handshake;
    if (p->stage == PROXY_HELLO) {
        bool auth = *proxy_socks5_username || *proxy_socks5_password;
        if (b[0] != 5 || b[1] != (auth ? 2 : 0)) return false;
        if (auth) {
            size_t ulen = strlen(proxy_socks5_username), plen = strlen(proxy_socks5_password);
            if (ulen == 0 || ulen > 127 || plen == 0 || plen > 127) return false;
            b[0] = 1; b[1] = (uint8_t) ulen;
            memcpy(b + 2, proxy_socks5_username, ulen); b[2 + ulen] = (uint8_t) plen;
            memcpy(b + 3 + ulen, proxy_socks5_password, plen);
            p->wanted = 3 + ulen + plen; p->stage = PROXY_SEND_AUTH;
        } else p->stage = PROXY_SEND_ASSOCIATE;
    } else if (p->stage == PROXY_AUTH) {
        if (b[0] != 1 || b[1] != 0) return false;
        p->stage = PROXY_SEND_ASSOCIATE;
    } else if (p->stage == PROXY_ASSOCIATE) return finish_associate(args, p);
    if (p->stage == PROXY_SEND_ASSOCIATE) {
        memset(b, 0, 10); b[0] = 5; b[1] = 3; b[3] = 1;
        p->wanted = 10; // Unknown external UDP tuple: RFC 1928 all-zero address/port.
    }
    p->used = 0;
    return watch_control(p, EPOLLOUT);
}

static void control_event(const struct arguments *args, struct udp_proxy *p, uint32_t events) {
    if (events & (EPOLLERR | EPOLLHUP | EPOLLRDHUP)) { fail_proxy(args, p, "control connection closed"); return; }
    if (p->stage == PROXY_READY) {
        if (events & EPOLLIN) fail_proxy(args, p, "unexpected control data or EOF");
        return;
    }
    if (p->stage == PROXY_CONNECT) {
        int error = 0; socklen_t size = sizeof(error);
        if (getsockopt(p->control.socket, SOL_SOCKET, SO_ERROR, &error, &size) < 0 || error) {
            fail_proxy(args, p, "control connect failed"); return;
        }
        p->stage = PROXY_SEND_HELLO; p->used = 0; p->wanted = 3;
        p->handshake[0] = 5; p->handshake[1] = 1;
        p->handshake[2] = *proxy_socks5_username || *proxy_socks5_password ? 2 : 0;
    }
    bool sending = p->stage == PROXY_SEND_HELLO || p->stage == PROXY_SEND_AUTH || p->stage == PROXY_SEND_ASSOCIATE;
    ssize_t bytes = sending
            ? send(p->control.socket, p->handshake + p->used, p->wanted - p->used, MSG_NOSIGNAL)
            : recv(p->control.socket, p->handshake + p->used, p->wanted - p->used, 0);
    if (bytes < 0 && (errno == EAGAIN || errno == EINTR)) return;
    if (bytes <= 0) { fail_proxy(args, p, "control read/write failed"); return; }
    p->used += (size_t) bytes;
    if (p->used != p->wanted) return;
    if (sending) {
        p->stage = p->stage == PROXY_SEND_HELLO ? PROXY_HELLO :
                   p->stage == PROXY_SEND_AUTH ? PROXY_AUTH : PROXY_ASSOCIATE;
        p->used = 0; p->wanted = p->stage == PROXY_ASSOCIATE ? 4 : 2;
        if (!watch_control(p, EPOLLIN)) fail_proxy(args, p, "cannot monitor control reply");
    } else if (p->stage == PROXY_ASSOCIATE && p->wanted == 4) {
        p->wanted = p->handshake[3] == 1 ? 10 : p->handshake[3] == 4 ? 22 : 0;
        if (p->wanted == 0) fail_proxy(args, p, "relay must advertise an IPv4/IPv6 address");
    } else if (!next_handshake(args, p)) fail_proxy(args, p, "proxy refused or malformed handshake");
}

void check_udp_proxy(const struct arguments *args, struct ng_session *event_session,
                     const struct epoll_event *event) {
    struct udp_proxy *p = event_session->udp.proxy;
    struct ng_session *s = p->owner;
    if (s->udp.state != UDP_ACTIVE || s->socket < 0) { close_udp_proxy(s); return; }
    if (event_session == &p->control) { control_event(args, p, event->events); return; }
    if (event->events & (EPOLLERR | EPOLLHUP)) { fail_proxy(args, p, "relay socket failed"); return; }
    if (!(event->events & EPOLLIN)) return;
    size_t max = p->family == AF_INET ? UDP4_MAXMSG : UDP6_MAXMSG;
    uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, max, "udp proxy recv");
    if (buffer == NULL) { fail_proxy(args, p, "cannot receive relay datagram"); return; }
    struct iovec iov = {.iov_base = buffer, .iov_len = max};
    struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1};
    ssize_t bytes = recvmsg(s->socket, &message, 0);
    if (bytes < 0) {
        if (errno != EAGAIN && errno != EINTR) fail_proxy(args, p, "relay receive failed");
    } else if (!(message.msg_flags & MSG_TRUNC) && bytes >= 4 &&
               buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0) {
        size_t address = buffer[3] == 1 ? 4 : buffer[3] == 4 ? 16 : 0;
        size_t header = 6 + address;
        if (address == (s->udp.version == 4 ? 4 : 16) && (size_t) bytes >= header &&
            (size_t) bytes - header <= (s->udp.version == 4 ? UDP4_MAXMSG : UDP6_MAXMSG) &&
            memcmp(buffer + 4, &s->udp.daddr, address) == 0 &&
            memcmp(buffer + 4 + address, &s->udp.dest, 2) == 0) {
            s->udp.time = time(NULL);
            forward_udp_reply(args, s, buffer + header, (size_t) bytes - header);
            if (s->udp.state != UDP_ACTIVE) close_udp_proxy(s);
        }
    }
    release_packet_buffer(args, buffer);
}
