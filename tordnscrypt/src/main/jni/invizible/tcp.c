#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"
#pragma ide diagnostic ignored "cppcoreguidelines-avoid-magic-numbers"
#pragma ide diagnostic ignored "readability-magic-numbers"
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

extern char tor_socks5_addr[INET6_ADDRSTRLEN + 1];
extern int tor_socks5_port;
extern char tor_socks5_username[127 + 1];
extern char tor_socks5_password[127 + 1];
extern bool tor_isolate_uid;
extern atomic_bool tor_connection_available;

extern char proxy_socks5_addr[INET6_ADDRSTRLEN + 1];
extern int proxy_socks5_port;
extern char proxy_socks5_username[127 + 1];
extern char proxy_socks5_password[127 + 1];

extern int own_uid;

static int open_tcp_socket_with_error(const struct arguments *args,
                                     const struct tcp_session *cur,
                                     struct allowed *redirect, int *connect_error_out);

static bool reserve_tcp_memory(struct tcp_session *cur, size_t bytes,
                              unsigned int nodes, bool dns, bool progress) {
    if (cur->memory == NULL)
        return true;
    struct tcp_memory *memory = cur->memory;
    size_t used = dns ? memory->dns : memory->queued;
    size_t limit = dns ? TCP_MEMORY_DNS_LIMIT : progress
                   ? TCP_MEMORY_PROGRESS_LIMIT : TCP_MEMORY_QUEUE_LIMIT;
    unsigned int node_limit = progress ? TCP_MEMORY_PROGRESS_NODES : TCP_MEMORY_QUEUE_NODES;
    if (used > limit || bytes > limit - used ||
        (!dns && (memory->nodes > node_limit || nodes > node_limit - memory->nodes)))
        return false;
    if (dns)
        memory->dns += bytes;
    else {
        memory->queued += bytes;
        memory->nodes += nodes;
    }
    return true;
}

static void release_tcp_memory(struct tcp_session *cur, size_t bytes,
                              unsigned int nodes, bool dns) {
    if (cur->memory == NULL || (bytes == 0 && nodes == 0))
        return;
    if (dns) {
        cur->memory->dns -= bytes;
        cur->memory->dns_generation++;
    } else {
        cur->memory->queued -= bytes;
        cur->memory->nodes -= nodes;
        cur->memory->generation++;
    }
}

static size_t tcp_memory_window(const struct tcp_session *cur) {
    if (cur->memory == NULL)
        return TCP_QUEUE_BYTES;
    bool dns = ntohs(cur->dest) == 53;
    size_t limit = dns ? TCP_MEMORY_PROGRESS_LIMIT : TCP_MEMORY_QUEUE_LIMIT;
    unsigned int nodes = dns ? TCP_MEMORY_PROGRESS_NODES : TCP_MEMORY_QUEUE_NODES;
    if (cur->memory->queued >= limit || cur->memory->nodes >= nodes)
        return 0;
    size_t available = limit - cur->memory->queued;
    available = available > sizeof(struct segment) ? available - sizeof(struct segment) : 0;
    // A positive internal window that scales to zero would stop both memory
    // release checks and writable monitoring before the app can send again.
    return available >= ((size_t) 1 << cur->recv_scale) ? available : 0;
}

static void free_tcp_segment(struct tcp_session *cur, struct segment *segment) {
    release_tcp_memory(cur, sizeof(*segment) + segment->len, 1, false);
    ng_free(segment->data, __FILE__, __LINE__);
    ng_free(segment, __FILE__, __LINE__);
}

static struct segment *allocate_tcp_segment(struct tcp_session *cur, uint16_t length,
                                            bool progress, const char *node_tag,
                                            const char *data_tag) {
    size_t bytes = sizeof(struct segment) + length;
    if (!reserve_tcp_memory(cur, bytes, 1, false, progress))
        return NULL;
    struct segment *segment = ng_malloc(sizeof(*segment), node_tag);
    if (segment != NULL) {
        segment->data = ng_malloc(length, data_tag);
        if (segment->data != NULL)
            return segment;
        ng_free(segment, __FILE__, __LINE__);
    }
    release_tcp_memory(cur, bytes, 1, false);
    return NULL;
}

static void clear_dns_response(struct tcp_session *cur) {
    if (cur->dns_response != NULL)
        ng_free(cur->dns_response, __FILE__, __LINE__);
    cur->dns_response = NULL;
    release_tcp_memory(cur, cur->dns_response_capacity, 0, true);
    cur->dns_response_capacity = 0;
    cur->dns_waiting = false;
    cur->dns_response_size = cur->dns_response_used = cur->dns_response_sent = 0;
    cur->dns_response_ready = false;
    cur->dns_header_used = 0;
    cur->dns_body_remaining = 0;
}

void clear_tcp_data(struct tcp_session *cur) {
    clear_dns_response(cur);
    struct segment *s = cur->forward;
    cur->forward = NULL;
    cur->queue_bytes = cur->queue_unsent = cur->queue_segments = 0;
    while (s != NULL) {
        struct segment *p = s;
        s = s->next;
        free_tcp_segment(cur, p);
    }
}

