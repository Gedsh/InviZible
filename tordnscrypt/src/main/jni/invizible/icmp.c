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
#include <linux/errqueue.h>

extern int own_uid;

static int icmp_reply_log_priority(int version, const struct icmp *icmp) {
    return icmp->icmp_code == 0 &&
           icmp->icmp_type == (version == 4 ? ICMP_ECHOREPLY : ICMP6_ECHO_REPLY)
           ? ANDROID_LOG_DEBUG : ANDROID_LOG_WARN;
}

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
        struct log_address source = {.version = s->icmp.version, .address = &s->icmp.saddr};
        struct log_address dest = {.version = s->icmp.version, .address = &s->icmp.daddr};
        log_android(ANDROID_LOG_DEBUG, "ICMP idle %jd/%d sec stop %d from %s to %s",
                    (intmax_t) (now - s->icmp.time), timeout, s->icmp.stop,
                    format_log_address(&source), format_log_address(&dest));

        if (close(s->socket))
            log_android(ANDROID_LOG_ERROR, "ICMP close %d error %d: %s",
                        s->socket, errno, strerror(errno));
        s->socket = -1;

        return 1;
    }

    return 0;
}

// Quote the original tunnel packet, never the redirected upstream tuple.
static void write_socket_error(const struct arguments *args,
                              const uint8_t *packet, size_t length, int serr,
                              uint32_t mtu, bool expired) {
    if (packet == NULL || length == 0)
        return;
    struct icmp_session reply = {.version = packet[0] >> 4};
    size_t limit, original_length;
    if (reply.version == 4) {
        if (length < sizeof(struct iphdr)) return;
        size_t header = (packet[0] & 0x0F) * 4u;
        original_length = read_net16(packet + 2);
        if (header < sizeof(struct iphdr) || header + 8 > length ||
            original_length < header + 8 || (read_net16(packet + 6) & IP_OFFMASK) != 0)
            return;
        memcpy(&reply.saddr.ip4, packet + 12, 4);
        memcpy(&reply.daddr.ip4, packet + 16, 4);
        uint32_t source = ntohl(reply.saddr.ip4), dest = ntohl(reply.daddr.ip4);
        if (source == 0 || source == UINT32_MAX || IN_MULTICAST(source) ||
            dest == UINT32_MAX || IN_MULTICAST(dest))
            return;
        limit = 576 - sizeof(struct iphdr) - 8;
    } else if (reply.version == 6) {
        if (length < sizeof(struct ip6_hdr) + 8) return;
        original_length = sizeof(struct ip6_hdr) + read_net16(packet + 4);
        if (original_length < sizeof(struct ip6_hdr) + 8) return;
        memcpy(&reply.saddr.ip6, packet + 8, 16);
        memcpy(&reply.daddr.ip6, packet + 24, 16);
        if (IN6_IS_ADDR_UNSPECIFIED(&reply.saddr.ip6) || IN6_IS_ADDR_MULTICAST(&reply.saddr.ip6) ||
            IN6_IS_ADDR_MULTICAST(&reply.daddr.ip6))
            return;
        limit = 1280 - sizeof(struct ip6_hdr) - 8;
    } else
        return;

    if (length > original_length) length = original_length;
    if (length > limit) length = limit;
    _Alignas(8) uint8_t message[1280 - sizeof(struct ip6_hdr)] = {0};
    message[0] = reply.version == 4 ? ICMP_UNREACH : ICMP6_DST_UNREACH;
    message[1] = reply.version == 4
                 ? (serr == ECONNREFUSED ? ICMP_UNREACH_PORT : ICMP_UNREACH_HOST)
                 : (serr == ECONNREFUSED ? ICMP6_DST_UNREACH_NOPORT : ICMP6_DST_UNREACH_NOROUTE);
    if (expired) {
        message[0] = reply.version == 4 ? ICMP_TIMXCEED : ICMP6_TIME_EXCEEDED;
        message[1] = 0;
    } else if (serr == EMSGSIZE) {
        if (mtu == 0 || (reply.version == 4 && (mtu < 68 || mtu > UINT16_MAX)))
            return;
        message[0] = reply.version == 4 ? ICMP_UNREACH : ICMP6_PACKET_TOO_BIG;
        message[1] = reply.version == 4 ? ICMP_UNREACH_NEEDFRAG : 0;
        if (reply.version == 4) {
            uint16_t value = htons((uint16_t) mtu);
            memcpy(message + 6, &value, sizeof(value));
        } else {
            uint32_t value = htonl(mtu);
            memcpy(message + 4, &value, sizeof(value));
        }
    } else if (serr == EACCES || serr == EPERM) {
        message[1] = reply.version == 4 ? ICMP_UNREACH_FILTER_PROHIB : ICMP6_DST_UNREACH_ADMIN;
    } else if ((serr == ENETUNREACH || serr == ENETDOWN) && reply.version == 4) {
        message[1] = ICMP_UNREACH_NET;
    }
    memcpy(message + 8, packet, length);
    uint16_t csum = 0;
    if (reply.version == 6) {
        struct ip6_hdr_pseudo pseudo = {0};
        pseudo.ip6ph_src = reply.daddr.ip6;
        pseudo.ip6ph_dst = reply.saddr.ip6;
        pseudo.ip6ph_len = htonl((uint32_t) (8 + length));
        pseudo.ip6ph_nxt = IPPROTO_ICMPV6;
        csum = calc_checksum(0, (const uint8_t *) &pseudo, sizeof(pseudo));
    }
    csum = ~calc_checksum(csum, message, 8 + length);
    memcpy(message + 2, &csum, sizeof(csum));
    write_icmp(args, &reply, message, 8 + length);
}

