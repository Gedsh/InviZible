#pragma clang diagnostic push
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

struct ng_session *find_udp_session(const struct arguments *args, const uint8_t *pkt,
                                    const uint8_t *payload) {
    const uint8_t version = pkt[0] >> 4;
    const struct iphdr *ip4 = (const struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (const struct ip6_hdr *) pkt;
    const struct udphdr *udp = (const struct udphdr *) payload;
    struct ng_session *cur = args->ctx->ng_session;
    while (cur != NULL &&
           !(cur->protocol == IPPROTO_UDP && cur->udp.version == version &&
             cur->udp.source == udp->source && cur->udp.dest == udp->dest &&
             (version == 4 ? cur->udp.saddr.ip4 == ip4->saddr && cur->udp.daddr.ip4 == ip4->daddr
                           : memcmp(&cur->udp.saddr.ip6, &ip6->ip6_src, 16) == 0 &&
                             memcmp(&cur->udp.daddr.ip6, &ip6->ip6_dst, 16) == 0)))
        cur = cur->next;
    return cur;
}

void clear(struct context *ctx) {
    struct ng_session *s = ctx->ng_session;
    while (s != NULL) {
        if (s->socket >= 0 && close(s->socket))
            log_android(ANDROID_LOG_ERROR, "close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        if (s->protocol == IPPROTO_TCP)
            clear_tcp_data(&s->tcp);
        struct ng_session *p = s;
        s = s->next;
        ng_free(p, __FILE__, __LINE__);
    }
    ctx->ng_session = NULL;
}

void *handle_events(void *a) {
    struct arguments *args = (struct arguments *) a;
    // Reads finish processing before the next event. Keep output separate from
    // borrowed input, and never use either scratch buffer for retained session data.
    struct packet_buffers buffers = {0};
    args->buffers = &buffers;
    log_android(ANDROID_LOG_WARN, "Start events tun=%d", args->tun);

    // Get max number of sessions
    int maxsessions = SESSION_MAX;
    struct rlimit rlim;
    if (getrlimit(RLIMIT_NOFILE, &rlim))
        log_android(ANDROID_LOG_WARN, "getrlimit error %d: %s", errno, strerror(errno));
    else {
        maxsessions = (int) (rlim.rlim_cur * SESSION_LIMIT / 100);
        if (maxsessions > SESSION_MAX)
            maxsessions = SESSION_MAX;
        log_android(ANDROID_LOG_WARN, "getrlimit soft %d hard %d max sessions %d",
                    rlim.rlim_cur, rlim.rlim_max, maxsessions);
    }

    // Terminate existing sessions not allowed anymore
    check_allowed(args);

    // Open epoll file
    int epoll_fd = epoll_create(1);
    if (epoll_fd < 0) {
        log_android(ANDROID_LOG_ERROR, "epoll create error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll create error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Monitor stop events
    struct epoll_event ev_pipe;
    memset(&ev_pipe, 0, sizeof(struct epoll_event));
    ev_pipe.events = EPOLLIN | EPOLLERR;
    ev_pipe.data.ptr = &ev_pipe;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, args->ctx->pipefds[0], &ev_pipe)) {
        log_android(ANDROID_LOG_ERROR, "epoll add pipe error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll add pipe error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Monitor tun events
    struct epoll_event ev_tun;
    memset(&ev_tun, 0, sizeof(struct epoll_event));
    ev_tun.events = EPOLLIN | EPOLLERR;
    ev_tun.data.ptr = NULL;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, args->tun, &ev_tun)) {
        log_android(ANDROID_LOG_ERROR, "epoll add tun error %d: %s", errno, strerror(errno));
        report_exit(args, "epoll add tun error %d: %s", errno, strerror(errno));
        args->ctx->stopping = 1;
    }

    // Loop
    long long last_check = 0;
    while (!args->ctx->stopping) {
        log_android(ANDROID_LOG_DEBUG, "Loop");

        int recheck = 0;
        int timeout = EPOLL_TIMEOUT;

        // Count sessions
        int isessions = 0;
        int usessions = 0;
        int tsessions = 0;
        struct ng_session *s = args->ctx->ng_session;
        while (s != NULL) {
            if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
                if (!s->icmp.stop)
                    isessions++;
            } else if (s->protocol == IPPROTO_UDP) {
                if (s->udp.state == UDP_ACTIVE)
                    usessions++;
            } else if (s->protocol == IPPROTO_TCP) {
                if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE)
                    tsessions++;
                if (s->socket >= 0)
                    recheck = recheck | monitor_tcp_session(args, s, epoll_fd);
            }
            s = s->next;
        }
        int sessions = isessions + usessions + tsessions;

        // Check sessions
        long long ms = get_ms();
        if (ms - last_check > EPOLL_MIN_CHECK) {
            last_check = ms;

            time_t now = time(NULL);
            struct ng_session *sl = NULL;
            unsigned int closed_sessions = 0;
            s = args->ctx->ng_session;
            while (s != NULL) {
                int del = 0;
                if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
                    del = check_icmp_session(args, s, sessions, maxsessions);
                    if (!s->icmp.stop && !del) {
                        int stimeout = s->icmp.time +
                                       get_icmp_timeout(&s->icmp, sessions, maxsessions) - now + 1;
                        if (stimeout > 0 && stimeout < timeout)
                            timeout = stimeout;
                    }
                } else if (s->protocol == IPPROTO_UDP) {
                    del = check_udp_session(args, s, sessions, maxsessions);
                    if (!del) {
                        int lifetime = (s->udp.state == UDP_CLOSED || s->udp.state == UDP_BLOCKED
                                ? UDP_KEEP_TIMEOUT : get_udp_timeout(&s->udp, sessions, maxsessions));
                        int stimeout = s->udp.time + lifetime - now + 1;
                        if (stimeout < 1)
                            stimeout = 1;
                        if (stimeout < timeout)
                            timeout = stimeout;
                    }
                } else if (s->protocol == IPPROTO_TCP) {
                    del = check_tcp_session(args, s, sessions, maxsessions);
                    if (!del) {
                        int lifetime = (s->tcp.state == TCP_CLOSE
                                ? TCP_KEEP_TIMEOUT : get_tcp_timeout(&s->tcp, sessions, maxsessions));
                        int stimeout = s->tcp.time + lifetime - now + 1;
                        if (stimeout < 1)
                            stimeout = 1;
                        if (stimeout < timeout)
                            timeout = stimeout;
                    }
                }

                // New sessions are prepended. Retain the most recently created
                // closed records, after closing sockets and accounting usage.
                // Evict before polling so no current event batch refers to them.
                if (!del && ((s->protocol == IPPROTO_UDP && s->udp.state == UDP_CLOSED) ||
                             (s->protocol == IPPROTO_TCP && s->tcp.state == TCP_CLOSE))) {
                    if (++closed_sessions > SESSION_CLOSED_MAX)
                        del = 1;
                }

                if (del) {
                    if (sl == NULL)
                        args->ctx->ng_session = s->next;
                    else
                        sl->next = s->next;

                    struct ng_session *c = s;
                    s = s->next;
                    if (c->protocol == IPPROTO_TCP)
                        clear_tcp_data(&c->tcp);
                    ng_free(c, __FILE__, __LINE__);
                } else {
                    sl = s;
                    s = s->next;
                }
            }
        } else {
            recheck = 1;
            log_android(ANDROID_LOG_DEBUG, "Skipped session checks");
        }

        log_android(ANDROID_LOG_DEBUG,
                    "sessions ICMP %d UDP %d TCP %d max %d/%d timeout %d recheck %d",
                    isessions, usessions, tsessions, sessions, maxsessions, timeout, recheck);

        // Poll
        struct epoll_event ev[EPOLL_EVENTS];
        int ready = epoll_wait(epoll_fd, ev, EPOLL_EVENTS,
                               recheck ? EPOLL_MIN_CHECK : timeout * 1000);

        if (ready < 0) {
            if (errno == EINTR) {
                log_android(ANDROID_LOG_DEBUG, "epoll interrupted tun %d", args->tun);
                continue;
            } else {
                log_android(ANDROID_LOG_ERROR,
                            "epoll tun %d error %d: %s",
                            args->tun, errno, strerror(errno));
                report_exit(args, "epoll tun %d error %d: %s",
                            args->tun, errno, strerror(errno));
                break;
            }
        }

        if (ready == 0)
            log_android(ANDROID_LOG_DEBUG, "epoll timeout");
        else {

            if (pthread_mutex_lock(&args->ctx->lock))
                log_android(ANDROID_LOG_ERROR, "pthread_mutex_lock failed");

            int error = 0;

            for (int i = 0; i < ready; i++) {
                if (ev[i].data.ptr == &ev_pipe) {
                    // Check pipe
                    uint8_t buffer[1];
                    if (read(args->ctx->pipefds[0], buffer, 1) < 0)
                        log_android(ANDROID_LOG_WARN, "Read pipe error %d: %s",
                                    errno, strerror(errno));
                    else
                        log_android(ANDROID_LOG_WARN, "Read pipe");

                } else if (ev[i].data.ptr == NULL) {
                    // Check upstream
                    log_android(ANDROID_LOG_DEBUG, "epoll ready %d/%d in %d out %d err %d hup %d",
                                i, ready,
                                (ev[i].events & EPOLLIN) != 0,
                                (ev[i].events & EPOLLOUT) != 0,
                                (ev[i].events & EPOLLERR) != 0,
                                (ev[i].events & EPOLLHUP) != 0);

                    int count = 0;
                    while (count < TUN_YIELD && !error && !args->ctx->stopping &&
                           is_readable(args->tun)) {
                        count++;
                        if (check_tun(args, &ev[i], epoll_fd, sessions, maxsessions) < 0)
                            error = 1;
                    }

                } else {
                    // Check downstream
                    log_android(ANDROID_LOG_DEBUG,
                                "epoll ready %d/%d in %d out %d err %d hup %d prot %d sock %d",
                                i, ready,
                                (ev[i].events & EPOLLIN) != 0,
                                (ev[i].events & EPOLLOUT) != 0,
                                (ev[i].events & EPOLLERR) != 0,
                                (ev[i].events & EPOLLHUP) != 0,
                                ((struct ng_session *) ev[i].data.ptr)->protocol,
                                ((struct ng_session *) ev[i].data.ptr)->socket);

                    struct ng_session *session = (struct ng_session *) ev[i].data.ptr;
                    if (session->protocol == IPPROTO_ICMP ||
                        session->protocol == IPPROTO_ICMPV6)
                        check_icmp_socket(args, &ev[i]);
                    else if (session->protocol == IPPROTO_UDP) {
                        int count = 0;
                        do {
                            count++;
                            check_udp_socket(args, &ev[i]);
                        } while (count < UDP_YIELD && !args->ctx->stopping &&
                                 !(ev[i].events & EPOLLERR) && (ev[i].events & EPOLLIN) &&
                                 is_readable(session->socket));
                    } else if (session->protocol == IPPROTO_TCP) {
                        int count = 0;
                        do {
                            count++;
                            check_tcp_socket(args, &ev[i], epoll_fd);
                        } while (count < TCP_YIELD && !args->ctx->stopping &&
                                 !session->tcp.upstream_eof &&
                                 !(ev[i].events & EPOLLERR) && (ev[i].events & EPOLLIN) &&
                                 is_readable(session->socket));
                    }
                }

                if (error)
                    break;
            }

            if (pthread_mutex_unlock(&args->ctx->lock))
                log_android(ANDROID_LOG_ERROR, "pthread_mutex_unlock failed");

            if (error)
                break;
        }
    }

    // Close epoll file
    if (epoll_fd >= 0 && close(epoll_fd))
        log_android(ANDROID_LOG_ERROR,
                    "epoll close error %d: %s", errno, strerror(errno));

    // Cleanup
    clear_packet_buffers(&buffers);
    args->buffers = NULL;
    log_android(ANDROID_LOG_WARN, "Stopped events tun=%d", args->tun);
    ng_free(args, __FILE__, __LINE__);
    return NULL;
}