static void close_tcp_socket(struct ng_session *s) {
    if (s->socket >= 0) {
        if (close(s->socket))
            log_android(ANDROID_LOG_ERROR, "TCP close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        s->socket = -1;
    }
}

// Acknowledge the app's FIN only after every preceding byte has been forwarded.
static int finish_tcp_input(const struct arguments *args, struct ng_session *s) {
    if (!s->tcp.fin_received || s->tcp.fin_acked || s->tcp.forward != NULL ||
        s->tcp.remote_seq != s->tcp.fin_seq || s->tcp.state == TCP_CLOSING)
        return 0;

    int result;
    do {
        result = shutdown(s->socket, SHUT_WR);
    } while (result < 0 && errno == EINTR);
    if (result < 0) {
        int shutdown_error = errno;
        char source[INET6_ADDRSTRLEN + 1] = "?";
        char dest[INET6_ADDRSTRLEN + 1] = "?";
        inet_ntop(s->tcp.version == 4 ? AF_INET : AF_INET6,
                  s->tcp.version == 4 ? (const void *) &s->tcp.saddr.ip4
                                      : (const void *) &s->tcp.saddr.ip6,
                  source, sizeof(source));
        inet_ntop(s->tcp.version == 4 ? AF_INET : AF_INET6,
                  s->tcp.version == 4 ? (const void *) &s->tcp.daddr.ip4
                                      : (const void *) &s->tcp.daddr.ip6,
                  dest, sizeof(dest));
        log_android(ANDROID_LOG_ERROR,
                    "TCP%d socket from %s/%u to %s/%u %s loc %u rem %u "
                    "uid %d socket %d socks5 %d upstream_eof %d "
                    "fin_received %d fin_acked %d fin %u acked %u shutdown write error %d: %s",
                    s->tcp.version, source, ntohs(s->tcp.source), dest, ntohs(s->tcp.dest),
                    strstate(s->tcp.state), s->tcp.local_seq - s->tcp.local_start,
                    s->tcp.remote_seq - s->tcp.remote_start, s->tcp.uid, s->socket,
                    s->tcp.socks5, s->tcp.upstream_eof, s->tcp.fin_received, s->tcp.fin_acked,
                    s->tcp.fin_seq - s->tcp.remote_start, s->tcp.acked - s->tcp.local_start,
                    shutdown_error, strerror(shutdown_error));
        write_rst(args, &s->tcp);
        return -1;
    }
    s->tcp.fin_acked = true;
    s->tcp.remote_seq++;
    if (s->tcp.upstream_eof) {
        close_tcp_socket(s);
        s->tcp.state = (s->tcp.acked == s->tcp.local_seq ? TCP_CLOSE : TCP_LAST_ACK);
    } else
        s->tcp.state = TCP_CLOSE_WAIT;
    if (write_ack(args, &s->tcp) < 0)
        return -1;
    s->tcp.time = time(NULL);
    return 1;
}

static void flush_dns_response(const struct arguments *args, struct tcp_session *cur,
                               uint32_t window) {
    size_t count = cur->dns_response_size - cur->dns_response_sent;
    if (count > cur->mss)
        count = cur->mss;
    if (count > window)
        count = window;
    if (count == 0)
        return;
    if (write_data(args, cur, cur->dns_response + cur->dns_response_sent, count) < 0)
        return;
    cur->dns_response_sent += count;
    cur->local_seq += count;
    cur->unconfirmed++;
    if (cur->dns_response_sent == cur->dns_response_size)
        clear_dns_response(cur);
}

static bool prepare_dns_response(const struct arguments *args, struct tcp_session *cur) {
    if (cur->memory == NULL)
        cur->memory = &args->ctx->tcp_memory;
    cur->dns_waiting = false;
    if (cur->dns_response == NULL) {
        if (!reserve_tcp_memory(cur, 2, 0, true, false)) {
            cur->dns_waiting = true;
            cur->dns_memory_generation = cur->memory->dns_generation;
            return false;
        }
        cur->dns_response = ng_malloc(2, "tcp dns frame");
        if (cur->dns_response == NULL) {
            release_tcp_memory(cur, 2, 0, true);
            write_rst(args, cur);
            return false;
        }
        cur->dns_response_capacity = cur->dns_response_size = 2;
    }
    if (cur->dns_response_used == 2 && cur->dns_response_size == 2) {
        size_t size = (size_t) read_net16(cur->dns_response) + 2;
        if (size > 2) {
            // Reserve the entire replacement while the old allocation is live.
            if (!reserve_tcp_memory(cur, size, 0, true, false)) {
                cur->dns_waiting = true;
                cur->dns_memory_generation = cur->memory->dns_generation;
                return false;
            }
            uint8_t *resized = ng_realloc(cur->dns_response, size, "tcp dns frame");
            if (resized == NULL) {
                release_tcp_memory(cur, size, 0, true);
                write_rst(args, cur);
                return false;
            }
            release_tcp_memory(cur, cur->dns_response_capacity, 0, true);
            cur->dns_response = resized;
            cur->dns_response_capacity = cur->dns_response_size = size;
        }
    }
    return true;
}

static void read_dns_response(const struct arguments *args, struct ng_session *s,
                              uint32_t window) {
    struct tcp_session *cur = &s->tcp;
    if (cur->dns_response_ready) {
        flush_dns_response(args, cur, window);
        return;
    }
    if (!prepare_dns_response(args, cur))
        return;
    size_t count = cur->dns_response_size - cur->dns_response_used;
    if (count > cur->mss)
        count = cur->mss;
    if (count > window)
        count = window;
    ssize_t bytes = recv(s->socket, cur->dns_response + cur->dns_response_used, count, 0);
    if (bytes < 0) {
        if (errno != EINTR && errno != EAGAIN) {
            log_android(ANDROID_LOG_WARN, "TCP DNS recv error %d: %s", errno, strerror(errno));
            write_rst(args, cur);
        }
        return;
    }
    if (bytes == 0) {
        if (cur->dns_response_used != 0) {
            log_android(ANDROID_LOG_WARN, "TCP DNS incomplete response at EOF");
            write_rst(args, cur);
        } else {
            clear_dns_response(cur);
            cur->upstream_eof = true;
            if (write_fin_ack(args, cur) >= 0) {
                cur->local_seq++;
                cur->state = (cur->fin_acked ? TCP_LAST_ACK : TCP_FIN_WAIT1);
                if (cur->fin_acked)
                    close_tcp_socket(s);
            }
        }
        return;
    }
    cur->received += bytes;
    if (!check_dns_tcp_response(cur, cur->dns_response + cur->dns_response_used, (size_t) bytes)) {
        log_android(ANDROID_LOG_WARN, "TCP DNS response has multiple questions");
        write_rst(args, cur);
        return;
    }
    cur->dns_response_used += bytes;
    if (!prepare_dns_response(args, cur))
        return;
    if (cur->dns_response_used == cur->dns_response_size) {
        size_t length = cur->dns_response_size - 2;
        parse_dns_response(args, s, cur->dns_response + 2, &length);
        uint16_t prefix = htons((uint16_t) length);
        memcpy(cur->dns_response, &prefix, sizeof(prefix));
        cur->dns_response_size = length + 2;
        cur->dns_response_ready = true;
        flush_dns_response(args, cur, window);
    }
}

int get_tcp_timeout(const struct tcp_session *t, int sessions, int maxsessions) {
    int timeout;
    if (t->state == TCP_LISTEN || t->state == TCP_SYN_RECV)
        timeout = TCP_INIT_TIMEOUT;
    else if (t->state == TCP_ESTABLISHED)
        timeout = TCP_IDLE_TIMEOUT;
    else
        timeout = TCP_CLOSE_TIMEOUT;

    int scale = 100 - sessions * 100 / maxsessions;
    timeout = timeout * scale / 100;

    return timeout;
}

struct tcp_socket_log {
    const struct tcp_session *tcp;
    int socket;
    uint8_t state;
    uint32_t local;
    uint32_t remote;
    bool maintenance;
    char text[250];
};

static const char *tcp_socket_text(struct tcp_socket_log *diagnostic) {
    if (diagnostic->text[0] == 0) {
        int saved_errno = errno;
        const struct tcp_session *tcp = diagnostic->tcp;
        struct log_address source = {.version = tcp->version, .address = &tcp->saddr};
        struct log_address dest = {.version = tcp->version, .address = &tcp->daddr};
        if (diagnostic->maintenance)
            snprintf(diagnostic->text, sizeof(diagnostic->text),
                     "TCP socket from %s/%u to %s/%u %s socket %d",
                     format_log_address(&source), ntohs(tcp->source),
                     format_log_address(&dest), ntohs(tcp->dest),
                     strstate(diagnostic->state), diagnostic->socket);
        else
            snprintf(diagnostic->text, sizeof(diagnostic->text),
                     "TCP socket from %s/%u to %s/%u %s loc %u rem %u",
                     format_log_address(&source), ntohs(tcp->source),
                     format_log_address(&dest), ntohs(tcp->dest),
                     strstate(diagnostic->state), diagnostic->local, diagnostic->remote);
        errno = saved_errno;
    }
    return diagnostic->text;
}

struct tcp_packet_log {
    const uint8_t *pkt;
    const struct tcphdr *header;
    const struct tcp_session *tcp;
    int uid;
    uint16_t datalen;
    uint8_t state;
    uint32_t local;
    uint32_t remote;
    uint32_t acked;
    bool detailed;
    char text[400];
};

static const char *tcp_packet_text(struct tcp_packet_log *diagnostic) {
    if (diagnostic->text[0] == 0) {
        int saved_errno = errno;
        const struct tcphdr *tcp = diagnostic->header;
        const struct tcp_session *cur = diagnostic->tcp;
        int version = diagnostic->pkt[0] >> 4;
        const struct iphdr *ip4 = (const struct iphdr *) diagnostic->pkt;
        const struct ip6_hdr *ip6 = (const struct ip6_hdr *) diagnostic->pkt;
        struct log_address source = {.version = version, .address = version == 4
                ? (const void *) &ip4->saddr : &ip6->ip6_src};
        struct log_address dest = {.version = version, .address = version == 4
                ? (const void *) &ip4->daddr : &ip6->ip6_dst};
        char flags[7];
        int flen = 0;
        if (tcp->syn) flags[flen++] = 'S';
        if (tcp->ack) flags[flen++] = 'A';
        if (tcp->psh) flags[flen++] = 'P';
        if (tcp->fin) flags[flen++] = 'F';
        if (tcp->rst) flags[flen++] = 'R';
        if (tcp->urg) flags[flen++] = 'U';
        flags[flen] = 0;
        int length = snprintf(diagnostic->text, sizeof(diagnostic->text),
                "TCP %s %s/%u > %s/%u seq %u ack %u data %u win %u uid %d",
                flags, format_log_address(&source), ntohs(tcp->source),
                format_log_address(&dest), ntohs(tcp->dest),
                ntohl(tcp->seq) - (cur == NULL ? 0 : cur->remote_start),
                tcp->ack ? ntohl(tcp->ack_seq) - (cur == NULL ? 0 : cur->local_start) : 0,
                diagnostic->datalen, ntohs(tcp->window), diagnostic->uid);
        if (diagnostic->detailed && length >= 0 && (size_t) length < sizeof(diagnostic->text))
            snprintf(diagnostic->text + length, sizeof(diagnostic->text) - (size_t) length,
                     " %s loc %u rem %u acked %u", strstate(diagnostic->state),
                     diagnostic->local, diagnostic->remote, diagnostic->acked);
        errno = saved_errno;
    }
    return diagnostic->text;
}

int check_tcp_session(const struct arguments *args, struct ng_session *s,
                      int sessions, int maxsessions) {
    time_t now = time(NULL);

    struct log_address dest = {.version = s->tcp.version, .address = &s->tcp.daddr};
    struct tcp_socket_log diagnostic = {.tcp = &s->tcp, .socket = s->socket,
            .state = s->tcp.state, .maintenance = true};

    int timeout = get_tcp_timeout(&s->tcp, sessions, maxsessions);

    // Check session timeout
    if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE &&
        s->tcp.time + timeout < now) {
        log_android(ANDROID_LOG_INFO, "%s idle %d/%d sec ", tcp_socket_text(&diagnostic), now - s->tcp.time,
                    timeout);
        if (s->tcp.state == TCP_LISTEN)
            s->tcp.state = TCP_CLOSING;
        else
            write_rst(args, &s->tcp);

        bool not_own_uid = s->tcp.uid != own_uid;
        bool not_dns_request = ntohs(s->tcp.dest) != 53;
        bool not_to_i2pd = !str_equal(I2PD_REDIRECT_ADDRESS, format_log_address(&dest));
        bool not_to_localhost = !(str_equal(LOOPBACK_ADDRESS, format_log_address(&dest)) ||
                                  str_equal(LOOPBACK_ADDRESS_IPv6, format_log_address(&dest)));
        if (tor_connection_available && s->tcp.socks5 > SOCKS5_NONE && s->tcp.socks5 < SOCKS5_CONNECTED
            && s->socket >= 0 && not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
            && *tor_socks5_addr && tor_socks5_port) {

            if (check_tor_connection(args)) {
                tor_connection_available = false;
            }
            log_android(ANDROID_LOG_WARN,
                        "Suspect Tor connection is unavailable %s idle %d/%d sec socks5 state %d",
                        tcp_socket_text(&diagnostic), now - s->tcp.time, timeout, s->tcp.socks5);
        }
    }

    // Check closing sessions
    if (s->tcp.state == TCP_CLOSING) {
        // Full shutdown, reset, or timeout releases the retained socket.
        if (s->socket >= 0) {
            if (close(s->socket))
                log_android(ANDROID_LOG_ERROR, "%s close error %d: %s",
                            tcp_socket_text(&diagnostic), errno, strerror(errno));
            else
                log_android(ANDROID_LOG_INFO, "%s close", tcp_socket_text(&diagnostic));
            s->socket = -1;
        }

        s->tcp.time = time(NULL);
        s->tcp.state = TCP_CLOSE;
    }

    // Keep the closed-session metadata for late packets, but no payload can be forwarded now.
    if (s->tcp.state == TCP_CLOSE)
        clear_tcp_data(&s->tcp);

    if ((s->tcp.state == TCP_CLOSING || s->tcp.state == TCP_CLOSE) &&
        (s->tcp.sent || s->tcp.received)) {
        account_usage(args, s->tcp.version, IPPROTO_TCP,
                      format_log_address(&dest), ntohs(s->tcp.dest), s->tcp.uid, s->tcp.sent, s->tcp.received);
        s->tcp.sent = 0;
        s->tcp.received = 0;
    }

    // Cleanup lingering sessions
    if (s->tcp.state == TCP_CLOSE && s->tcp.time + TCP_KEEP_TIMEOUT < now)
        return 1;

    return 0;
}