void write_connection_unreach(const struct arguments *args,
                              const uint8_t *packet, size_t length, int serr) {
    if (serr == ECONNREFUSED || serr == EHOSTUNREACH || serr == EHOSTDOWN ||
        serr == ENETUNREACH || serr == ENETDOWN || serr == EACCES || serr == EPERM)
        write_socket_error(args, packet, length, serr, 0, false);
}

void enable_socket_errors(int sock, int version) {
    int on = 1;
    // Optional on older kernels: SO_ERROR remains the fallback if unsupported.
    if (setsockopt(sock, version == 4 ? IPPROTO_IP : IPPROTO_IPV6,
                   version == 4 ? IP_RECVERR : IPV6_RECVERR, &on, sizeof(on)))
        log_android(ANDROID_LOG_WARN, "IP%d RECVERR error %d: %s", version, errno, strerror(errno));
}

// Locate the transport header within the bounded original quote. Only extension
// headers accepted by the tunnel parser can appear here.
static size_t quoted_transport(const uint8_t *packet, size_t length, int *protocol) {
    if (length < 20) return 0;
    if (packet[0] >> 4 == 4) {
        size_t off = (packet[0] & 15u) * 4u;
        if (off < 20 || off + 8 > length) return 0;
        *protocol = packet[9];
        return off;
    }
    if (packet[0] >> 4 != 6 || length < 48) return 0;
    size_t off = 40;
    *protocol = packet[6];
    while (*protocol != IPPROTO_UDP && *protocol != IPPROTO_ICMPV6) {
        if (length - off < 8) return 0;
        size_t size;
        if (*protocol == IPPROTO_FRAGMENT) size = 8;
        else if (*protocol == IPPROTO_AH) size = ((size_t) packet[off + 1] + 2) * 4;
        else if (*protocol == IPPROTO_HOPOPTS || *protocol == IPPROTO_ROUTING ||
                 *protocol == IPPROTO_DSTOPTS) size = ((size_t) packet[off + 1] + 1) * 8;
        else return 0;
        if (size > length - off) return 0;
        *protocol = packet[off];
        off += size;
    }
    return off + 8 <= length ? off : 0;
}

