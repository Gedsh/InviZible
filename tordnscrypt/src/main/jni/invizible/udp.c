#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"
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

extern int own_uid;
extern int tor_dns_port;
extern bool bypass_lan;

int get_udp_timeout(const struct udp_session *u, int sessions, int maxsessions) {
    int timeout = (ntohs(u->dest) == 53 ? UDP_TIMEOUT_53 : UDP_TIMEOUT_ANY);

    int scale = 100 - sessions * 100 / maxsessions;
    timeout = timeout * scale / 100;

    return timeout;
}

int check_udp_session(const struct arguments *args, struct ng_session *s,
                      int sessions, int maxsessions) {
    time_t now = time(NULL);

    struct log_address source = {.version = s->udp.version, .address = &s->udp.saddr};
    struct log_address dest = {.version = s->udp.version, .address = &s->udp.daddr};

    // Check session timeout
    int timeout = get_udp_timeout(&s->udp, sessions, maxsessions);
    if (s->udp.state == UDP_ACTIVE && s->udp.time + timeout < now) {
        log_android(ANDROID_LOG_WARN, "UDP idle %jd/%d sec state %d from %s/%u to %s/%u",
                    (intmax_t) (now - s->udp.time), timeout, s->udp.state,
                    format_log_address(&source), ntohs(s->udp.source), format_log_address(&dest), ntohs(s->udp.dest));
        s->udp.state = UDP_FINISHING;
    }

    // Check finished sessions
    if (s->udp.state == UDP_FINISHING) {
        log_android(ANDROID_LOG_INFO, "UDP close from %s/%u to %s/%u socket %d",
                    format_log_address(&source), ntohs(s->udp.source), format_log_address(&dest), ntohs(s->udp.dest), s->socket);

        if (close(s->socket))
            log_android(ANDROID_LOG_ERROR, "UDP close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        s->socket = -1;

        s->udp.time = time(NULL);
        s->udp.state = UDP_CLOSED;
    }

    if (s->udp.state == UDP_CLOSED && (s->udp.sent || s->udp.received)) {
        account_usage(args, s->udp.version, IPPROTO_UDP,
                      format_log_address(&dest), ntohs(s->udp.dest), s->udp.uid, s->udp.sent, s->udp.received);
        s->udp.sent = 0;
        s->udp.received = 0;
    }

    // Cleanup lingering sessions
    if ((s->udp.state == UDP_CLOSED || s->udp.state == UDP_BLOCKED) &&
        s->udp.time + UDP_KEEP_TIMEOUT < now)
        return 1;

    return 0;
}

void check_udp_socket(const struct arguments *args, const struct epoll_event *ev) {
    struct ng_session *s = (struct ng_session *) ev->data.ptr;

    // Check socket error
    if (ev->events & EPOLLERR) {
        s->udp.time = time(NULL);

        int serr = 0;
        socklen_t optlen = sizeof(int);
        int err = getsockopt(s->socket, SOL_SOCKET, SO_ERROR, &serr, &optlen);
        if (err < 0)
            log_android(ANDROID_LOG_ERROR, "UDP getsockopt error %d: %s",
                        errno, strerror(errno));
        else if (serr)
            log_android(ANDROID_LOG_ERROR, "UDP SO_ERROR %d: %s", serr, strerror(serr));

        s->udp.state = UDP_FINISHING;

        if (err >= 0 && (serr == ECONNREFUSED || serr == EHOSTUNREACH)) {
            write_connection_unreach(args, s, serr);
        }
    } else {
        // Check socket read
        if (ev->events & EPOLLIN) {
            s->udp.time = time(NULL);

            uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, s->udp.mss, "udp recv");
            if (buffer == NULL) {
                s->udp.state = UDP_FINISHING;
                return;
            }
            ssize_t bytes = recv(s->socket, buffer, s->udp.mss, 0);
            if (bytes < 0) {
                // Socket error
                log_android(ANDROID_LOG_WARN, "UDP recv error %d: %s",
                            errno, strerror(errno));

                if (errno != EINTR && errno != EAGAIN)
                    s->udp.state = UDP_FINISHING;
            } else if (bytes == 0) {
                log_android(ANDROID_LOG_WARN, "UDP recv eof");
                s->udp.state = UDP_FINISHING;

            } else {
                // Socket read data
                struct log_address dest = {.version = s->udp.version, .address = &s->udp.daddr};
                log_android(ANDROID_LOG_INFO, "UDP recv bytes %zd from %s/%u for tun",
                            bytes, format_log_address(&dest), ntohs(s->udp.dest));

                s->udp.received += bytes;

                if (ntohs(s->udp.dest) == 53 && dns_has_multiple_questions(buffer, (size_t) bytes)) {
                    log_android(ANDROID_LOG_WARN, "Dropping DNS response with multiple questions");
                    s->udp.state = UDP_FINISHING;
                    release_packet_buffer(args, buffer);
                    return;
                }

                // Process DNS response
                if (ntohs(s->udp.dest) == 53)
                    parse_dns_response(args, s, buffer, (size_t *) &bytes);

                // Forward to tun
                if (write_udp(args, &s->udp, buffer, (size_t) bytes) < 0)
                    s->udp.state = UDP_FINISHING;
                else {
                    // Prevent too many open files
                    if (ntohs(s->udp.dest) == 53)
                        s->udp.state = UDP_FINISHING;
                }
            }
            release_packet_buffer(args, buffer);
        }
    }
}

