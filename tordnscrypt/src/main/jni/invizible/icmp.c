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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
*/

#include "invizible.h"

extern int own_uid;

int get_icmp_timeout(const struct icmp_session *u, int sessions, int maxsessions) {
    int timeout = ICMP_TIMEOUT;

    int scale = 100 - sessions * 100 / maxsessions;
    timeout = timeout * scale / 100;

    return timeout;
}

int check_icmp_session(const struct arguments *args, struct ng_session *s,
                       int sessions, int maxsessions) {
    time_t now = time(NULL);

    int timeout = get_icmp_timeout(&s->icmp, sessions, maxsessions);
    if (s->icmp.stop || s->icmp.time + timeout < now) {
        char source[INET6_ADDRSTRLEN + 1];
        char dest[INET6_ADDRSTRLEN + 1];
        if (s->icmp.version == 4) {
            inet_ntop(AF_INET, &s->icmp.saddr.ip4, source, sizeof(source));
            inet_ntop(AF_INET, &s->icmp.daddr.ip4, dest, sizeof(dest));
        } else {
            inet_ntop(AF_INET6, &s->icmp.saddr.ip6, source, sizeof(source));
            inet_ntop(AF_INET6, &s->icmp.daddr.ip6, dest, sizeof(dest));
        }
        log_android(ANDROID_LOG_WARN, "ICMP idle %d/%d sec stop %d from %s to %s",
                    now - s->icmp.time, timeout, s->icmp.stop, dest, source);

        if (close(s->socket))
            log_android(ANDROID_LOG_ERROR, "ICMP close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        s->socket = -1;

        return 1;
    }

    return 0;
}

// Connection refused
void write_connection_unreach(const struct arguments *args,
                              const struct ng_session *s,
                              const int serr) {
    struct icmp icmp;
    memset(&icmp, 0, sizeof(struct icmp));
    icmp.icmp_type = ICMP_UNREACH;
    if (serr == ECONNREFUSED)
        icmp.icmp_code = ICMP_UNREACH_PORT;
    else
        icmp.icmp_code = ICMP_UNREACH_HOST;
    icmp.icmp_cksum = 0;
    icmp.icmp_cksum = ~calc_checksum(0, (const uint8_t *) &icmp, 4);

    struct icmp_session sicmp;
    memset(&sicmp, 0, sizeof(struct icmp_session));

    if (s->protocol == IPPROTO_TCP) {
        sicmp.version = s->tcp.version;
        if (s->tcp.version == 4) {
            sicmp.saddr.ip4 = (__be32) s->tcp.saddr.ip4;
            sicmp.daddr.ip4 = (__be32) s->tcp.daddr.ip4;
        } else {
            memcpy(&sicmp.saddr.ip6, &s->tcp.saddr.ip6, 16);
            memcpy(&sicmp.daddr.ip6, &s->tcp.daddr.ip6, 16);
        }
        write_icmp(args, &sicmp, (uint8_t *) &icmp, 8);
    } else if (s->protocol == IPPROTO_UDP) {
        sicmp.version = s->udp.version;
        if (s->udp.version == 4) {
            sicmp.saddr.ip4 = (__be32) s->udp.saddr.ip4;
            sicmp.daddr.ip4 = (__be32) s->udp.daddr.ip4;
        } else {
            memcpy(&sicmp.saddr.ip6, &s->udp.saddr.ip6, 16);
            memcpy(&sicmp.daddr.ip6, &s->udp.daddr.ip6, 16);
        }
        write_icmp(args, &sicmp, (uint8_t *) &icmp, 8);
    }
}

void check_icmp_socket(const struct arguments *args, const struct epoll_event *ev) {
    struct ng_session *s = (struct ng_session *) ev->data.ptr;

    // Check socket error
    if (ev->events & EPOLLERR) {
        s->icmp.time = time(NULL);

        int serr = 0;
        socklen_t optlen = sizeof(int);
        int err = getsockopt(s->socket, SOL_SOCKET, SO_ERROR, &serr, &optlen);
        if (err < 0)
            log_android(ANDROID_LOG_ERROR, "ICMP getsockopt error %d: %s",
                        errno, strerror(errno));
        else if (serr)
            log_android(ANDROID_LOG_ERROR, "ICMP SO_ERROR %d: %s",
                        serr, strerror(serr));

        s->icmp.stop = 1;
    } else {
        // Check socket read
        if (ev->events & EPOLLIN) {
            s->icmp.time = time(NULL);

            uint16_t blen = (uint16_t) (s->icmp.version == 4 ? ICMP4_MAXMSG : ICMP6_MAXMSG);
            uint8_t *buffer = ng_malloc(blen, "icmp socket");
            ssize_t bytes = recv(s->socket, buffer, blen, 0);
            if (bytes < 0) {
                // Socket error
                log_android(ANDROID_LOG_WARN, "ICMP recv error %d: %s",
                            errno, strerror(errno));

                if (errno != EINTR && errno != EAGAIN)
                    s->icmp.stop = 1;
            } else if (bytes == 0) {
                log_android(ANDROID_LOG_WARN, "ICMP recv eof");
                s->icmp.stop = 1;

            } else {
                // Socket read data
                char dest[INET6_ADDRSTRLEN + 1];
                if (s->icmp.version == 4)
                    inet_ntop(AF_INET, &s->icmp.daddr.ip4, dest, sizeof(dest));
                else
                    inet_ntop(AF_INET6, &s->icmp.daddr.ip6, dest, sizeof(dest));

                // cur->id should be equal to icmp->icmp_id
                // but for some unexplained reason this is not the case
                // some bits seems to be set extra
                struct icmp *icmp = (struct icmp *) buffer;
                log_android(
                        s->icmp.id == icmp->icmp_id ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
                        "ICMP recv bytes %d from %s for tun type %d code %d id %x/%x seq %d",
                        bytes, dest,
                        icmp->icmp_type, icmp->icmp_code,
                        s->icmp.id, icmp->icmp_id, icmp->icmp_seq);

                // restore original ID
                icmp->icmp_id = s->icmp.id;
                uint16_t csum = 0;
                if (s->icmp.version == 6) {
                    // Untested
                    struct ip6_hdr_pseudo pseudo;
                    memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
                    memcpy(&pseudo.ip6ph_src, &s->icmp.daddr.ip6, 16);
                    memcpy(&pseudo.ip6ph_dst, &s->icmp.saddr.ip6, 16);
                    pseudo.ip6ph_len = bytes - sizeof(struct ip6_hdr);
                    pseudo.ip6ph_nxt = IPPROTO_ICMPV6;
                    csum = calc_checksum(
                            0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
                }
                icmp->icmp_cksum = 0;
                icmp->icmp_cksum = ~calc_checksum(csum, buffer, (size_t) bytes);

                // Forward to tun
                if (write_icmp(args, &s->icmp, buffer, (size_t) bytes) < 0)
                    s->icmp.stop = 1;
            }
            ng_free(buffer, __FILE__, __LINE__);
        }
    }
}

jboolean is_icmp_supported(const uint8_t *pkt,
                     const uint8_t *payload) {
    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    struct icmp *icmp = (struct icmp *) payload;

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    if (version == 4) {
        inet_ntop(AF_INET, &ip4->saddr, source, sizeof(source));
        inet_ntop(AF_INET, &ip4->daddr, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &ip6->ip6_src, source, sizeof(source));
        inet_ntop(AF_INET6, &ip6->ip6_dst, dest, sizeof(dest));
    }

    if (icmp->icmp_type != ICMP_ECHO && icmp->icmp_type != 128 /* ICMP_ECHOV6 */) {
        return 0;
    }

    return 1;
}

jboolean handle_icmp(const struct arguments *args,
                     const uint8_t *pkt, size_t length,
                     const uint8_t *payload,
                     int uid,
                     const int epoll_fd) {
    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    struct icmp *icmp = (struct icmp *) payload;
    size_t icmplen = length - (payload - pkt);

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    if (version == 4) {
        inet_ntop(AF_INET, &ip4->saddr, source, sizeof(source));
        inet_ntop(AF_INET, &ip4->daddr, dest, sizeof(dest));
    } else {
        inet_ntop(AF_INET6, &ip6->ip6_src, source, sizeof(source));
        inet_ntop(AF_INET6, &ip6->ip6_dst, dest, sizeof(dest));
    }

    // Search session
    struct ng_session *cur = args->ctx->ng_session;
    while (cur != NULL &&
           !((cur->protocol == IPPROTO_ICMP || cur->protocol == IPPROTO_ICMPV6) &&
             !cur->icmp.stop && cur->icmp.version == version &&
                   cur->icmp.id == icmp->icmp_id &&
             (version == 4 ? cur->icmp.saddr.ip4 == ip4->saddr &&
                             cur->icmp.daddr.ip4 == ip4->daddr
                           : memcmp(&cur->icmp.saddr.ip6, &ip6->ip6_src, 16) == 0 &&
                             memcmp(&cur->icmp.daddr.ip6, &ip6->ip6_dst, 16) == 0)))
        cur = cur->next;

    // Create new session if needed
    if (cur == NULL) {
        log_android(ANDROID_LOG_INFO, "ICMP new session from %s to %s", source, dest);

        // Register session
        struct ng_session *s = ng_malloc(sizeof(struct ng_session), "icmp session");
        s->protocol = (uint8_t) (version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);

        s->icmp.time = time(NULL);
        s->icmp.uid = uid;
        s->icmp.version = version;

        if (version == 4) {
            s->icmp.saddr.ip4 = (__be32) ip4->saddr;
            s->icmp.daddr.ip4 = (__be32) ip4->daddr;
        } else {
            memcpy(&s->icmp.saddr.ip6, &ip6->ip6_src, 16);
            memcpy(&s->icmp.daddr.ip6, &ip6->ip6_dst, 16);
        }

        s->icmp.id = icmp->icmp_id; // store original ID

        s->icmp.stop = 0;
        s->next = NULL;

        // Open UDP socket
        s->socket = open_icmp_socket(args, &s->icmp);
        if (s->socket < 0) {
            ng_free(s, __FILE__, __LINE__);
            return 0;
        }

        log_android(ANDROID_LOG_DEBUG, "ICMP socket %d id %x", s->socket, s->icmp.id);

        // Monitor events
        memset(&s->ev, 0, sizeof(struct epoll_event));
        s->ev.events = EPOLLIN | EPOLLERR;
        s->ev.data.ptr = s;
        if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev))
            log_android(ANDROID_LOG_ERROR, "epoll add icmp error %d: %s", errno, strerror(errno));

        s->next = args->ctx->ng_session;
        args->ctx->ng_session = s;

        cur = s;
    }

    // Modify ID
    // http://lwn.net/Articles/443051/
    icmp->icmp_id = ~icmp->icmp_id;
    uint16_t csum = 0;
    if (version == 6) {
        // Untested
        struct ip6_hdr_pseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
        memcpy(&pseudo.ip6ph_src, &ip6->ip6_dst, 16);
        memcpy(&pseudo.ip6ph_dst, &ip6->ip6_src, 16);
        pseudo.ip6ph_len = ip6->ip6_ctlun.ip6_un1.ip6_un1_plen;
        pseudo.ip6ph_nxt = ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt;
        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
    }
    icmp->icmp_cksum = 0;
    icmp->icmp_cksum = ~calc_checksum(csum, (uint8_t *) icmp, icmplen);

    log_android(ANDROID_LOG_WARN,
                "ICMP forward from tun %s to %s type %d code %d id %x seq %d data %d",
                source, dest,
                icmp->icmp_type, icmp->icmp_code, icmp->icmp_id, icmp->icmp_seq, icmplen);

    cur->icmp.time = time(NULL);

    struct sockaddr_in server4;
    struct sockaddr_in6 server6;
    if (version == 4) {
        server4.sin_family = AF_INET;
        server4.sin_addr.s_addr = (__be32) ip4->daddr;
        server4.sin_port = 0;
    } else {
        server6.sin6_family = AF_INET6;
        memcpy(&server6.sin6_addr, &ip6->ip6_dst, 16);
        server6.sin6_port = 0;
    }

    // Send raw ICMP message
    if (sendto(cur->socket, icmp, (socklen_t) icmplen, MSG_NOSIGNAL,
               (version == 4 ? (const struct sockaddr *) &server4
                             : (const struct sockaddr *) &server6),
               (socklen_t) (version == 4 ? sizeof(server4) : sizeof(server6))) != icmplen) {
        log_android(ANDROID_LOG_ERROR, "ICMP sendto error %d: %s", errno, strerror(errno));
        if (errno != EINTR && errno != EAGAIN) {
            cur->icmp.stop = 1;
            return 0;
        }
    }

    return 1;
}