int forward_socket_errors(const struct arguments *args, int sock,
                          const uint8_t *packet, size_t length) {
    int result = -1;
    // Drain only on errors, with a work bound for fairness to other sessions.
    // Further queued errors keep EPOLLERR level-triggered for the next iteration.
    for (int i = 0; i < 8; i++) {
        union {
            struct cmsghdr alignment;
            uint8_t data[CMSG_SPACE(sizeof(struct sock_extended_err) + sizeof(struct sockaddr_in6))];
        } control;
        uint8_t data[112];
        struct iovec iov = {.iov_base = data, .iov_len = sizeof(data)};
        struct msghdr message = {.msg_iov = &iov, .msg_iovlen = 1,
                .msg_control = control.data, .msg_controllen = sizeof(control.data)};
        ssize_t received = recvmsg(sock, &message, MSG_ERRQUEUE | MSG_DONTWAIT);
        if (received < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (result < 0) result = 0;
        if (message.msg_flags & MSG_CTRUNC) continue;
        for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message); cmsg != NULL;
             cmsg = CMSG_NXTHDR(&message, cmsg)) {
            int version = cmsg->cmsg_level == IPPROTO_IP && cmsg->cmsg_type == IP_RECVERR ? 4 :
                          cmsg->cmsg_level == IPPROTO_IPV6 && cmsg->cmsg_type == IPV6_RECVERR ? 6 : 0;
            size_t available = message.msg_controllen - (size_t) ((uint8_t *) cmsg - control.data);
            if (!version || cmsg->cmsg_len < CMSG_LEN(sizeof(struct sock_extended_err)) ||
                cmsg->cmsg_len > available) continue;
            struct sock_extended_err error;
            memcpy(&error, CMSG_DATA(cmsg), sizeof(error));
            if (error.ee_origin != SO_EE_ORIGIN_LOCAL &&
                error.ee_origin != (version == 4 ? SO_EE_ORIGIN_ICMP : SO_EE_ORIGIN_ICMP6)) continue;
            if (error.ee_errno == 0) continue;
            log_android(ANDROID_LOG_WARN, "IP%d socket %d upstream error %u: %s mtu %u",
                        version, sock, error.ee_errno, strerror((int) error.ee_errno),
                        error.ee_errno == EMSGSIZE ? error.ee_info : 0);
            if (error.ee_errno != EMSGSIZE) result = 1;
            int protocol = 0;
            size_t off = quoted_transport(packet, length, &protocol);
            if (off == 0) continue;
            // UDP error queues contain payload only; ping queues include the
            // echo header with a kernel-assigned ID/checksum. Compare available
            // payload bytes (and ping sequence) to avoid quoting an older send.
            size_t skip = protocol == IPPROTO_UDP ? 0 : 6;
            size_t original = off + (protocol == IPPROTO_UDP ? 8 : skip);
            if ((size_t) received > skip) {
                size_t compare = (size_t) received - skip;
                if (compare > length - original) compare = length - original;
                if (memcmp(data + skip, packet + original, compare) != 0) continue;
            }
            // Do not translate MTUs between redirected address families: their
            // packet sizes differ. Never substitute the tunnel MTU for missing data.
            if (error.ee_errno == EMSGSIZE && (length == 0 || packet[0] >> 4 != version)) continue;
            uint32_t mtu = error.ee_info;
            if (error.ee_errno == EMSGSIZE && mtu != 0) {
                // The socket sends no tunnel IP options/extension headers.
                size_t extra = off - (version == 4 ? 20u : 40u);
                if (extra > UINT32_MAX - mtu) continue;
                mtu += (uint32_t) extra;
            }
            bool expired = error.ee_origin != SO_EE_ORIGIN_LOCAL &&
                    error.ee_type == (version == 4 ? ICMP_TIMXCEED : ICMP6_TIME_EXCEEDED) &&
                    error.ee_code == 0;
            if (expired || error.ee_errno == EMSGSIZE || error.ee_errno == ECONNREFUSED ||
                error.ee_errno == EHOSTUNREACH || error.ee_errno == EHOSTDOWN ||
                error.ee_errno == ENETUNREACH || error.ee_errno == ENETDOWN ||
                error.ee_errno == EACCES || error.ee_errno == EPERM)
                write_socket_error(args, packet, length, (int) error.ee_errno, mtu, expired);
        }
    }
    return result;
}