int monitor_tcp_session(const struct arguments *args, struct ng_session *s, int epoll_fd) {
    int recheck = 0;
    unsigned int events = EPOLLERR;

    if (s->tcp.memory != NULL && s->tcp.state != TCP_LISTEN &&
        s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE) {
        if (s->tcp.dns_waiting && s->tcp.dns_memory_generation != s->tcp.memory->dns_generation)
            prepare_dns_response(args, &s->tcp);
        if (s->tcp.memory_waiting && !s->tcp.fin_acked && s->tcp.recv_window == 0 &&
            s->tcp.memory_generation != s->tcp.memory->generation) {
            s->tcp.memory_generation = s->tcp.memory->generation;
            if (tcp_memory_window(&s->tcp) > 0) {
                // Once shared memory is available, socket readiness and this
                // flow's own queue control recovery if its window stays shut.
                s->tcp.memory_waiting = false;
                uint32_t window = get_receive_window(s);
                if (window > 0) {
                    s->tcp.recv_window = window;
                    write_ack(args, &s->tcp);
                }
            }
        }
    }

    // DNS output is already in memory and targets TUN, not the upstream socket.
    // Make bounded progress each loop even if that socket's send buffer is full.
    if (!s->tcp.upstream_eof &&
        (s->tcp.state == TCP_ESTABLISHED || s->tcp.state == TCP_CLOSE_WAIT)) {
        for (int count = 0; count < TCP_YIELD && s->tcp.dns_response_ready; count++) {
            uint32_t window = get_send_window(&s->tcp);
            if (window == 0)
                break;
            // Even a small reopening resets probe backoff if DNS fills it again.
            s->tcp.window_probe_interval = 0;
            uint32_t local_seq = s->tcp.local_seq;
            flush_dns_response(args, &s->tcp, window);
            if (s->tcp.local_seq == local_seq)
                break;
            s->tcp.time = time(NULL);
        }
    }

    if (s->tcp.state == TCP_LISTEN) {
        // Check for connected = writable
        if (s->tcp.socks5 == SOCKS5_NONE)
            events = events | EPOLLOUT;
        else
            events = events | EPOLLIN;
    } else if (s->tcp.state == TCP_ESTABLISHED || s->tcp.state == TCP_CLOSE_WAIT ||
               s->tcp.state == TCP_FIN_WAIT1 || s->tcp.state == TCP_FIN_WAIT2) {

        // Check for incoming data
        if (!s->tcp.upstream_eof && get_send_window(&s->tcp) > 0) {
            if (s->tcp.dns_response_ready)
                recheck = EPOLL_MIN_CHECK; // More buffered output after this loop's packet budget.
            else if (!s->tcp.dns_waiting)
                events |= EPOLLIN;
            s->tcp.window_probe_interval = 0;
        } else if (!s->tcp.upstream_eof) {
            long long ms = get_ms();
            if (s->tcp.window_probe_interval == 0) {
                s->tcp.window_probe_interval = TCP_WINDOW_PROBE_MIN;
                s->tcp.last_keep_alive = ms;
            }
            long long remaining = s->tcp.window_probe_interval - (ms - s->tcp.last_keep_alive);
            if (remaining <= 0) {
                s->tcp.last_keep_alive = ms;
                if (s->tcp.window_probe_interval < TCP_WINDOW_PROBE_MAX) {
                    s->tcp.window_probe_interval *= 2;
                    if (s->tcp.window_probe_interval > TCP_WINDOW_PROBE_MAX)
                        s->tcp.window_probe_interval = TCP_WINDOW_PROBE_MAX;
                }
                log_android(ANDROID_LOG_INFO, "Sending keep alive to update send window");
                s->tcp.remote_seq--;
                write_ack(args, &s->tcp);
                s->tcp.remote_seq++;
                remaining = s->tcp.window_probe_interval;
            }
            recheck = (int) remaining;
        }

        // Check for outgoing data
        if (s->tcp.forward != NULL) {
            // Nonblocking send handles partial writes. Let epoll wait for space;
            // a sequence gap instead waits for the missing packet from TUN.
            if (s->tcp.forward->seq == s->tcp.remote_seq)
                events = events | EPOLLOUT;
        } else if (!s->tcp.fin_acked && s->tcp.recv_window == 0 &&
                   tcp_memory_window(&s->tcp) > 0) {
            // The final queued write can fill the socket exactly. Wait for
            // space so check_tcp_socket can advertise the reopened app window.
            events |= EPOLLOUT;
        }
    }

    // HUP is reported even when no reads were requested. While DNS waits only
    // for shared memory, consume it once and rearm when the wait ends; otherwise
    // a closed upstream with unread data would wake epoll continuously.
    if (s->tcp.dns_waiting && events == EPOLLERR)
        events |= EPOLLONESHOT;

    if (events != s->ev.events) {
        s->ev.events = events;
        if (epoll_ctl(epoll_fd, EPOLL_CTL_MOD, s->socket, &s->ev)) {
            s->tcp.state = TCP_CLOSING;
            log_android(ANDROID_LOG_ERROR, "epoll mod tcp error %d: %s", errno, strerror(errno));
        } else
            log_android(ANDROID_LOG_DEBUG, "epoll mod tcp socket %d in %d out %d",
                        s->socket, (events & EPOLLIN) != 0, (events & EPOLLOUT) != 0);
    }

    return recheck;
}

uint32_t get_send_window(const struct tcp_session *cur) {
    uint32_t behind;
    if (cur->acked <= cur->local_seq)
        behind = (cur->local_seq - cur->acked);
    else
        behind = (0x10000 + cur->local_seq - cur->acked);
    behind += (cur->unconfirmed + 1) * 40; // Maximum header size

    uint32_t total = (behind < cur->send_window ? cur->send_window - behind : 0);

    log_android(ANDROID_LOG_DEBUG, "Send window behind %u window %u total %u",
                behind, cur->send_window, total);

    return total;
}

uint32_t get_receive_buffer(const struct ng_session *cur) {
    if (cur->socket < 0)
        return 0;

    // Get send buffer size
    // /proc/sys/net/core/wmem_default
    int sendbuf = 0;
    int sendbufsize = sizeof(sendbuf);
    if (getsockopt(cur->socket, SOL_SOCKET, SO_SNDBUF, &sendbuf, (socklen_t *) &sendbufsize) < 0)
        log_android(ANDROID_LOG_WARN, "getsockopt SO_RCVBUF %d: %s", errno, strerror(errno));

    if (sendbuf == 0)
        sendbuf = SEND_BUF_DEFAULT;

    // Get unsent data size
    int unsent = 0;
    if (ioctl(cur->socket, SIOCOUTQ, &unsent))
        log_android(ANDROID_LOG_WARN, "ioctl SIOCOUTQ %d: %s", errno, strerror(errno));

    uint32_t total = (uint32_t) (unsent < sendbuf ? sendbuf - unsent : 0);

    log_android(ANDROID_LOG_DEBUG, "Send buffer %u unsent %u total %u",
                sendbuf, unsent, total);

    return total;
}

static uint32_t receive_window_for_buffer(struct ng_session *cur, uint32_t window) {
    uint32_t max = ((uint32_t) 0xFFFF) << cur->tcp.recv_scale;
    if (window > max) {
        log_android(ANDROID_LOG_DEBUG, "Receive window %u > max %u", window, max);
        window = max;
    }

    uint32_t total = (cur->tcp.queue_unsent < window ? window - cur->tcp.queue_unsent : 0);
    uint32_t unit = (uint32_t) 1 << cur->tcp.recv_scale;
    // A writable socket can have less than one scaled unit free. The retained
    // queue can bridge that small gap, provided both memory limits below fit
    // the whole unit. Otherwise a zero advertisement could leave a positive
    // internal window, or reopening it could spin on an already writable socket.
    if (total > 0 && total < unit)
        total = unit;
    uint32_t capacity = (cur->tcp.queue_bytes < TCP_QUEUE_BYTES ? TCP_QUEUE_BYTES - cur->tcp.queue_bytes : 0);
    if (total > capacity)
        total = capacity;
    if (cur->tcp.queue_segments >= TCP_QUEUE_SEGMENTS)
        total = 0;
    size_t shared = tcp_memory_window(&cur->tcp);
    cur->tcp.memory_waiting = capacity >= unit &&
                             cur->tcp.queue_segments < TCP_QUEUE_SEGMENTS && shared < unit;
    if (cur->tcp.memory_waiting)
        cur->tcp.memory_generation = cur->tcp.memory->generation;
    if (total > shared)
        total = (uint32_t) shared;
    // Keep recovery, admission and readiness decisions consistent with the
    // window encoded on the wire, after applying every capacity limit.
    total = (total >> cur->tcp.recv_scale) << cur->tcp.recv_scale;

    log_android(ANDROID_LOG_DEBUG, "Receive window toforward %u window %u total %u",
                cur->tcp.queue_unsent, window, total);

    return total;
}

uint32_t get_receive_window(struct ng_session *cur) {
    return receive_window_for_buffer(cur, get_receive_buffer(cur));
}

void check_tcp_socket(const struct arguments *args,
                      const struct epoll_event *ev,
                      const int epoll_fd) {
    struct tcp_socket_capacity capacity = {0};
    check_tcp_socket_with_capacity(args, ev, epoll_fd, &capacity);
}