int open_icmp_socket(const struct arguments *args, const struct icmp_session *cur) {
    int sock;

    // Get UDP socket
    sock = socket(cur->version == 4 ? PF_INET : PF_INET6,
                  SOCK_DGRAM | SOCK_CLOEXEC,
                  cur->version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    if (sock < 0) {
        log_android(ANDROID_LOG_ERROR, "ICMP socket error %d: %s", errno, strerror(errno));
        return -1;
    }

    // Protect socket
    if (protect_socket(args, sock, cur->uid) < 0)
        return -1;


    return sock;
}

ssize_t write_icmp(const struct arguments *args, const struct icmp_session *cur,
                   uint8_t *data, size_t datalen) {
    size_t len;
    u_int8_t *buffer;
    struct icmp *icmp = (struct icmp *) data;
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];

    // Build packet
    if (cur->version == 4) {
        len = sizeof(struct iphdr) + datalen;
        buffer = ng_malloc(len, "icmp write4");
        struct iphdr *ip4 = (struct iphdr *) buffer;
        if (datalen)
            memcpy(buffer + sizeof(struct iphdr), data, datalen);

        // Build IP4 header
        memset(ip4, 0, sizeof(struct iphdr));
        ip4->version = 4;
        ip4->ihl = sizeof(struct iphdr) >> 2;
        ip4->tot_len = htons(len);
        ip4->ttl = IPDEFTTL;
        ip4->protocol = IPPROTO_ICMP;
        ip4->saddr = cur->daddr.ip4;
        ip4->daddr = cur->saddr.ip4;

        // Calculate IP4 checksum
        ip4->check = ~calc_checksum(0, (uint8_t *) ip4, sizeof(struct iphdr));
    } else {
        len = sizeof(struct ip6_hdr) + datalen;
        buffer = ng_malloc(len, "icmp write6");
        struct ip6_hdr *ip6 = (struct ip6_hdr *) buffer;
        if (datalen)
            memcpy(buffer + sizeof(struct ip6_hdr), data, datalen);

        // Build IP6 header
        memset(ip6, 0, sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_flow = 0;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_plen = htons(len - sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt = IPPROTO_ICMPV6;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_hlim = IPDEFTTL;
        ip6->ip6_ctlun.ip6_un2_vfc = IPV6_VERSION;
        memcpy(&(ip6->ip6_src), &cur->daddr.ip6, 16);
        memcpy(&(ip6->ip6_dst), &cur->saddr.ip6, 16);
    }

    inet_ntop(cur->version == 4 ? AF_INET : AF_INET6,
              cur->version == 4 ? (const void *) &cur->saddr.ip4 : (const void *) &cur->saddr.ip6,
              source, sizeof(source));
    inet_ntop(cur->version == 4 ? AF_INET : AF_INET6,
              cur->version == 4 ? (const void *) &cur->daddr.ip4 : (const void *) &cur->daddr.ip6,
              dest, sizeof(dest));

    // Send raw ICMP message
    log_android(ANDROID_LOG_WARN,
                "ICMP sending to tun %d from %s to %s data %u type %d code %d id %x seq %d",
                args->tun, dest, source, datalen,
                icmp->icmp_type, icmp->icmp_code, icmp->icmp_id, icmp->icmp_seq);

    ssize_t res = write(args->tun, buffer, len);

    ng_free(buffer, __FILE__, __LINE__);

    if (res != len) {
        log_android(ANDROID_LOG_ERROR, "write %d/%d", res, len);
        return -1;
    }

    return res;
}

#pragma clang diagnostic pop