void check_icmp_socket(const struct arguments *args, const struct epoll_event *ev) {
    struct ng_session *s = (struct ng_session *) ev->data.ptr;
    if (s->socket < 0 || s->icmp.stop) return;

    // Check socket error
    if (ev->events & EPOLLERR) {
        s->icmp.time = time(NULL);

        int queued = forward_socket_errors(args, s->socket,
                                           s->icmp.error_quote, s->icmp.error_quote_length);
        if (queued >= 0) {
            s->icmp.stop = queued != 0;
            return;
        }

        int serr = 0;
        socklen_t optlen = sizeof(int);
        int err = getsockopt(s->socket, SOL_SOCKET, SO_ERROR, &serr, &optlen);
        if (err < 0)
            log_android(ANDROID_LOG_ERROR, "ICMP getsockopt error %d: %s",
                        errno, strerror(errno));
        else if (serr)
            log_android(ANDROID_LOG_ERROR, "ICMP SO_ERROR %d: %s",
                        serr, strerror(serr));

        if (err >= 0 && serr != 0 && serr != EMSGSIZE)
            write_connection_unreach(args, s->icmp.error_quote, s->icmp.error_quote_length, serr);
        s->icmp.stop = err < 0 || (serr != 0 && serr != EMSGSIZE);
    } else {
        // Check socket read
        if (ev->events & EPOLLIN) {
            s->icmp.time = time(NULL);

            uint16_t blen = (uint16_t) (s->icmp.version == 4 ? ICMP4_MAXMSG : ICMP6_MAXMSG);
            uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, blen, "icmp socket");
            if (buffer == NULL) {
                s->icmp.stop = 1;
                return;
            }
            ssize_t bytes = recv(s->socket, buffer, blen, 0);
            if (bytes < 0) {
                // Socket error
                int serr = errno;
                log_android(ANDROID_LOG_WARN, "ICMP recv error %d: %s",
                            serr, strerror(serr));

                if (serr != EINTR && serr != EAGAIN) {
                    // An upstream error can arrive after the EPOLLIN snapshot.
                    int queued = forward_socket_errors(args, s->socket,
                                                       s->icmp.error_quote, s->icmp.error_quote_length);
                    if (queued < 0 && serr != EMSGSIZE)
                        write_connection_unreach(args, s->icmp.error_quote, s->icmp.error_quote_length, serr);
                    s->icmp.stop = queued > 0 || (queued < 0 && serr != EMSGSIZE);
                }
            } else if (bytes == 0) {
                log_android(ANDROID_LOG_WARN, "ICMP recv eof");
                s->icmp.stop = 1;

            } else if (bytes < ICMP_MINLEN) {
                log_android(ANDROID_LOG_WARN, "ICMP recv packet too short length %zd", bytes);
            } else {
                // Socket read data
                struct log_address dest = {.version = s->icmp.version, .address = &s->icmp.daddr};

                // The ping socket's echo ID may differ from the original session ID.
                struct icmp *icmp = (struct icmp *) buffer;
                log_android(
                        icmp_reply_log_priority(s->icmp.version, icmp),
                        "ICMP recv bytes %zd from %s for tun type %d code %d id %x/%x seq %d",
                        bytes, format_log_address(&dest),
                        icmp->icmp_type, icmp->icmp_code,
                        s->icmp.id, icmp->icmp_id, icmp->icmp_seq);

                // restore original ID
                icmp->icmp_id = s->icmp.id;
                uint16_t csum = 0;
                if (s->icmp.version == 6) {
                    // Include the IPv6 pseudo-header when restoring the echo ID.
                    struct ip6_hdr_pseudo pseudo;
                    memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
                    memcpy(&pseudo.ip6ph_src, &s->icmp.daddr.ip6, 16);
                    memcpy(&pseudo.ip6ph_dst, &s->icmp.saddr.ip6, 16);
                    // Ping sockets return the ICMP message without an IPv6 header.
                    // recv is bounded by the uint16_t buffer length above.
                    pseudo.ip6ph_len = htonl((uint32_t) bytes);
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
            release_packet_buffer(args, buffer);
        }
    }
}

jboolean is_icmp_supported(int version,
                           const uint8_t *payload) {
    // Callers validate the message length before reading type and code.
    return payload[1] == 0 &&
           ((version == 4 && payload[0] == ICMP_ECHO) ||
            (version == 6 && payload[0] == ICMP6_ECHO_REQUEST));
}

jboolean handle_icmp(const struct arguments *args,
                     const uint8_t *pkt, size_t length,
                     const uint8_t *payload,
                     int uid,
                     const int epoll_fd) {
    const size_t payload_offset = (size_t) (payload - pkt);
    if (payload_offset > length || length - payload_offset < ICMP_MINLEN)
        return 0;

    // Get headers
    const uint8_t version = (*pkt) >> 4;
    if (!is_icmp_supported(version, payload))
        return 0;
    const struct iphdr *ip4 = (struct iphdr *) pkt;
    const struct ip6_hdr *ip6 = (struct ip6_hdr *) pkt;
    struct icmp *icmp = (struct icmp *) payload;
    const size_t icmplen = length - payload_offset;

    struct log_address source = {.version = version, .address = version == 4 ? (const void *) &ip4->saddr : &ip6->ip6_src};
    struct log_address dest = {.version = version, .address = version == 4 ? (const void *) &ip4->daddr : &ip6->ip6_dst};

    // Search session
    struct ng_session *cur = find_session(args->ctx, version, IPPROTO_ICMP,
            version == 4 ? (const void *) &ip4->saddr : &ip6->ip6_src, icmp->icmp_id,
            version == 4 ? (const void *) &ip4->daddr : &ip6->ip6_dst, 0);

    // Create new session if needed
    if (cur == NULL) {
        log_android(ANDROID_LOG_DEBUG, "ICMP new session from %s to %s", format_log_address(&source), format_log_address(&dest));

        // Register session
        struct ng_session *s = ng_malloc(sizeof(struct ng_session), "icmp session");
        if (s == NULL)
            return 0;
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
        s->icmp.error_quote_length = 0;
        s->next = NULL;

        // Open ICMP ping socket
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
        if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev)) {
            log_android(ANDROID_LOG_ERROR, "epoll add icmp error %d: %s", errno, strerror(errno));
            close(s->socket);
            ng_free(s, __FILE__, __LINE__);
            return 0;
        }

        add_session(args->ctx, s);

        cur = s;
    }

    if (cur->icmp.uid < 0 && uid >= 0 && cur->socket >= 0)
        cur->icmp.uid = uid;

    // Keep the original identifier and checksum in any error sent back to the app.
    cur->icmp.error_quote_length = 0;
    if (payload_offset + ICMP_MINLEN <= sizeof(cur->icmp.error_quote)) {
        size_t quoted = length < sizeof(cur->icmp.error_quote) ? length : sizeof(cur->icmp.error_quote);
        memcpy(cur->icmp.error_quote, pkt, quoted);
        cur->icmp.error_quote_length = (uint16_t) quoted;
    }

    // Modify ID
    // http://lwn.net/Articles/443051/
    icmp->icmp_id = ~icmp->icmp_id;
    uint16_t csum = 0;
    if (version == 6) {
        // Include the IPv6 pseudo-header when modifying the echo ID.
        struct ip6_hdr_pseudo pseudo;
        memset(&pseudo, 0, sizeof(struct ip6_hdr_pseudo));
        memcpy(&pseudo.ip6ph_src, &ip6->ip6_dst, 16);
        memcpy(&pseudo.ip6ph_dst, &ip6->ip6_src, 16);
        // Exclude extension headers and encode the full 32-bit pseudo-header field.
        pseudo.ip6ph_len = htonl((uint32_t) icmplen);
        pseudo.ip6ph_nxt = IPPROTO_ICMPV6;
        csum = calc_checksum(0, (uint8_t *) &pseudo, sizeof(struct ip6_hdr_pseudo));
    }
    icmp->icmp_cksum = 0;
    icmp->icmp_cksum = ~calc_checksum(csum, (uint8_t *) icmp, icmplen);

    log_android(ANDROID_LOG_DEBUG,
                "ICMP forward from tun %s to %s type %d code %d id %x seq %d data %zu",
                format_log_address(&source), format_log_address(&dest),
                icmp->icmp_type, icmp->icmp_code, icmp->icmp_id, icmp->icmp_seq, icmplen);

    cur->icmp.time = time(NULL);

    struct sockaddr_in server4 = {0};
    struct sockaddr_in6 server6 = {0};
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
    ssize_t sent = sendto(cur->socket, icmp, (socklen_t) icmplen, MSG_NOSIGNAL,
                          (version == 4 ? (const struct sockaddr *) &server4
                                        : (const struct sockaddr *) &server6),
                          (socklen_t) (version == 4 ? sizeof(server4) : sizeof(server6)));
    if (sent < 0 || (size_t) sent != icmplen) {
        int serr = errno;
        log_android(ANDROID_LOG_ERROR, "ICMP sendto error %d: %s", serr, strerror(serr));
        if (serr == EMSGSIZE) {
            if (forward_socket_errors(args, cur->socket,
                                      cur->icmp.error_quote, cur->icmp.error_quote_length) > 0)
                cur->icmp.stop = 1;
            return 0;
        }
        if (serr != EINTR && serr != EAGAIN) {
            write_connection_unreach(args, cur->icmp.error_quote, cur->icmp.error_quote_length, serr);
            cur->icmp.stop = 1;
            return 0;
        }
    }

    return 1;
}