void check_allowed(const struct arguments *args) {
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];

    struct ng_session *l = NULL;
    struct ng_session *s = args->ctx->ng_session;
    while (s != NULL) {
        if (s->protocol == IPPROTO_ICMP || s->protocol == IPPROTO_ICMPV6) {
            if (!s->icmp.stop) {
                if (s->icmp.version == 4) {
                    inet_ntop(AF_INET, &s->icmp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->icmp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->icmp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->icmp.daddr.ip6, dest, sizeof(dest));
                }

                jobject objPacket = create_packet(
                        args, s->icmp.version, IPPROTO_ICMP, "",
                        source, 0, dest, 0, "", s->icmp.uid, 0);
                if (is_address_allowed(args, objPacket) == NULL) {
                    s->icmp.stop = 1;
                    log_android(ANDROID_LOG_WARN, "ICMP terminate %d uid %d",
                                s->socket, s->icmp.uid);
                }
            }

        } else if (s->protocol == IPPROTO_UDP) {
            if (s->udp.state == UDP_ACTIVE) {
                if (s->udp.version == 4) {
                    inet_ntop(AF_INET, &s->udp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->udp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->udp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->udp.daddr.ip6, dest, sizeof(dest));
                }

                jobject objPacket = create_packet(
                        args, s->udp.version, IPPROTO_UDP, "",
                        source, ntohs(s->udp.source), dest, ntohs(s->udp.dest), "", s->udp.uid, 0);
                if (is_address_allowed(args, objPacket) == NULL) {
                    s->udp.state = UDP_FINISHING;
                    log_android(ANDROID_LOG_WARN, "UDP terminate session socket %d uid %d",
                                s->socket, s->udp.uid);
                }
            } else if (s->udp.state == UDP_BLOCKED) {
                log_android(ANDROID_LOG_WARN, "UDP remove blocked session uid %d", s->udp.uid);

                if (l == NULL)
                    args->ctx->ng_session = s->next;
                else
                    l->next = s->next;

                struct ng_session *c = s;
                s = s->next;
                ng_free(c, __FILE__, __LINE__);
                continue;
            }

        } else if (s->protocol == IPPROTO_TCP) {
            if (s->tcp.state != TCP_CLOSING && s->tcp.state != TCP_CLOSE) {
                if (s->tcp.version == 4) {
                    inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
                }

                jobject objPacket = create_packet(
                        args, s->tcp.version, IPPROTO_TCP, "",
                        source, ntohs(s->tcp.source), dest, ntohs(s->tcp.dest), "", s->tcp.uid, 0);
                if (is_address_allowed(args, objPacket) == NULL) {
                    write_rst(args, &s->tcp);
                    log_android(ANDROID_LOG_WARN, "TCP terminate socket %d uid %d",
                                s->socket, s->tcp.uid);
                }
            }

        }

        l = s;
        s = s->next;
    }
}


#pragma clang diagnostic pop