void check_tcp_socket_with_capacity(const struct arguments *args,
                                   const struct epoll_event *ev, int epoll_fd,
                                   struct tcp_socket_capacity *capacity) {
    struct ng_session *s = (struct ng_session *) ev->data.ptr;

    int oldstate = s->tcp.state;
    uint32_t oldlocal = s->tcp.local_seq;
    uint32_t oldremote = s->tcp.remote_seq;

    struct tcp_socket_log diagnostic = {.tcp = &s->tcp, .state = oldstate,
            .local = oldlocal - s->tcp.local_start, .remote = oldremote - s->tcp.remote_start};
    struct log_address dest = {.version = s->tcp.version, .address = &s->tcp.daddr};

    // Check socket error
    if (ev->events & EPOLLERR) {
        s->tcp.time = time(NULL);

        int serr = 0;
        socklen_t optlen = sizeof(int);
        int err = getsockopt(s->socket, SOL_SOCKET, SO_ERROR, &serr, &optlen);
        if (err < 0)
            log_android(ANDROID_LOG_ERROR, "%s getsockopt error %d: %s",
                        tcp_socket_text(&diagnostic), errno, strerror(errno));
        else if (serr)
            log_android(ANDROID_LOG_ERROR, "%s uid %d socket %d socks5 %d SO_ERROR %d: %s",
                        tcp_socket_text(&diagnostic), s->tcp.uid, s->socket, s->tcp.socks5, serr, strerror(serr));

        write_rst(args, &s->tcp);

        if (err >= 0 && (serr == ECONNREFUSED || serr == EHOSTUNREACH)) {
            write_connection_unreach(args, s, serr);
        }
    } else {
        // Assume socket okay
        if (s->tcp.state == TCP_LISTEN) {
            // Check socket connect
            if (s->tcp.socks5 == SOCKS5_NONE) {
                if (ev->events & EPOLLOUT) {
                    log_android(ANDROID_LOG_INFO, "%s connected", tcp_socket_text(&diagnostic));

                    // https://tools.ietf.org/html/rfc1928
                    // https://tools.ietf.org/html/rfc1929
                    // https://en.wikipedia.org/wiki/SOCKS#SOCKS5

                    bool not_own_uid = s->tcp.uid != own_uid;
                    bool not_dns_request = ntohs(s->tcp.dest) != 53;
                    bool not_to_i2pd = !str_equal(I2PD_REDIRECT_ADDRESS, format_log_address(&dest));
                    bool not_to_localhost = !(str_equal(LOOPBACK_ADDRESS, format_log_address(&dest)) ||
                                              str_equal(LOOPBACK_ADDRESS_IPv6, format_log_address(&dest)));

                    bool redirect_to_tor = false;
                    if (not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
                        && *tor_socks5_addr && tor_socks5_port) {
                        redirect_to_tor = is_redirect_to_tor(
                                args,
                                s->tcp.uid,
                                format_log_address(&dest),
                                ntohs(s->tcp.dest)
                        );
                    }

                    bool redirect_to_proxy = false;
                    if (not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
                        && *proxy_socks5_addr && proxy_socks5_port) {
                        redirect_to_proxy = is_redirect_to_proxy(
                                args,
                                s->tcp.uid,
                                format_log_address(&dest),
                                ntohs(s->tcp.dest)
                        );
                    }

                    if (redirect_to_tor || redirect_to_proxy) {
                        s->tcp.socks5 = SOCKS5_HELLO;
                    } else {
                        s->tcp.socks5 = SOCKS5_CONNECTED;
                    }
                }
            } else {
                if (ev->events & EPOLLIN) {
                    uint8_t buffer[32];
                    ssize_t bytes = recv(s->socket, buffer, sizeof(buffer), 0);
                    if (bytes < 0) {
                        log_android(ANDROID_LOG_ERROR, "%s recv SOCKS5 error %d: %s",
                                    tcp_socket_text(&diagnostic), errno, strerror(errno));
                        write_rst(args, &s->tcp);
                    } else {
                        if (LOG_LEVEL < ANDROID_LOG_WARN) {
                            char *h = hex(buffer, (const size_t) bytes);
                            log_android(ANDROID_LOG_INFO, "%s recv SOCKS5 %s", tcp_socket_text(&diagnostic),
                                        h == NULL ? "(unavailable)" : h);
                            ng_free(h, __FILE__, __LINE__);
                        }
                        if (s->tcp.socks5 == SOCKS5_HELLO &&
                            bytes == 2 && buffer[0] == 5) {
                            if (buffer[1] == 0)
                                s->tcp.socks5 = SOCKS5_CONNECT;
                            else if (buffer[1] == 2)
                                s->tcp.socks5 = SOCKS5_AUTH;
                            else {
                                s->tcp.socks5 = 0;
                                log_android(ANDROID_LOG_ERROR, "%s SOCKS5 auth %d not supported",
                                            tcp_socket_text(&diagnostic), buffer[1]);
                                write_rst(args, &s->tcp);
                            }

                        } else if (s->tcp.socks5 == SOCKS5_AUTH &&
                                   bytes == 2 &&
                                   (buffer[0] == 1 || buffer[0] == 5)) {
                            if (buffer[1] == 0) {
                                s->tcp.socks5 = SOCKS5_CONNECT;
                                log_android(ANDROID_LOG_INFO, "%s SOCKS5 auth OK", tcp_socket_text(&diagnostic));
                            } else {
                                s->tcp.socks5 = 0;
                                log_android(ANDROID_LOG_ERROR, "%s SOCKS5 auth error %d",
                                            tcp_socket_text(&diagnostic), buffer[1]);
                                write_rst(args, &s->tcp);
                            }

                        } else if (s->tcp.socks5 == SOCKS5_CONNECT &&
                                   (bytes == 6 + (s->tcp.version == 4 ? 4 : 16) ||
                                    //for IPv4-mapped IPv6 address proxy
                                    s->tcp.version == 6 && bytes == 6 + 4) &&
                                   buffer[0] == 5) {
                            if (buffer[1] == 0) {
                                s->tcp.socks5 = SOCKS5_CONNECTED;
                                log_android(ANDROID_LOG_INFO, "%s SOCKS5 connected", tcp_socket_text(&diagnostic));
                            } else {
                                s->tcp.socks5 = 0;
                                log_android(ANDROID_LOG_ERROR, "%s SOCKS5 connect error %d",
                                            tcp_socket_text(&diagnostic), buffer[1]);
                                write_rst(args, &s->tcp);
                                /*
                                    0x00 = request granted
                                    0x01 = general failure
                                    0x02 = connection not allowed by ruleset
                                    0x03 = network unreachable
                                    0x04 = host unreachable
                                    0x05 = connection refused by destination host
                                    0x06 = TTL expired
                                    0x07 = command not supported / protocol error
                                    0x08 = address type not supported
                                 */
                            }

                        } else {
                            s->tcp.socks5 = 0;
                            log_android(ANDROID_LOG_ERROR, "%s recv SOCKS5 state %d",
                                        tcp_socket_text(&diagnostic), s->tcp.socks5);
                            write_rst(args, &s->tcp);
                        }
                    }
                }
            }

            if (s->tcp.socks5 == SOCKS5_HELLO) {
                uint8_t buffer[4] = {5, 2, 0, 2};
                if (LOG_LEVEL < ANDROID_LOG_WARN) {
                    char *h = hex(buffer, sizeof(buffer));
                    log_android(ANDROID_LOG_INFO, "%s sending SOCKS5 hello: %s",
                                tcp_socket_text(&diagnostic), h == NULL ? "(unavailable)" : h);
                    ng_free(h, __FILE__, __LINE__);
                }
                ssize_t sent = send(s->socket, buffer, sizeof(buffer), MSG_NOSIGNAL);
                if (sent < 0) {
                    log_android(ANDROID_LOG_ERROR, "%s send SOCKS5 hello error %d: %s",
                                tcp_socket_text(&diagnostic), errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_AUTH) {
                char socks5_username[127 + 1];
                char socks5_password[127 + 1];

                bool not_own_uid = s->tcp.uid != own_uid;
                bool not_dns_request = ntohs(s->tcp.dest) != 53;
                bool not_to_i2pd = !str_equal(I2PD_REDIRECT_ADDRESS, format_log_address(&dest));
                bool not_to_localhost = !(str_equal(LOOPBACK_ADDRESS, format_log_address(&dest)) ||
                                          str_equal(LOOPBACK_ADDRESS_IPv6, format_log_address(&dest)));

                bool redirect_to_tor = false;
                if (not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
                    && *tor_socks5_addr && tor_socks5_port) {
                    redirect_to_tor = is_redirect_to_tor(
                            args,
                            s->tcp.uid,
                            format_log_address(&dest),
                            ntohs(s->tcp.dest)
                    );
                }

                if (*proxy_socks5_addr && proxy_socks5_port && !redirect_to_tor) {
                    strcpy(socks5_username, proxy_socks5_username);
                    strcpy(socks5_password, proxy_socks5_password);
                } else if (tor_isolate_uid) {
                    sprintf(socks5_username, "%d", s->tcp.uid);
                    sprintf(socks5_password, "%d", s->tcp.uid);
                } else {
                    strcpy(socks5_username, tor_socks5_username);
                    strcpy(socks5_password, tor_socks5_password);
                }

                uint8_t ulen = strlen(socks5_username);
                uint8_t plen = strlen(socks5_password);
                uint8_t buffer[512];
                *(buffer + 0) = 1; // Version
                *(buffer + 1) = ulen;
                memcpy(buffer + 2, socks5_username, ulen);
                *(buffer + 2 + ulen) = plen;
                memcpy(buffer + 2 + ulen + 1, socks5_password, plen);

                size_t len = 2 + ulen + 1 + plen;

                if (LOG_LEVEL < ANDROID_LOG_WARN) {
                    char *h = hex(buffer, len);
                    log_android(ANDROID_LOG_INFO, "%s sending SOCKS5 auth: %s",
                                tcp_socket_text(&diagnostic), h == NULL ? "(unavailable)" : h);
                    ng_free(h, __FILE__, __LINE__);
                }
                ssize_t sent = send(s->socket, buffer, len, MSG_NOSIGNAL);
                if (sent < 0) {
                    log_android(ANDROID_LOG_ERROR,
                                "%s send SOCKS5 connect error %d: %s",
                                tcp_socket_text(&diagnostic), errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_CONNECT) {
                uint8_t buffer[22];
                *(buffer + 0) = 5; // version
                *(buffer + 1) = 1; // TCP/IP stream connection
                *(buffer + 2) = 0; // reserved
                *(buffer + 3) = (uint8_t) (s->tcp.version == 4 ? 1 : 4);
                if (s->tcp.version == 4) {
                    memcpy(buffer + 4, &s->tcp.daddr.ip4, 4);
                    *((__be16 *) (buffer + 4 + 4)) = s->tcp.dest;
                } else {
                    memcpy(buffer + 4, &s->tcp.daddr.ip6, 16);
                    *((__be16 *) (buffer + 4 + 16)) = s->tcp.dest;
                }

                size_t len = (s->tcp.version == 4 ? 10 : 22);

                if (LOG_LEVEL < ANDROID_LOG_WARN) {
                    char *h = hex(buffer, len);
                    log_android(ANDROID_LOG_INFO, "%s sending SOCKS5 connect: %s",
                                tcp_socket_text(&diagnostic), h == NULL ? "(unavailable)" : h);
                    ng_free(h, __FILE__, __LINE__);
                }
                ssize_t sent = send(s->socket, buffer, len, MSG_NOSIGNAL);
                if (sent < 0) {
                    log_android(ANDROID_LOG_ERROR,
                                "%s send SOCKS5 connect error %d: %s",
                                tcp_socket_text(&diagnostic), errno, strerror(errno));
                    write_rst(args, &s->tcp);
                }

            } else if (s->tcp.socks5 == SOCKS5_CONNECTED) {
                s->tcp.remote_seq++; // remote SYN
                if (write_syn_ack(args, &s->tcp) >= 0) {
                    s->tcp.time = time(NULL);
                    s->tcp.local_seq++; // local SYN
                    s->tcp.state = TCP_SYN_RECV;
                }
            }
        } else {

            // Always forward data
            int fwd = 0;
            if (ev->events & EPOLLOUT) {
                // Forward data
                while (s->tcp.forward != NULL &&
                       s->tcp.forward->seq == s->tcp.remote_seq) {
                    log_android(ANDROID_LOG_DEBUG, "%s fwd %u...%u sent %u",
                                tcp_socket_text(&diagnostic),
                                s->tcp.forward->seq - s->tcp.remote_start,
                                s->tcp.forward->seq + s->tcp.forward->len - s->tcp.remote_start,
                                s->tcp.forward->sent);

                    ssize_t sent = send(s->socket,
                                        s->tcp.forward->data + s->tcp.forward->sent,
                                        s->tcp.forward->len - s->tcp.forward->sent,
                                        (unsigned int) (MSG_NOSIGNAL | (s->tcp.forward->psh
                                                                        ? 0
                                                                        : MSG_MORE)));
                    if (sent < 0) {
                        log_android(errno == EINTR || errno == EAGAIN ? ANDROID_LOG_DEBUG : ANDROID_LOG_ERROR,
                                    "%s send error %d: %s",
                                    tcp_socket_text(&diagnostic), errno, strerror(errno));
                        if (errno == EINTR || errno == EAGAIN) {
                            // Retry later
                            break;
                        } else {
                            write_rst(args, &s->tcp);
                            break;
                        }
                    } else if (sent == 0) {
                        break;
                    } else {
                        fwd = 1;
                        s->tcp.sent += sent;
                        s->tcp.forward->sent += sent;
                        s->tcp.queue_unsent -= sent;

                        if (s->tcp.forward->len == s->tcp.forward->sent) {
                            s->tcp.remote_seq = s->tcp.forward->seq + s->tcp.forward->sent;

                            struct segment *p = s->tcp.forward;
                            s->tcp.forward = s->tcp.forward->next;
                            s->tcp.queue_bytes -= p->len;
                            s->tcp.queue_segments--;
                            free_tcp_segment(&s->tcp, p);
                        } else {
                            log_android(ANDROID_LOG_INFO,
                                        "%s partial send %u/%u",
                                        tcp_socket_text(&diagnostic), s->tcp.forward->sent, s->tcp.forward->len);
                            break;
                        }
                    }
                }

                // Log data buffered
                struct segment *seg = s->tcp.forward;
                while (seg != NULL) {
                    log_android(ANDROID_LOG_INFO, "%s queued %u...%u sent %u",
                                tcp_socket_text(&diagnostic),
                                seg->seq - s->tcp.remote_start,
                                seg->seq + seg->len - s->tcp.remote_start,
                                seg->sent);
                    seg = seg->next;
                }
            }

            // Get receive window
            // Reads can only free upstream send space. Reuse the conservative
            // sample within this event, but refresh after every successful send.
            if (fwd || s->socket < 0)
                capacity->valid = false;
            if (!capacity->valid) {
                capacity->available = get_receive_buffer(s);
                capacity->valid = true;
            }
            uint32_t window = receive_window_for_buffer(s, capacity->available);
            uint32_t prev = s->tcp.recv_window;
            s->tcp.recv_window = window;
            if ((prev == 0 && window > 0) || (prev > 0 && window == 0))
                log_android(ANDROID_LOG_INFO, "%s recv window %u > %u",
                            tcp_socket_text(&diagnostic), prev, window);

            // Acknowledge forwarded data
            int finished = finish_tcp_input(args, s);
            if (finished < 0)
                return;
            if (!finished && (fwd || (prev == 0 && window > 0))) {
                if (write_ack(args, &s->tcp) >= 0)
                    s->tcp.time = time(NULL);
            }

            if (s->tcp.state == TCP_ESTABLISHED || s->tcp.state == TCP_CLOSE_WAIT) {
                // Check socket read
                // Send window can be changed in the mean time

                uint32_t send_window = get_send_window(&s->tcp);
                if (!s->tcp.upstream_eof && ((ev->events & EPOLLIN) || s->tcp.dns_response_ready) && send_window > 0) {
                    s->tcp.time = time(NULL);

                    if (ntohs(s->tcp.dest) == 53) {
                        read_dns_response(args, s, send_window);
                        return;
                    }

                    uint32_t buffer_size = (send_window > s->tcp.mss
                                            ? s->tcp.mss : send_window);
                    uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, buffer_size, "tcp socket");
                    if (buffer == NULL) {
                        write_rst(args, &s->tcp);
                        return;
                    }
                    ssize_t bytes = recv(s->socket, buffer, (size_t) buffer_size, 0);
                    if (bytes < 0) {
                        // Socket error
                        log_android(ANDROID_LOG_ERROR, "%s recv error %d: %s",
                                    tcp_socket_text(&diagnostic), errno, strerror(errno));

                        if (errno != EINTR && errno != EAGAIN)
                            write_rst(args, &s->tcp);
                    } else if (bytes == 0) {
                        log_android(ANDROID_LOG_INFO, "%s recv eof", tcp_socket_text(&diagnostic));

                        s->tcp.upstream_eof = true;
                        if (write_fin_ack(args, &s->tcp) >= 0) {
                            log_android(ANDROID_LOG_INFO, "%s FIN sent", tcp_socket_text(&diagnostic));
                            s->tcp.local_seq++; // local FIN
                            s->tcp.state = (s->tcp.fin_acked ? TCP_LAST_ACK : TCP_FIN_WAIT1);
                            if (s->tcp.fin_acked)
                                close_tcp_socket(s);
                        }

                    } else {
                        // Socket read data
                        log_android(ANDROID_LOG_DEBUG, "%s recv bytes %d", tcp_socket_text(&diagnostic), bytes);
                        s->tcp.received += bytes;

                        // Forward to tun
                        if (write_data(args, &s->tcp, buffer, (size_t) bytes) >= 0) {
                            s->tcp.local_seq += bytes;
                            s->tcp.unconfirmed++;
                        }
                    }
                    release_packet_buffer(args, buffer);
                }
            }
        }
    }

    if (s->tcp.state != oldstate || s->tcp.local_seq != oldlocal ||
        s->tcp.remote_seq != oldremote)
        log_android(ANDROID_LOG_DEBUG, "%s new state", tcp_socket_text(&diagnostic));
}

jboolean handle_tcp(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, int allowed, struct allowed *redirect,
                    const int epoll_fd) {
    size_t payload_offset = (size_t) (payload - pkt);
    if (payload_offset > length || length - payload_offset < sizeof(struct tcphdr))
        return 0;

    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    const struct tcphdr *tcphdr = (struct tcphdr *) payload;
    const size_t header_length = (size_t) tcphdr->doff * 4;
    if (header_length < sizeof(struct tcphdr) || header_length > length - payload_offset) {
        log_android(ANDROID_LOG_WARN, "TCP invalid header length %zu", header_length);
        return 0;
    }
    const size_t tcpoptlen = header_length - sizeof(struct tcphdr);
    const uint8_t *tcpoptions = payload + sizeof(struct tcphdr);
    const uint8_t *data = payload + header_length;
    if (length - (data - pkt) > UINT16_MAX)
        return 0;
    const uint16_t datalen = (const uint16_t) (length - (data - pkt));

    // Search session
    struct ng_session *cur = find_session(args->ctx, version, IPPROTO_TCP,
            version == 4 ? (const void *) &ip4->saddr : &ip6->ip6_src, tcphdr->source,
            version == 4 ? (const void *) &ip4->daddr : &ip6->ip6_dst, tcphdr->dest);

    struct tcp_packet_log diagnostic = {.pkt = pkt, .header = tcphdr,
            .tcp = cur == NULL ? NULL : &cur->tcp, .uid = cur == NULL ? uid : cur->tcp.uid,
            .datalen = datalen, .state = cur == NULL ? 0 : cur->tcp.state,
            .local = cur == NULL ? 0 : cur->tcp.local_seq - cur->tcp.local_start,
            .remote = cur == NULL ? 0 : cur->tcp.remote_seq - cur->tcp.remote_start,
            .acked = cur == NULL ? 0 : cur->tcp.acked - cur->tcp.local_start};
    log_android(tcphdr->urg ? ANDROID_LOG_INFO : ANDROID_LOG_DEBUG, "%s", tcp_packet_text(&diagnostic));

    // Drop URG data
    if (tcphdr->urg)
        return 1;

    // Check session
    if (cur == NULL) {
        if (tcphdr->syn) {
            // Decode options
            // http://www.iana.org/assignments/tcp-parameters/tcp-parameters.xhtml#tcp-parameters-1
            uint16_t mss = get_default_mss(version);
            uint8_t ws = 0;
            int optlen = tcpoptlen;
            const uint8_t *options = tcpoptions;
            while (optlen > 0) {
                uint8_t kind = *options;
                if (kind == 0) // End of options list
                    break;
                if (kind == 1) { // No operation has no length byte.
                    optlen--;
                    options++;
                    continue;
                }
                if (optlen < 2)
                    return 0;
                uint8_t len = options[1];
                if (len < 2 || len > optlen)
                    return 0;

                if (kind == 2) {
                    if (len != 4)
                        return 0;
                    mss = read_net16(options + 2);
                    if (mss == 0)
                        return 0;
                } else if (kind == 3) {
                    if (len != 3)
                        return 0;
                    ws = (options[2] > 14 ? 14 : options[2]);
                }
                optlen -= len;
                options += len;
            }

            log_android(ANDROID_LOG_INFO, "%s new session mss %u ws %u window %u",
                        tcp_packet_text(&diagnostic), mss, ws, (uint32_t) ntohs(tcphdr->window) << ws);

            // Register session
            struct ng_session *s = ng_malloc(sizeof(struct ng_session), "tcp session");
            if (s == NULL)
                return 0;
            s->protocol = IPPROTO_TCP;

            s->tcp.time = time(NULL);
            s->tcp.uid = uid;
            s->tcp.version = version;
            s->tcp.mss = mss;
            s->tcp.recv_scale = ws;
            s->tcp.send_scale = ws;
            s->tcp.send_window = ((uint32_t) ntohs(tcphdr->window)) << s->tcp.send_scale;
            s->tcp.recv_window = 0; // Setup failures can send a reset before opening the socket.
            s->tcp.unconfirmed = 0;
            s->tcp.remote_seq = ntohl(tcphdr->seq); // ISN remote
            s->tcp.local_seq = (uint32_t) rand(); // ISN local
            s->tcp.remote_start = s->tcp.remote_seq;
            s->tcp.local_start = s->tcp.local_seq;
            s->tcp.acked = 0;
            s->tcp.last_keep_alive = 0;
            s->tcp.window_probe_interval = 0;
            s->tcp.sent = 0;
            s->tcp.received = 0;

            if (version == 4) {
                s->tcp.saddr.ip4 = (__be32) ip4->saddr;
                s->tcp.daddr.ip4 = (__be32) ip4->daddr;
            } else {
                memcpy(&s->tcp.saddr.ip6, &ip6->ip6_src, 16);
                memcpy(&s->tcp.daddr.ip6, &ip6->ip6_dst, 16);
            }

            s->tcp.source = tcphdr->source;
            s->tcp.dest = tcphdr->dest;
            s->tcp.state = TCP_LISTEN;
            s->tcp.socks5 = SOCKS5_NONE;
            s->tcp.upstream_eof = false;
            s->tcp.fin_received = false;
            s->tcp.fin_acked = false;
            s->tcp.fin_seq = 0;
            s->tcp.dns_header_used = 0;
            s->tcp.dns_body_remaining = 0;
            s->tcp.dns_response = NULL;
            s->tcp.dns_response_size = s->tcp.dns_response_used = s->tcp.dns_response_sent = 0;
            s->tcp.dns_response_ready = false;
            s->tcp.dns_response_capacity = 0;
            s->tcp.dns_waiting = false;
            s->tcp.dns_memory_generation = 0;
            s->tcp.memory = &args->ctx->tcp_memory;
            s->tcp.memory_generation = s->tcp.memory->generation;
            s->tcp.memory_waiting = false;
            s->tcp.forward = NULL;
            s->tcp.queue_bytes = s->tcp.queue_unsent = s->tcp.queue_segments = 0;
            s->next = NULL;

            if (datalen) {
                log_android(ANDROID_LOG_INFO, "%s SYN data", tcp_packet_text(&diagnostic));
                s->tcp.forward = allocate_tcp_segment(&s->tcp, datalen,
                        ntohs(s->tcp.dest) == 53, "syn segment", "syn segment data");
                if (s->tcp.forward == NULL) {
                    ng_free(s, __FILE__, __LINE__);
                    return 0;
                }
                s->tcp.forward->seq = s->tcp.remote_seq;
                s->tcp.forward->len = datalen;
                s->tcp.forward->sent = 0;
                s->tcp.forward->psh = tcphdr->psh;
                memcpy(s->tcp.forward->data, data, datalen);
                s->tcp.forward->next = NULL;
                s->tcp.queue_bytes = s->tcp.queue_unsent = datalen;
                s->tcp.queue_segments = 1;
            }

            if (!allowed) {
                log_android(ANDROID_LOG_WARN, "%s resetting blocked session", tcp_packet_text(&diagnostic));

                write_rst(args, &s->tcp);
                write_connection_unreach(args, s, EHOSTDOWN);

                clear_tcp_data(&s->tcp);
                ng_free(s, __FILE__, __LINE__);
                return 0;
            }

            // Open socket
            int connect_error;
            s->socket = open_tcp_socket_with_error(args, &s->tcp, redirect, &connect_error);
            if (s->socket < 0) {
                // Report definitive connect failures instead of dropping every SYN retry.
                // Local setup and transient resource failures can still be retried.
                if (connect_error == ENETUNREACH || connect_error == EHOSTUNREACH ||
                    connect_error == ECONNREFUSED)
                    write_rst(args, &s->tcp);
                clear_tcp_data(&s->tcp);
                ng_free(s, __FILE__, __LINE__);
                return 0;
            }

            s->tcp.recv_window = get_receive_window(s);

            log_android(ANDROID_LOG_DEBUG, "TCP socket %d lport %d",
                        s->socket, get_local_port(s->socket));

            // Monitor events
            memset(&s->ev, 0, sizeof(struct epoll_event));
            s->ev.events = EPOLLOUT | EPOLLERR;
            s->ev.data.ptr = s;
            if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev))
                log_android(ANDROID_LOG_ERROR, "epoll add tcp error %d: %s",
                            errno, strerror(errno));

            add_session(args->ctx, s);
        } else {
            log_android(ANDROID_LOG_WARN, "%s unknown session", tcp_packet_text(&diagnostic));

            struct tcp_session rst;
            memset(&rst, 0, sizeof(struct tcp_session));
            rst.version = version;
            rst.local_seq = ntohl(tcphdr->ack_seq);
            rst.remote_seq = ntohl(tcphdr->seq) + datalen + (tcphdr->syn || tcphdr->fin ? 1 : 0);

            if (version == 4) {
                rst.saddr.ip4 = (__be32) ip4->saddr;
                rst.daddr.ip4 = (__be32) ip4->daddr;
            } else {
                memcpy(&rst.saddr.ip6, &ip6->ip6_src, 16);
                memcpy(&rst.daddr.ip6, &ip6->ip6_dst, 16);
            }

            rst.source = tcphdr->source;
            rst.dest = tcphdr->dest;

            write_rst(args, &rst);
            return 0;
        }
    } else {
        diagnostic.detailed = true;
        diagnostic.text[0] = 0;

        // Session found
        if (cur->tcp.state == TCP_CLOSING || cur->tcp.state == TCP_CLOSE) {
            log_android(ANDROID_LOG_INFO, "%s was closed", tcp_packet_text(&diagnostic));
            write_rst(args, &cur->tcp);
            return 0;
        } else {
            int oldstate = cur->tcp.state;
            uint32_t oldlocal = cur->tcp.local_seq;
            uint32_t oldremote = cur->tcp.remote_seq;

            log_android(ANDROID_LOG_DEBUG, "%s handling", tcp_packet_text(&diagnostic));

            if (!tcphdr->syn)
                cur->tcp.time = time(NULL);
            cur->tcp.send_window = ((uint32_t) ntohs(tcphdr->window)) << cur->tcp.send_scale;
            cur->tcp.unconfirmed = 0;

            // Do not change the order of the conditions

            // Queue data to forward
            if (datalen) {
                uint32_t end = ntohl(tcphdr->seq) + datalen;
                if (cur->tcp.fin_received && compare_u32(end, cur->tcp.fin_seq) > 0) {
                    log_android(ANDROID_LOG_ERROR, "%s data beyond app FIN", tcp_packet_text(&diagnostic));
                    write_rst(args, &cur->tcp);
                    return 0;
                }
                if (!cur->tcp.fin_acked && cur->socket < 0) {
                    log_android(ANDROID_LOG_ERROR, "%s data while local closed", tcp_packet_text(&diagnostic));
                    write_rst(args, &cur->tcp);
                    return 0;
                }
                if (!cur->tcp.fin_acked && !queue_tcp(args, tcphdr, LOG_LEVEL <= ANDROID_LOG_WARN ? tcp_packet_text(&diagnostic) : "", &cur->tcp, data, datalen))
                    return 0; // Let the peer retransmit without acknowledging lost data.
            }

            if (tcphdr->rst /* +ACK */) {
                // No sequence check
                // http://tools.ietf.org/html/rfc1122#page-87
                log_android(ANDROID_LOG_WARN, "%s received reset", tcp_packet_text(&diagnostic));
                cur->tcp.state = TCP_CLOSING;
                return 0;
            } else {
                if (!tcphdr->ack || ntohl(tcphdr->ack_seq) == cur->tcp.local_seq) {
                    if (tcphdr->syn) {
                        log_android(ANDROID_LOG_WARN, "%s repeated SYN", tcp_packet_text(&diagnostic));
                        // The socket is probably not opened yet

                    } else if (tcphdr->ack) {
                        cur->tcp.acked = ntohl(tcphdr->ack_seq);

                        if (cur->tcp.state == TCP_SYN_RECV)
                            cur->tcp.state = TCP_ESTABLISHED;

                        else if (cur->tcp.state == TCP_ESTABLISHED) {
                            // Do nothing
                        } else if (cur->tcp.state == TCP_LAST_ACK)
                            cur->tcp.state = TCP_CLOSING;

                        else if (cur->tcp.state == TCP_CLOSE_WAIT) {
                            // ACK after FIN/ACK
                        } else if (cur->tcp.state == TCP_FIN_WAIT1) {
                            cur->tcp.state = TCP_FIN_WAIT2;
                        } else if (cur->tcp.state == TCP_FIN_WAIT2) {
                            // The app can still send data after acknowledging our FIN.
                        } else {
                            log_android(ANDROID_LOG_ERROR, "%s invalid state", tcp_packet_text(&diagnostic));
                            return 0;
                        }
                    } else if (!tcphdr->fin) {
                        log_android(ANDROID_LOG_ERROR, "%s unknown packet", tcp_packet_text(&diagnostic));
                        return 0;
                    }
                } else {
                    uint32_t ack = ntohl(tcphdr->ack_seq);
                    if ((uint32_t) (ack + 1) == cur->tcp.local_seq) {
                        // Keep alive
                        if (cur->tcp.state == TCP_ESTABLISHED) {
                            int on = 1;
                            if (setsockopt(cur->socket, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on)))
                                log_android(ANDROID_LOG_ERROR,
                                            "%s setsockopt SO_KEEPALIVE error %d: %s",
                                            tcp_packet_text(&diagnostic), errno, strerror(errno));
                            else
                                log_android(ANDROID_LOG_INFO, "%s enabled keep alive", tcp_packet_text(&diagnostic));
                        } else
                            log_android(ANDROID_LOG_INFO, "%s keep alive", tcp_packet_text(&diagnostic));

                    } else if (compare_u32(ack, cur->tcp.local_seq) < 0) {
                        if (compare_u32(ack, cur->tcp.acked) <= 0)
                            log_android(
                                    ack == cur->tcp.acked ? ANDROID_LOG_WARN : ANDROID_LOG_ERROR,
                                    "%s repeated ACK %u/%u",
                                    tcp_packet_text(&diagnostic),
                                    ack - cur->tcp.local_start,
                                    cur->tcp.acked - cur->tcp.local_start);
                        else {
                            log_android(ANDROID_LOG_INFO, "%s previous ACK %u",
                                        tcp_packet_text(&diagnostic), ack - cur->tcp.local_seq);
                            cur->tcp.acked = ack;
                        }

                        if (!tcphdr->fin && !cur->tcp.fin_received)
                            return 1;
                    } else {
                        log_android(ANDROID_LOG_ERROR, "%s future ACK", tcp_packet_text(&diagnostic));
                        write_rst(args, &cur->tcp);
                        return 0;
                    }
                }
            }

            if (tcphdr->fin && cur->tcp.state != TCP_CLOSING) {
                uint32_t fin_seq = ntohl(tcphdr->seq) + datalen;
                if (cur->tcp.state != TCP_ESTABLISHED && cur->tcp.state != TCP_CLOSE_WAIT &&
                    cur->tcp.state != TCP_FIN_WAIT1 && cur->tcp.state != TCP_FIN_WAIT2 &&
                    cur->tcp.state != TCP_LAST_ACK)
                    return 0;
                if (cur->tcp.fin_received && cur->tcp.fin_seq != fin_seq)
                    return 0;
                if (!cur->tcp.fin_received) {
                    if (compare_u32(fin_seq, cur->tcp.remote_seq) < 0)
                        return 0;
                    cur->tcp.fin_received = true;
                    cur->tcp.fin_seq = fin_seq;
                    if (!cur->tcp.upstream_eof)
                        cur->tcp.state = TCP_CLOSE_WAIT;
                }
                if (cur->tcp.fin_acked)
                    write_ack(args, &cur->tcp);
            }
            int finished = finish_tcp_input(args, cur);
            if (finished < 0)
                return 0;
            if (!finished && datalen && cur->tcp.fin_acked && !tcphdr->fin &&
                cur->tcp.state != TCP_CLOSING)
                write_ack(args, &cur->tcp);

            if (cur->tcp.state != oldstate ||
                cur->tcp.local_seq != oldlocal ||
                cur->tcp.remote_seq != oldremote)
                log_android(ANDROID_LOG_INFO, "%s > %s loc %u rem %u",
                            tcp_packet_text(&diagnostic),
                            strstate(cur->tcp.state),
                            cur->tcp.local_seq - cur->tcp.local_start,
                            cur->tcp.remote_seq - cur->tcp.remote_start);
        }
    }

    return 1;
}