int open_icmp_socket(const struct arguments *args, const struct icmp_session *cur) {
    int sock;

    // Get ICMP ping socket
    sock = socket(cur->version == 4 ? PF_INET : PF_INET6,
                  SOCK_DGRAM | SOCK_CLOEXEC,
                  cur->version == 4 ? IPPROTO_ICMP : IPPROTO_ICMPV6);
    if (sock < 0) {
        log_android(ANDROID_LOG_ERROR, "ICMP socket error %d: %s", errno, strerror(errno));
        return -1;
    }

    // Protect socket
    if (protect_socket(args, sock, cur->uid) < 0) {
        close(sock);
        return -1;
    }

    enable_socket_errors(sock, cur->version);

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

ssize_t write_icmp(const struct arguments *args, const struct icmp_session *cur,
                   uint8_t *data, size_t datalen) {
    size_t len;
    uint8_t *buffer;
    struct icmp *icmp = (struct icmp *) data;

    // Build packet
    if (cur->version == 4) {
        len = sizeof(struct iphdr) + datalen;
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "icmp write4");
        if (buffer == NULL)
            return -1;
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
        buffer = get_packet_buffer(args, PACKET_OUTPUT, len, "icmp write6");
        if (buffer == NULL)
            return -1;
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

    struct log_address source = {.version = cur->version, .address = &cur->saddr};
    struct log_address dest = {.version = cur->version, .address = &cur->daddr};

    // Send raw ICMP message
    log_android(icmp_reply_log_priority(cur->version, icmp),
                "ICMP sending to tun %d from %s to %s data %zu type %d code %d id %x seq %d",
                args->tun, format_log_address(&dest), format_log_address(&source), datalen,
                icmp->icmp_type, icmp->icmp_code, icmp->icmp_id, icmp->icmp_seq);

    ssize_t res = write(args->tun, buffer, len);

    release_packet_buffer(args, buffer);

    if (res < 0 || (size_t) res != len) {
        log_android(ANDROID_LOG_ERROR, "write %zd/%zu", res, len);
        return -1;
    }

    return res;
}

#pragma clang diagnostic pop