int has_udp_session(const struct arguments *args, const uint8_t *pkt, const uint8_t *payload) {
    const struct udphdr *udp = (const struct udphdr *) payload;
    if (ntohs(udp->dest) == 53)
        return !args->fwd53;
    return find_udp_session(args, pkt, payload) != NULL;
}

void block_udp(const struct arguments *args,
               const uint8_t *pkt, size_t length,
               const uint8_t *payload,
               int uid) {
    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    const struct udphdr *udphdr = (struct udphdr *) payload;

    struct log_address source = {.version = version, .address = version == 4 ? (const void *) &ip4->saddr : &ip6->ip6_src};
    struct log_address dest = {.version = version, .address = version == 4 ? (const void *) &ip4->daddr : &ip6->ip6_dst};

    log_android(ANDROID_LOG_INFO, "UDP blocked session from %s/%u to %s/%u",
                format_log_address(&source), ntohs(udphdr->source), format_log_address(&dest), ntohs(udphdr->dest));

    // Blocked flows have no socket or epoll registration, so their records can be reused.
    int blocked = 0;
    struct ng_session **match = NULL;
    struct ng_session **oldest = NULL;
    for (struct ng_session **link = &args->ctx->ng_session; *link != NULL; link = &(*link)->next) {
        struct ng_session *cur = *link;
        if (cur->protocol != IPPROTO_UDP || cur->udp.state != UDP_BLOCKED)
            continue;
        blocked++;
        if (cur->udp.version == version && cur->udp.source == udphdr->source &&
            cur->udp.dest == udphdr->dest &&
            (version == 4 ? cur->udp.saddr.ip4 == (uint32_t) ip4->saddr &&
                           cur->udp.daddr.ip4 == (uint32_t) ip4->daddr
                          : memcmp(&cur->udp.saddr.ip6, &ip6->ip6_src, 16) == 0 &&
                            memcmp(&cur->udp.daddr.ip6, &ip6->ip6_dst, 16) == 0)) {
            match = link;
            break;
        }
        // The tail is older when timestamps tie because new records are prepended.
        if (oldest == NULL || cur->udp.time <= (*oldest)->udp.time)
            oldest = link;
    }

    struct ng_session *s;
    struct ng_session **reuse = (match != NULL ? match : blocked >= UDP_BLOCKED_MAX ? oldest : NULL);
    if (reuse != NULL) {
        s = *reuse;
        unindex_session(args->ctx, s); // Remove the old tuple before resetting/reusing it.
        *reuse = s->next;
    } else {
        s = ng_malloc(sizeof(struct ng_session), "udp session block");
        if (s == NULL)
            return;
    }
    memset(s, 0, sizeof(*s));
    s->protocol = IPPROTO_UDP;

    s->udp.time = time(NULL);
    s->udp.uid = uid;
    s->udp.version = version;

    if (version == 4) {
        s->udp.saddr.ip4 = (__be32) ip4->saddr;
        s->udp.daddr.ip4 = (__be32) ip4->daddr;
    } else {
        memcpy(&s->udp.saddr.ip6, &ip6->ip6_src, 16);
        memcpy(&s->udp.daddr.ip6, &ip6->ip6_dst, 16);
    }

    s->udp.source = udphdr->source;
    s->udp.dest = udphdr->dest;
    s->udp.state = UDP_BLOCKED;
    s->socket = -1;

    write_connection_unreach(args, s, EHOSTDOWN);

    add_session(args->ctx, s);
}