static void insert_tcp_gap(struct tcp_session *cur, struct segment *head) {
    size_t retained = head->len;
    uint32_t unsent = head->len;
    unsigned int segments = 1;
    size_t copied = 0;
    struct segment **link = &cur->forward;
    while (*link != NULL) {
        struct segment *q = *link;
        uint32_t offset = q->seq - head->seq;
        if (offset < head->len) {
            // Preserve previously accepted overlapping bytes. Any unacknowledged
            // suffix beyond this packet can be retransmitted after the gap closes.
            size_t start = (offset > copied ? offset : copied);
            size_t end = offset + q->len;
            if (end > head->len)
                end = head->len;
            if (start < end)
                memcpy(head->data + start, q->data + (start - offset), end - start);
            if (end > copied)
                copied = end;
            *link = q->next;
            free_tcp_segment(cur, q);
        } else if (retained + q->len > TCP_QUEUE_BYTES || segments >= TCP_QUEUE_SEGMENTS) {
            // Keep the nearest data and discard the furthest unacknowledged tail.
            struct tcp_session discarded = {.forward = q, .memory = cur->memory};
            *link = NULL;
            clear_tcp_data(&discarded);
            break;
        } else {
            retained += q->len;
            unsent += q->len - q->sent;
            segments++;
            link = &q->next;
        }
    }
    head->next = cur->forward;
    cur->forward = head;
    cur->queue_bytes = retained;
    cur->queue_unsent = unsent;
    cur->queue_segments = segments;
}

