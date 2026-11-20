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
        struct log_address source = {.version = s->icmp.version, .address = &s->icmp.saddr};
        struct log_address dest = {.version = s->icmp.version, .address = &s->icmp.daddr};
        log_android(ANDROID_LOG_WARN, "ICMP idle %jd/%d sec stop %d from %s to %s",
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
void write_connection_unreach(const struct arguments *args,
                              const uint8_t *packet, size_t length, const int serr) {
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
            uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, blen, "icmp socket");
            if (buffer == NULL) {
                s->icmp.stop = 1;
                return;
            }
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

            } else if (bytes < ICMP_MINLEN) {
                log_android(ANDROID_LOG_WARN, "ICMP recv packet too short length %zd", bytes);
            } else {
                // Socket read data
                struct log_address dest = {.version = s->icmp.version, .address = &s->icmp.daddr};

                // The ping socket's echo ID may differ from the original session ID.
                struct icmp *icmp = (struct icmp *) buffer;
                log_android(
                        s->icmp.id == icmp->icmp_id ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
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
        log_android(ANDROID_LOG_INFO, "ICMP new session from %s to %s", format_log_address(&source), format_log_address(&dest));

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
        if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, s->socket, &s->ev))
            log_android(ANDROID_LOG_ERROR, "epoll add icmp error %d: %s", errno, strerror(errno));

        add_session(args->ctx, s);

        cur = s;
    }

    if (cur->icmp.uid < 0 && uid >= 0 && cur->socket >= 0)
        cur->icmp.uid = uid;

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

    log_android(ANDROID_LOG_WARN,
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
    log_android(ANDROID_LOG_WARN,
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