jboolean handle_udp(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, struct allowed *redirect,
                    const int epoll_fd) {
    return handle_udp_with_lookup(args, pkt, length, payload, uid, redirect, epoll_fd, NULL);
}

jboolean handle_udp_with_lookup(const struct arguments *args,
                    const uint8_t *pkt, size_t length,
                    const uint8_t *payload,
                    int uid, struct allowed *redirect,
                    const int epoll_fd, const struct udp_session_lookup *lookup) {
    // Get headers
    const uint8_t version = (*pkt) >> 4;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    const struct udphdr *udphdr = (struct udphdr *) payload;
    const uint8_t *data = payload + sizeof(struct udphdr);
    const size_t datalen = length - (data - pkt);

    if (ntohs(udphdr->dest) == 53 && datalen < sizeof(struct dns_header)) {
        log_android(ANDROID_LOG_WARN, "DNS request too short");
        return 0;
    }

    if (ntohs(udphdr->dest) == 53 && datalen >= sizeof(struct dns_header) &&
        (data[2] & 0x80) == 0 && dns_has_multiple_questions(data, datalen)) {
        // A header-only FORMERR avoids reflecting malformed names or section counts.
        struct dns_header response = {0};
        const struct dns_header *request = (const void *) data;
        response.id = request->id;
        response.qr = 1;
        response.rd = request->rd;
        response.cd = request->cd;
        response.rcode = 1;
        struct udp_session reply = {.version = version, .source = udphdr->source,
                                     .dest = udphdr->dest};
        if (version == 4) {
            reply.saddr.ip4 = ip4->saddr;
            reply.daddr.ip4 = ip4->daddr;
        } else {
            memcpy(&reply.saddr.ip6, &ip6->ip6_src, 16);
            memcpy(&reply.daddr.ip6, &ip6->ip6_dst, 16);
        }
        log_android(ANDROID_LOG_WARN, "DNS request has multiple questions; returning FORMERR");
        return write_udp(args, &reply, (uint8_t *) &response, sizeof(response)) >= 0;
    }

    struct ng_session *cur = (lookup == NULL ? find_udp_session(args, pkt, payload)
                                              : lookup->session);

    struct log_address source = {.version = version, .address = version == 4 ? (const void *) &ip4->saddr : &ip6->ip6_src};
    struct log_address dest = {.version = version, .address = version == 4 ? (const void *) &ip4->daddr : &ip6->ip6_dst};

    if (cur != NULL && cur->socket < 0) {
        write_connection_unreach(args, cur, EHOSTDOWN);
        return 0;
    }

    if (cur != NULL && cur->udp.state != UDP_ACTIVE) {
        log_android(ANDROID_LOG_INFO, "UDP ignore session from %s/%u to %s/%u state %d",
                    format_log_address(&source), ntohs(udphdr->source), format_log_address(&dest), ntohs(udphdr->dest), cur->udp.state);
        return 0;
    }

    if (ntohs(udphdr->dest) == 53 && redirect == NULL && uid != own_uid && args->fwd53) {
        log_android(
                ANDROID_LOG_ERROR, "Direct DNS connection for %s/%u to %s/%u uid %uid not allowed",
                format_log_address(&source), ntohs(udphdr->source), format_log_address(&dest), ntohs(udphdr->dest), uid
        );
        return 0;
    }

    // Create new session if needed
    if (cur == NULL) {
        log_android(ANDROID_LOG_INFO, "UDP new session from %s/%u to %s/%u",
                    format_log_address(&source), ntohs(udphdr->source), format_log_address(&dest), ntohs(udphdr->dest));

        // Register session
        struct ng_session *s = ng_malloc(sizeof(struct ng_session), "udp session");
        if (s == NULL)
            return 0;
        s->protocol = IPPROTO_UDP;

        s->udp.time = time(NULL);
        s->udp.uid = uid;
        s->udp.version = version;

        int rversion;
        if (redirect == NULL) {
            rversion = s->udp.version;
        } else if (s->udp.version == 6 && strcmp(redirect->raddr, LOOPBACK_ADDRESS) == 0) {
            strcpy(redirect->raddr, LOOPBACK_ADDRESS_IPv6);
            rversion = 6;
        } else {
            rversion = (strstr(redirect->raddr, ":") == NULL ? 4 : 6);
        }

        s->udp.mss = (uint16_t) (rversion == 4 ? UDP4_MAXMSG : UDP6_MAXMSG);

        s->udp.sent = 0;
        s->udp.received = 0;

        if (version == 4) {
            s->udp.saddr.ip4 = (__be32) ip4->saddr;
            s->udp.daddr.ip4 = (__be32) ip4->daddr;
        } else {
            memcpy(&s->udp.saddr.ip6, &ip6->ip6_src, 16);
            memcpy(&s->udp.daddr.ip6, &ip6->ip6_dst, 16);
        }

        s->udp.source = udphdr->source;
        s->udp.dest = udphdr->dest;
        s->udp.state = UDP_ACTIVE;
        s->next = NULL;

        // Open UDP socket
        s->socket = open_udp_socket(args, &s->udp, redirect);
        if (s->socket < 0) {
            ng_free(s, __FILE__, __LINE__);
            return 0;
        }

        log_android(ANDROID_LOG_DEBUG, "UDP socket %d", s->socket);

        // Monitor events
        memset(&s->ev, 0, sizeof(struct epoll_event));
        s->ev.events = EPOLLIN | EPOLLERR;
        s->ev.data.ptr = s;
        if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev))
            log_android(ANDROID_LOG_ERROR, "epoll add udp error %d: %s", errno, strerror(errno));

        add_session(args->ctx, s);

        cur = s;
    }

    // Check for DHCP (tethering)
    if (ntohs(udphdr->source) == 68 || ntohs(udphdr->dest) == 67) {
        if (check_dhcp(args, &cur->udp, data, datalen) >= 0)
            return 1;
    }

    log_android(ANDROID_LOG_INFO, "UDP forward from tun %s/%u to %s/%u data %zu",
                format_log_address(&source), ntohs(udphdr->source), format_log_address(&dest), ntohs(udphdr->dest), datalen);

    cur->udp.time = time(NULL);

    if (ntohs(udphdr->dest) == 53) {
        struct dns_header *dns = (struct dns_header *) data;
        int qcount = ntohs(dns->q_count);
        if (dns->qr == 0 && dns->opcode == 0 && qcount > 0) {
            // http://tools.ietf.org/html/rfc1035
            char qname[DNS_QNAME_MAX + 1];
            int32_t off = sizeof(struct dns_header);

            off = get_qname(data, datalen, (uint16_t) off, qname);
            if (off <= 0 || (size_t) off + 4 > datalen) {
                log_android(ANDROID_LOG_WARN,
                            "DNS request Q invalid off %d datalen %zu", off, datalen);
                return 0;
            }
            uint16_t qtype = read_net16(data + off);
            uint16_t qclass = read_net16(data + off + 2);
            log_android(ANDROID_LOG_INFO,
                        "DNS question %d qtype %d qclass %d qname %s",
                        0, qtype, qclass, qname);

            //handle onion websites
            if (tor_dns_port > 0) {
                if (redirect != NULL && str_ends_with(qname, ".onion")) {
                    redirect->rport = tor_dns_port;
                }
            }

            if (redirect != NULL) {
                //https://datatracker.ietf.org/doc/html/rfc7050
                if (str_equal(qname, "ipv4only.arpa")) {
                    redirect = NULL;
                } else if (bypass_lan) {
                    //https://datatracker.ietf.org/doc/html/rfc6762
                    if (str_ends_with(qname, ".local")
                        || str_ends_with(qname, ".lan")
                        || str_ends_with(qname, ".home")
                        || str_ends_with(qname, ".corp")
                        || str_ends_with(qname, ".private")
                        || str_ends_with(qname, ".internal")
                        || str_ends_with(qname, ".intranet")
                        || str_ends_with(qname, ".254.169.in-addr.arpa")
                        || str_ends_with(qname, ".8.e.f.ip6.arpa")
                        || str_ends_with(qname, ".9.e.f.ip6.arpa")
                        || str_ends_with(qname, ".a.e.f.ip6.arpa")
                        || str_ends_with(qname, ".b.e.f.ip6.arpa")) {
                        redirect = NULL;
                    }
                }
            }
        }
    }

    int rversion;
    struct sockaddr_in addr4 = {0};
    struct sockaddr_in6 addr6 = {0};
    if (redirect == NULL) {
        rversion = cur->udp.version;
        if (cur->udp.version == 4) {
            addr4.sin_family = AF_INET;
            addr4.sin_addr.s_addr = (__be32) cur->udp.daddr.ip4;
            addr4.sin_port = cur->udp.dest;
        } else {
            addr6.sin6_family = AF_INET6;
            memcpy(&addr6.sin6_addr, &cur->udp.daddr.ip6, 16);
            addr6.sin6_port = cur->udp.dest;
        }
    } else {

        if (strstr(redirect->raddr, ":") == NULL) {
            rversion = 4;
        } else {
            rversion = 6;
        }

        log_android(ANDROID_LOG_INFO, "UDP%d redirect to %s/%u",
                    rversion, redirect->raddr, redirect->rport);

        if (rversion == 4) {
            addr4.sin_family = AF_INET;
            inet_pton(AF_INET, redirect->raddr, &addr4.sin_addr);
            addr4.sin_port = htons(redirect->rport);
        } else {
            addr6.sin6_family = AF_INET6;
            inet_pton(AF_INET6, redirect->raddr, &addr6.sin6_addr);
            addr6.sin6_port = htons(redirect->rport);
        }
    }

    ssize_t sent = sendto(cur->socket, data, (socklen_t) datalen, MSG_NOSIGNAL,
                          (rversion == 4 ? (const struct sockaddr *) &addr4
                                       : (const struct sockaddr *) &addr6),
                          (socklen_t) (rversion == 4 ? sizeof(addr4) : sizeof(addr6)));
    if (sent < 0 || (size_t) sent != datalen) {
        log_android(ANDROID_LOG_ERROR, "UDP sendto error %d: %s", errno, strerror(errno));
        if (errno != EINTR && errno != EAGAIN) {
            cur->udp.state = UDP_FINISHING;
            return 0;
        }
    } else
        cur->udp.sent += datalen;

    return 1;
}