int queue_tcp(const struct arguments *args,
               const struct tcphdr *tcphdr,
               const char *session, struct tcp_session *cur,
               const uint8_t *data, uint16_t datalen) {
    uint32_t seq = ntohl(tcphdr->seq);
    if (cur->memory == NULL && args != NULL && args->ctx != NULL)
        cur->memory = &args->ctx->tcp_memory;
    if (compare_u32(seq, cur->remote_seq) < 0)
        log_android(ANDROID_LOG_WARN, "%s already forwarded %u..%u",
                    session,
                    seq - cur->remote_start, seq + datalen - cur->remote_start);
    else {
        struct segment *p = NULL;
        struct segment *s = cur->forward;
        while (s != NULL && compare_u32(s->seq, seq) < 0) {
            p = s;
            s = s->next;
        }

        // A shorter retransmission must not discard an existing unsent tail or
        // leave the partial-send offset beyond the replacement allocation.
        if (s != NULL && s->seq == seq && datalen <= s->len)
            return 1;

        uint32_t offset = seq - cur->remote_seq;
        size_t previous = (s != NULL && s->seq == seq ? s->len : 0);
        int fills_gap = (seq == cur->remote_seq && cur->forward != NULL &&
                         cur->forward->seq != seq);
        if (!fills_gap && (offset >= cur->recv_window || datalen > cur->recv_window - offset ||
            cur->queue_bytes - previous + datalen > TCP_QUEUE_BYTES ||
            (previous == 0 && cur->queue_segments >= TCP_QUEUE_SEGMENTS)))
            return 0; // No ACK: let the peer retry after the window reopens.

        if (s == NULL || compare_u32(s->seq, seq) > 0) {
            log_android(ANDROID_LOG_DEBUG, "%s queuing %u...%u",
                        session,
                        seq - cur->remote_start, seq + datalen - cur->remote_start);
            bool progress = fills_gap || ntohs(cur->dest) == 53;
            struct segment *n = allocate_tcp_segment(cur, datalen, progress,
                                                      "tcp segment", "tcp segment");
            if (n == NULL) {
                if (cur->memory != NULL && tcp_memory_window(cur) < datalen) {
                    cur->recv_window = 0;
                    cur->memory_generation = cur->memory->generation;
                    cur->memory_waiting = true;
                    write_ack(args, cur);
                }
                return 0;
            }
            n->seq = seq;
            n->len = datalen;
            n->sent = 0;
            n->psh = tcphdr->psh;
            memcpy(n->data, data, datalen);
            if (fills_gap)
                insert_tcp_gap(cur, n);
            else {
                n->next = s;
                if (p == NULL)
                    cur->forward = n;
                else
                    p->next = n;
                cur->queue_bytes += datalen;
                cur->queue_unsent += datalen;
                cur->queue_segments++;
            }
        } else {
            // Extend a retransmission without rewriting bytes already accepted.
            bool progress = seq == cur->remote_seq || ntohs(cur->dest) == 53;
            if (!reserve_tcp_memory(cur, datalen, 0, false, progress)) {
                cur->recv_window = 0;
                cur->memory_generation = cur->memory->generation;
                cur->memory_waiting = true;
                write_ack(args, cur);
                return 0;
            }
            uint8_t *replacement = ng_malloc(datalen, "tcp segment smaller");
            if (replacement == NULL) {
                release_tcp_memory(cur, datalen, 0, false);
                return 0;
            }
            memcpy(replacement, s->data, s->len);
            memcpy(replacement + s->len, data + s->len, datalen - s->len);
            ng_free(s->data, __FILE__, __LINE__);
            release_tcp_memory(cur, s->len, 0, false);
            s->data = replacement;
            cur->queue_bytes += datalen - s->len;
            cur->queue_unsent += datalen - s->len;
            s->len = datalen;
        }
    }
    return 1;
}

static int open_tcp_socket_with_error(const struct arguments *args,
                                     const struct tcp_session *cur,
                                     struct allowed *redirect, int *connect_error_out) {
    *connect_error_out = 0;
    int sock;
    int version;
    if (redirect == NULL) {
        version = cur->version;
    } else if (cur->version == 6 && strcmp(redirect->raddr, LOOPBACK_ADDRESS) == 0) {
        strcpy(redirect->raddr, LOOPBACK_ADDRESS_IPv6);
        version = 6;
    } else {
        version = (strstr(redirect->raddr, ":") == NULL ? 4 : 6);
    }

    // Get TCP socket
    if ((sock = socket(version == 4 ? PF_INET : PF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0)) < 0) {
        log_android(ANDROID_LOG_ERROR, "socket error %d: %s", errno, strerror(errno));
        return -1;
    }

    // Protect
    if (protect_socket(args, sock, cur->uid) < 0) {
        close(sock);
        return -1;
    }


    int on = 1;
    if (setsockopt(sock, SOL_TCP, TCP_NODELAY, &on, sizeof(on)) < 0)
        log_android(ANDROID_LOG_ERROR, "setsockopt TCP_NODELAY error %d: %s",
                    errno, strerror(errno));

    // Set non blocking
    int flags = fcntl(sock, F_GETFL, 0);
    if (flags < 0 || fcntl(sock, F_SETFL, flags | O_NONBLOCK) < 0) {
        log_android(ANDROID_LOG_ERROR, "fcntl socket O_NONBLOCK error %d: %s",
                    errno, strerror(errno));
        close(sock);
        return -1;
    }

    char dest[INET6_ADDRSTRLEN + 1];
    if (cur->version == 4) {
        inet_ntop(AF_INET, &cur->daddr.ip4, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &cur->daddr.ip6, dest, sizeof(dest));
    }

    // Build target address
    struct sockaddr_in addr4 = {0};
    struct sockaddr_in6 addr6 = {0};
    const char *route = (redirect == NULL ? "direct" : "redirect");
    if (redirect == NULL) {

        bool not_own_uid = cur->uid != own_uid;
        bool not_dns_request = ntohs(cur->dest) != 53;
        bool not_to_i2pd = !str_equal(I2PD_REDIRECT_ADDRESS, dest);
        bool not_to_localhost = !(str_equal(LOOPBACK_ADDRESS, dest) ||
                                  str_equal(LOOPBACK_ADDRESS_IPv6, dest));

        bool redirect_to_tor = false;
        if (not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
            && *tor_socks5_addr && tor_socks5_port) {
            redirect_to_tor = is_redirect_to_tor(args, cur->uid, dest, ntohs(cur->dest));
        }

        bool redirect_to_proxy = false;
        if (not_own_uid && not_dns_request && not_to_i2pd && not_to_localhost
            && *proxy_socks5_addr && proxy_socks5_port) {
            redirect_to_proxy = is_redirect_to_proxy(args, cur->uid, dest, ntohs(cur->dest));
        }

        if (redirect_to_tor) {
            route = "tor";
            log_android(ANDROID_LOG_INFO, "TCP%d SOCKS5 to %s/%u",
                        version, tor_socks5_addr, tor_socks5_port);

            if (version == 4) {
                addr4.sin_family = AF_INET;
                inet_pton(AF_INET, tor_socks5_addr, &addr4.sin_addr);
                addr4.sin_port = htons(tor_socks5_port);
            } else if (strcmp(tor_socks5_addr, LOOPBACK_ADDRESS) == 0) {
                addr6.sin6_family = AF_INET6;
                inet_pton(AF_INET6, LOOPBACK_ADDRESS_IPv6, &addr6.sin6_addr);
                addr6.sin6_port = htons(tor_socks5_port);
            } else {
                addr6.sin6_family = AF_INET6;
                inet_pton(AF_INET6, tor_socks5_addr, &addr6.sin6_addr);
                addr6.sin6_port = htons(tor_socks5_port);
            }
        } else if (redirect_to_proxy) {
            route = "proxy";
            log_android(ANDROID_LOG_INFO, "TCP%d SOCKS5 to %s/%u",
                        version, proxy_socks5_addr, proxy_socks5_port);

            if (version == 4) {
                addr4.sin_family = AF_INET;
                inet_pton(AF_INET, proxy_socks5_addr, &addr4.sin_addr);
                addr4.sin_port = htons(proxy_socks5_port);
            } else if (strcmp(proxy_socks5_addr, LOOPBACK_ADDRESS) == 0) {
                addr6.sin6_family = AF_INET6;
                inet_pton(AF_INET6, LOOPBACK_ADDRESS_MAPPED_IPv6, &addr6.sin6_addr);
                addr6.sin6_port = htons(proxy_socks5_port);
            } else {
                addr6.sin6_family = AF_INET6;
                inet_pton(AF_INET6, proxy_socks5_addr, &addr6.sin6_addr);
                addr6.sin6_port = htons(proxy_socks5_port);
            }
        } else {
            if (version == 4) {
                addr4.sin_family = AF_INET;
                addr4.sin_addr.s_addr = (__be32) cur->daddr.ip4;
                addr4.sin_port = cur->dest;
            } else {
                addr6.sin6_family = AF_INET6;
                memcpy(&addr6.sin6_addr, &cur->daddr.ip6, 16);
                addr6.sin6_port = cur->dest;
            }
        }
    } else {
        log_android(ANDROID_LOG_INFO, "TCP%d redirect to %s/%u",
                    version, redirect->raddr, redirect->rport);

        if (version == 4) {
            addr4.sin_family = AF_INET;
            inet_pton(AF_INET, redirect->raddr, &addr4.sin_addr);
            addr4.sin_port = htons(redirect->rport);
        } else {
            addr6.sin6_family = AF_INET6;
            inet_pton(AF_INET6, redirect->raddr, &addr6.sin6_addr);
            addr6.sin6_port = htons(redirect->rport);
        }
    }

    // Initiate connect
    int err = connect(sock,
                      (version == 4 ? (const struct sockaddr *) &addr4
                                    : (const struct sockaddr *) &addr6),
                      (socklen_t) (version == 4
                                   ? sizeof(struct sockaddr_in)
                                   : sizeof(struct sockaddr_in6)));
    if (err < 0 && errno != EINPROGRESS) {
        int connect_error = errno;
        *connect_error_out = connect_error;
        char source[INET6_ADDRSTRLEN + 1] = "?";
        char target[INET6_ADDRSTRLEN + 1] = "?";
        inet_ntop(cur->version == 4 ? AF_INET : AF_INET6,
                  cur->version == 4 ? (const void *) &cur->saddr.ip4
                                    : (const void *) &cur->saddr.ip6,
                  source, sizeof(source));
        inet_ntop(version == 4 ? AF_INET : AF_INET6,
                  version == 4 ? (const void *) &addr4.sin_addr
                               : (const void *) &addr6.sin6_addr,
                  target, sizeof(target));
        log_android(ANDROID_LOG_ERROR,
                    "TCP%d connect from %s/%u to %s/%u uid %d socket %d "
                    "route %s target IPv%d %s/%u error %d: %s",
                    cur->version, source, ntohs(cur->source), dest, ntohs(cur->dest),
                    cur->uid, sock, route, version, target,
                    ntohs(version == 4 ? addr4.sin_port : addr6.sin6_port),
                    connect_error, strerror(connect_error));
        close(sock);
        return -1;
    }

    return sock;
}