int open_udp_socket(const struct arguments *args,
                    const struct udp_session *cur, const struct allowed *redirect) {
    int sock;
    int version;
    if (redirect == NULL)
        version = cur->version;
    else
        version = (strstr(redirect->raddr, ":") == NULL ? 4 : 6);

    // Get UDP socket
    sock = socket(version == 4 ? PF_INET : PF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, IPPROTO_UDP);
    if (sock < 0) {
        log_android(ANDROID_LOG_ERROR, "UDP socket error %d: %s", errno, strerror(errno));
        return -1;
    }

    // Protect socket
    if (protect_socket(args, sock, cur->uid) < 0) {
        close(sock);
        return -1;
    }



    // Check for broadcast/multicast
    if (cur->version == 4) {
        uint32_t broadcast4 = INADDR_BROADCAST;
        if (memcmp(&cur->daddr.ip4, &broadcast4, sizeof(broadcast4)) == 0) {
            log_android(ANDROID_LOG_WARN, "UDP4 broadcast");
            int on = 1;
            if (setsockopt(sock, SOL_SOCKET, SO_BROADCAST, &on, sizeof(on)))
                log_android(ANDROID_LOG_ERROR, "UDP setsockopt SO_BROADCAST error %d: %s",
                            errno, strerror(errno));
        }
    } else {
        // http://man7.org/linux/man-pages/man7/ipv6.7.html
        if (*((uint8_t *) &cur->daddr.ip6) == 0xFF) {
            log_android(ANDROID_LOG_WARN, "UDP6 multicast");

            int loop = 1; // true
            if (setsockopt(sock, IPPROTO_IPV6, IPV6_MULTICAST_LOOP, &loop, sizeof(loop)))
                log_android(ANDROID_LOG_ERROR,
                            "UDP setsockopt IPV6_MULTICAST_LOOP error %d: %s",
                            errno, strerror(errno));

            int ttl = -1; // route default
            if (setsockopt(sock, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &ttl, sizeof(ttl)))
                log_android(ANDROID_LOG_ERROR,
                            "UDP setsockopt IPV6_MULTICAST_HOPS error %d: %s",
                            errno, strerror(errno));

            struct ipv6_mreq mreq6;
            memcpy(&mreq6.ipv6mr_multiaddr, &cur->daddr.ip6, sizeof(struct in6_addr));
            mreq6.ipv6mr_interface = INADDR_ANY;
            if (setsockopt(sock, IPPROTO_IPV6, IPV6_ADD_MEMBERSHIP, &mreq6, sizeof(mreq6)))
                log_android(ANDROID_LOG_ERROR,
                            "UDP setsockopt IPV6_ADD_MEMBERSHIP error %d: %s",
                            errno, strerror(errno));
        }
    }

    // Set non blocking
    int flags = fcntl(sock, F_GETFL, 0);
    if (flags < 0 || fcntl(sock, F_SETFL, flags | O_NONBLOCK) < 0) {
        log_android(ANDROID_LOG_ERROR, "fcntl socket O_NONBLOCK error %d: %s",
                    errno, strerror(errno));
        close(sock);
        return -1;
    }

    return sock;
}