int open_tcp_socket(const struct arguments *args,
                    const struct tcp_session *cur, struct allowed *redirect) {
    int connect_error;
    return open_tcp_socket_with_error(args, cur, redirect, &connect_error);
}

int write_syn_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 1, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

int write_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 0, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

int write_data(const struct arguments *args, struct tcp_session *cur,
               const uint8_t *buffer, size_t length) {
    if (write_tcp(args, cur, buffer, length, 0, 1, 0, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

int write_fin_ack(const struct arguments *args, struct tcp_session *cur) {
    if (write_tcp(args, cur, NULL, 0, 0, 1, 1, 0) < 0) {
        cur->state = TCP_CLOSING;
        return -1;
    }
    return 0;
}

void write_rst(const struct arguments *args, struct tcp_session *cur) {
    // https://www.snellman.net/blog/archive/2016-02-01-tcp-rst/
    int ack = 0;
    if (cur->state == TCP_LISTEN) {
        ack = 1;
        cur->remote_seq++; // SYN
    }
    write_tcp(args, cur, NULL, 0, 0, ack, 0, 1);
    if (cur->state != TCP_CLOSE)
        cur->state = TCP_CLOSING;
}

ssize_t write_tcp(const struct arguments *args, const struct tcp_session *cur,
                  const uint8_t *data, size_t datalen,
                  int syn, int ack, int fin, int rst) {
    size_t len;
    u_int8_t *buffer;
    struct tcphdr *tcp;
    uint16_t csum;

    // Build packet
    int optlen = (syn ? 4 + 3 + 1 : 0);
    uint8_t *options;
    if (cur->version == 4) {
        len = sizeof(struct iphdr) + sizeof(struct tcphdr) + optlen + datalen;
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "tcp write4");
        if (buffer == NULL)
            return -1;
        struct iphdr *ip4 = (struct iphdr *) buffer;
        tcp = (struct tcphdr *) (buffer + sizeof(struct iphdr));
        options = buffer + sizeof(struct iphdr) + sizeof(struct tcphdr);
        if (datalen)
            memcpy(buffer + sizeof(struct iphdr) + sizeof(struct tcphdr) + optlen, data, datalen);

        // Build IP4 header
        memset(ip4, 0, sizeof(struct iphdr));
        ip4->version = 4;
        ip4->ihl = sizeof(struct iphdr) >> 2;
        ip4->tot_len = htons(len);
        ip4->ttl = IPDEFTTL;
        ip4->protocol = IPPROTO_TCP;
        ip4->saddr = cur->daddr.ip4;
        ip4->daddr = cur->saddr.ip4;

        // Calculate IP4 checksum
        ip4->check = ~calc_checksum(0, (uint8_t *) ip4, sizeof(struct iphdr));

        // Calculate TCP4 checksum
        struct ippseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ippseudo));
        pseudo.ippseudo_src.s_addr = (__be32) ip4->saddr;
        pseudo.ippseudo_dst.s_addr = (__be32) ip4->daddr;
        pseudo.ippseudo_p = ip4->protocol;
        pseudo.ippseudo_len = htons(sizeof(struct tcphdr) + optlen + datalen);

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ippseudo));
    } else {
        len = sizeof(struct ip6_hdr) + sizeof(struct tcphdr) + optlen + datalen;
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "tcp write 6");
        if (buffer == NULL)
            return -1;
        struct ip6_hdr *ip6 = (struct ip6_hdr *) buffer;
        tcp = (struct tcphdr *) (buffer + sizeof(struct ip6_hdr));
        options = buffer + sizeof(struct ip6_hdr) + sizeof(struct tcphdr);
        if (datalen)
            memcpy(buffer + sizeof(struct ip6_hdr) + sizeof(struct tcphdr) + optlen, data, datalen);

        // Build IP6 header
        memset(ip6, 0, sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_plen = htons(len - sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt = IPPROTO_TCP;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_hlim = IPDEFTTL;
        ip6->ip6_ctlun.ip6_un2_vfc = 0x60;
        memcpy(&(ip6->ip6_src), &cur->daddr.ip6, 16);
        memcpy(&(ip6->ip6_dst), &cur->saddr.ip6, 16);

        // Calculate TCP6 checksum
        struct ip6_hdr_pseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
        memcpy(&pseudo.ip6ph_src, &ip6->ip6_dst, 16);
        memcpy(&pseudo.ip6ph_dst, &ip6->ip6_src, 16);
        pseudo.ip6ph_len = ip6->ip6_ctlun.ip6_un1.ip6_un1_plen;
        pseudo.ip6ph_nxt = ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt;

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
    }


    // Build TCP header
    memset(tcp, 0, sizeof(struct tcphdr));
    tcp->source = cur->dest;
    tcp->dest = cur->source;
    tcp->seq = htonl(cur->local_seq);
    tcp->ack_seq = htonl((uint32_t) (cur->remote_seq));
    tcp->doff = (__u16) ((sizeof(struct tcphdr) + optlen) >> 2);
    tcp->syn = (__u16) syn;
    tcp->ack = (__u16) ack;
    tcp->fin = (__u16) fin;
    tcp->rst = (__u16) rst;
    tcp->window = htons(cur->recv_window >> cur->recv_scale);

    if (!tcp->ack)
        tcp->ack_seq = 0;

    // TCP options
    if (syn) {
        *(options) = 2; // MSS
        *(options + 1) = 4; // total option length
        *((uint16_t *) (options + 2)) = get_default_mss(cur->version);

        *(options + 4) = 3; // window scale
        *(options + 5) = 3; // total option length
        *(options + 6) = cur->recv_scale;

        *(options + 7) = 0; // End, padding
    }

    // Continue checksum
    csum = calc_checksum(csum, (uint8_t *) tcp, sizeof(struct tcphdr));
    csum = calc_checksum(csum, options, (size_t) optlen);
    // Buffered DNS output can start at an odd offset; checksum the aligned packet copy.
    csum = calc_checksum(csum, options + optlen, datalen);
    tcp->check = ~csum;

    struct log_address dest = {.version = cur->version, .address = &cur->daddr};

    // Send packet
    log_android(ANDROID_LOG_DEBUG,
                "TCP sending%s%s%s%s to tun %s/%u seq %u ack %u data %u",
                (tcp->syn ? " SYN" : ""),
                (tcp->ack ? " ACK" : ""),
                (tcp->fin ? " FIN" : ""),
                (tcp->rst ? " RST" : ""),
                format_log_address(&dest), ntohs(tcp->dest),
                ntohl(tcp->seq) - cur->local_start,
                ntohl(tcp->ack_seq) - cur->remote_start,
                datalen);

    ssize_t res = write(args->tun, buffer, len);

    if (res < 0) {
        log_android(ANDROID_LOG_ERROR, "TCP write%s%s%s%s data %d error %d: %s",
                    (tcp->syn ? " SYN" : ""),
                    (tcp->ack ? " ACK" : ""),
                    (tcp->fin ? " FIN" : ""),
                    (tcp->rst ? " RST" : ""),
                    datalen,
                    errno, strerror((errno)));
    }


    release_packet_buffer(args, buffer);

    if (res != len) {
        log_android(ANDROID_LOG_ERROR, "TCP write %d/%d", res, len);
        return -1;
    }

    return res;
}

#pragma clang diagnostic pop