ssize_t write_udp(const struct arguments *args, const struct udp_session *cur,
                  uint8_t *data, size_t datalen) {
    size_t len;
    u_int8_t *buffer;
    struct udphdr *udp;
    uint16_t csum;

    // Build packet
    if (cur->version == 4) {
        len = sizeof(struct iphdr) + sizeof(struct udphdr) + datalen;
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "udp write4");
        if (buffer == NULL)
            return -1;
        struct iphdr *ip4 = (struct iphdr *) buffer;
        udp = (struct udphdr *) (buffer + sizeof(struct iphdr));
        if (datalen)
            memcpy(buffer + sizeof(struct iphdr) + sizeof(struct udphdr), data, datalen);

        // Build IP4 header
        memset(ip4, 0, sizeof(struct iphdr));
        ip4->version = 4;
        ip4->ihl = sizeof(struct iphdr) >> 2;
        ip4->tot_len = htons(len);
        ip4->ttl = IPDEFTTL;
        ip4->protocol = IPPROTO_UDP;
        ip4->saddr = cur->daddr.ip4;
        ip4->daddr = cur->saddr.ip4;

        // Calculate IP4 checksum
        ip4->check = ~calc_checksum(0, (uint8_t *) ip4, sizeof(struct iphdr));

        // Calculate UDP4 checksum
        struct ippseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ippseudo));
        pseudo.ippseudo_src.s_addr = (__be32) ip4->saddr;
        pseudo.ippseudo_dst.s_addr = (__be32) ip4->daddr;
        pseudo.ippseudo_p = ip4->protocol;
        pseudo.ippseudo_len = htons(sizeof(struct udphdr) + datalen);

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ippseudo));
    } else {
        len = sizeof(struct ip6_hdr) + sizeof(struct udphdr) + datalen;
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "udp write6");
        if (buffer == NULL)
            return -1;
        struct ip6_hdr *ip6 = (struct ip6_hdr *) buffer;
        udp = (struct udphdr *) (buffer + sizeof(struct ip6_hdr));
        if (datalen)
            memcpy(buffer + sizeof(struct ip6_hdr) + sizeof(struct udphdr), data, datalen);

        // Build IP6 header
        memset(ip6, 0, sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_flow = 0;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_plen = htons(len - sizeof(struct ip6_hdr));
        ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt = IPPROTO_UDP;
        ip6->ip6_ctlun.ip6_un1.ip6_un1_hlim = IPDEFTTL;
        ip6->ip6_ctlun.ip6_un2_vfc = IPV6_VERSION;
        memcpy(&(ip6->ip6_src), &cur->daddr.ip6, 16);
        memcpy(&(ip6->ip6_dst), &cur->saddr.ip6, 16);

        // Calculate UDP6 checksum
        struct ip6_hdr_pseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
        memcpy(&pseudo.ip6ph_src, &ip6->ip6_dst, 16);
        memcpy(&pseudo.ip6ph_dst, &ip6->ip6_src, 16);
        pseudo.ip6ph_len = ip6->ip6_ctlun.ip6_un1.ip6_un1_plen;
        pseudo.ip6ph_nxt = ip6->ip6_ctlun.ip6_un1.ip6_un1_nxt;

        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
    }

    // Build UDP header
    memset(udp, 0, sizeof(struct udphdr));
    udp->source = cur->dest;
    udp->dest = cur->source;
    udp->len = htons(sizeof(struct udphdr) + datalen);

    // Continue checksum
    csum = calc_checksum(csum, (uint8_t *) udp, sizeof(struct udphdr));
    csum = calc_checksum(csum, data, datalen);
    udp->check = ~csum;

    struct log_address source = {.version = cur->version, .address = &cur->saddr};
    struct log_address dest = {.version = cur->version, .address = &cur->daddr};

    // Send packet
    log_android(ANDROID_LOG_DEBUG,
                "UDP sending to tun %d from %s/%u to %s/%u data %zu",
                args->tun, format_log_address(&dest), ntohs(cur->dest), format_log_address(&source), ntohs(cur->source), len);

    ssize_t res = write(args->tun, buffer, len);

    if (res < 0) {
        log_android(ANDROID_LOG_WARN, "UDP write error %d: %s", errno, strerror(errno));
    }


    release_packet_buffer(args, buffer);

    if (res < 0 || (size_t) res != len) {
        log_android(ANDROID_LOG_ERROR, "write %zd/%zu", res, len);
        return -1;
    }

    return res;
}

#pragma clang diagnostic pop
