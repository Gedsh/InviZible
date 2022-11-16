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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

#include "invizible.h"

int max_tun_msg = 0;
extern int loglevel;
extern int own_uid;
extern bool compatibility_mode;
extern bool can_filter;

uint16_t get_mtu() {
    //return 10000;
    return 1500;
}

uint16_t get_default_mss(int version) {
    if (version == 4)
        return (uint16_t) (get_mtu() - sizeof(struct iphdr) - sizeof(struct tcphdr));
    else
        return (uint16_t) (get_mtu() - sizeof(struct ip6_hdr) - sizeof(struct tcphdr));
}

int check_tun(const struct arguments *args,
              const struct epoll_event *ev,
              const int epoll_fd,
              int sessions, int maxsessions) {
    // Check tun error
    if (ev->events & EPOLLERR) {
        log_android(ANDROID_LOG_ERROR, "tun %d exception", args->tun);
        if (fcntl(args->tun, F_GETFL) < 0) {
            log_android(ANDROID_LOG_ERROR, "fcntl tun %d F_GETFL error %d: %s",
                        args->tun, errno, strerror(errno));
            report_exit(args, "fcntl tun %d F_GETFL error %d: %s",
                        args->tun, errno, strerror(errno));
        } else
            report_exit(args, "tun %d exception", args->tun);
        return -1;
    }

    // Check tun read
    if (ev->events & EPOLLIN) {
        uint8_t *buffer = ng_malloc(get_mtu(), "tun read");
        ssize_t length = read(args->tun, buffer, get_mtu());
        if (length < 0) {
            ng_free(buffer, __FILE__, __LINE__);

            log_android(ANDROID_LOG_ERROR, "tun %d read error %d: %s",
                        args->tun, errno, strerror(errno));
            if (errno == EINTR || errno == EAGAIN)
                // Retry later
                return 0;
            else {
                report_exit(args, "tun %d read error %d: %s",
                            args->tun, errno, strerror(errno));
                return -1;
            }
        } else if (length > 0) {

            if (length > max_tun_msg) {
                max_tun_msg = length;
                log_android(ANDROID_LOG_WARN, "Maximum tun msg length %d", max_tun_msg);
            }

            // Handle IP from tun
            handle_ip(args, buffer, (size_t) length, epoll_fd, sessions, maxsessions);

            ng_free(buffer, __FILE__, __LINE__);
        } else {
            // tun eof
            ng_free(buffer, __FILE__, __LINE__);

            log_android(ANDROID_LOG_ERROR, "tun %d empty read", args->tun);
            report_exit(args, "tun %d empty read", args->tun);
            return -1;
        }
    }

    return 0;
}

// https://en.wikipedia.org/wiki/IPv6_packet#Extension_headers
// http://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml
int is_lower_layer(int protocol) {
    // No next header = 59
    return (protocol == 0 || // Hop-by-Hop Options
            protocol == 60 || // Destination Options (before routing header)
            protocol == 43 || // Routing
            protocol == 44 || // Fragment
            protocol == 51 || // Authentication Header (AH)
            protocol == 50 || // Encapsulating Security Payload (ESP)
            protocol == 60 || // Destination Options (before upper-layer header)
            protocol == 135); // Mobility
}

int is_upper_layer(int protocol) {
    return (protocol == IPPROTO_TCP ||
            protocol == IPPROTO_UDP ||
            protocol == IPPROTO_ICMP ||
            protocol == IPPROTO_ICMPV6);
}

void handle_ip(const struct arguments *args,
               const uint8_t *pkt, const size_t length,
               const int epoll_fd,
               int sessions, int maxsessions) {
    uint8_t protocol;
    void *saddr;
    void *daddr;
    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    char flags[10];
    char data[16];
    int flen = 0;
    uint8_t *payload;

    // Get protocol, addresses & payload
    uint8_t version = (*pkt) >> 4;
    if (version == 4) {
        if (length < sizeof(struct iphdr)) {
            log_android(ANDROID_LOG_WARN, "IP4 packet too short length %d", length);
            return;
        }

        struct iphdr *ip4hdr = (struct iphdr *) pkt;

        protocol = ip4hdr->protocol;
        saddr = &ip4hdr->saddr;
        daddr = &ip4hdr->daddr;

        if (ip4hdr->frag_off & IP_MF) {
            log_android(ANDROID_LOG_ERROR, "IP fragment offset %u",
                        (ip4hdr->frag_off & IP_OFFMASK) * 8);
            //return;
        }

        uint8_t ipoptlen = (uint8_t) ((ip4hdr->ihl - 5) * 4);
        payload = (uint8_t *) (pkt + sizeof(struct iphdr) + ipoptlen);

        if (ntohs(ip4hdr->tot_len) != length) {
            log_android(ANDROID_LOG_ERROR, "Invalid length %u header length %u",
                        length, ntohs(ip4hdr->tot_len));
            return;
        }

        if (loglevel < ANDROID_LOG_WARN) {
            if (!calc_checksum(0, (uint8_t *) ip4hdr, sizeof(struct iphdr))) {
                log_android(ANDROID_LOG_ERROR, "Invalid IP checksum");
                return;
            }
        }
    } else if (version == 6) {
        if (length < sizeof(struct ip6_hdr)) {
            log_android(ANDROID_LOG_WARN, "IP6 packet too short length %d", length);
            return;
        }

        struct ip6_hdr *ip6hdr = (struct ip6_hdr *) pkt;

        // Skip extension headers
        uint16_t off = 0;
        protocol = ip6hdr->ip6_nxt;
        if (!is_upper_layer(protocol)) {
            log_android(ANDROID_LOG_WARN, "IP6 extension %d", protocol);
            off = sizeof(struct ip6_hdr);
            struct ip6_ext *ext = (struct ip6_ext *) (pkt + off);
            while (is_lower_layer(ext->ip6e_nxt) && !is_upper_layer(protocol)) {
                protocol = ext->ip6e_nxt;
                log_android(ANDROID_LOG_WARN, "IP6 extension %d", protocol);

                off += (8 + ext->ip6e_len);
                ext = (struct ip6_ext *) (pkt + off);
            }
            if (!is_upper_layer(protocol)) {
                off = 0;
                protocol = ip6hdr->ip6_nxt;
                log_android(ANDROID_LOG_WARN, "IP6 final extension %d", protocol);
            }
        }

        saddr = &ip6hdr->ip6_src;
        daddr = &ip6hdr->ip6_dst;

        payload = (uint8_t *) (pkt + sizeof(struct ip6_hdr) + off);

        // TODO checksum
    } else {
        log_android(ANDROID_LOG_ERROR, "Unknown version %d", version);
        return;
    }

    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    // Get ports & flags
    int syn = 0;
    uint16_t sport = 0;
    uint16_t dport = 0;
    *data = 0;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) {
        if (length - (payload - pkt) < ICMP_MINLEN) {
            log_android(ANDROID_LOG_WARN, "ICMP packet too short");
            return;
        }

        struct icmp *icmp = (struct icmp *) payload;

        sprintf(data, "type %d/%d", icmp->icmp_type, icmp->icmp_code);

        // http://lwn.net/Articles/443051/
        sport = ntohs(icmp->icmp_id);
        dport = ntohs(icmp->icmp_id);

    } else if (protocol == IPPROTO_UDP) {
        if (length - (payload - pkt) < sizeof(struct udphdr)) {
            log_android(ANDROID_LOG_WARN, "UDP packet too short");
            return;
        }

        struct udphdr *udp = (struct udphdr *) payload;

        sport = ntohs(udp->source);
        dport = ntohs(udp->dest);

        // TODO checksum (IPv6)
    } else if (protocol == IPPROTO_TCP) {
        if (length - (payload - pkt) < sizeof(struct tcphdr)) {
            log_android(ANDROID_LOG_WARN, "TCP packet too short");
            return;
        }

        struct tcphdr *tcp = (struct tcphdr *) payload;

        sport = ntohs(tcp->source);
        dport = ntohs(tcp->dest);

        if (tcp->syn) {
            syn = 1;
            flags[flen++] = 'S';
        }
        if (tcp->ack)
            flags[flen++] = 'A';
        if (tcp->psh)
            flags[flen++] = 'P';
        if (tcp->fin)
            flags[flen++] = 'F';
        if (tcp->rst)
            flags[flen++] = 'R';

        // TODO checksum
    } else if (protocol != IPPROTO_HOPOPTS && protocol != IPPROTO_IGMP && protocol != IPPROTO_ESP)
        log_android(ANDROID_LOG_WARN, "Unknown protocol %d", protocol);

    flags[flen] = 0;

    // Limit number of sessions
    if (sessions >= maxsessions) {
        if ((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) ||
            (protocol == IPPROTO_UDP && !has_udp_session(args, pkt, payload)) ||
            (protocol == IPPROTO_TCP && syn)) {
            log_android(ANDROID_LOG_ERROR,
                        "%d of max %d sessions, dropping version %d protocol %d",
                        sessions, maxsessions, protocol, version);
            return;
        }
    }

    // Get uid
    jint uid = -1;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6 ||
        (protocol == IPPROTO_UDP && !has_udp_session(args, pkt, payload)) ||
        (protocol == IPPROTO_TCP && syn)) {
        if (args->ctx->sdk <= 28 || (compatibility_mode && can_filter)) { // Android 9 Pie
            uid = get_uid(version, protocol, saddr, sport, daddr, dport);

            if (uid < 0 && args->ctx->sdk < 21) {
                usleep(100000);
                uid = get_uid(version, protocol, saddr, sport, daddr, dport);
            }

        } else {
            uid = get_uid_q(args, version, protocol, source, sport, dest, dport);
        }

        if (uid < 0 && dport != 53) {
            uid = restore_uid(args,
                              version,
                              protocol,
                              saddr,
                              sport,
                              daddr,
                              dport,
                              source,
                              dest,
                              flags,
                              payload);
        }
    }

    log_android(ANDROID_LOG_DEBUG,
                "Packet v%d %s/%u > %s/%u proto %d flags %s uid %d",
                version, source, sport, dest, dport, protocol, flags, uid);

    // Check if allowed
    int allowed = 0;
    struct allowed *redirect = NULL;
    if (protocol == IPPROTO_UDP
        && has_udp_session(args, pkt, payload)) {
        allowed = 1; // could be a lingering/blocked session
    } else if (protocol == IPPROTO_TCP
               && (!syn || (!args->fwd53 && uid == 0 && dport == 53))) {
        allowed = 1; // assume existing session
    } else {
        jobject objPacket = create_packet(
                args, version, protocol, flags, source, sport, dest, dport, data, uid, 0);
        redirect = is_address_allowed(args, objPacket);
        allowed = (redirect != NULL);
        if (redirect != NULL && (*redirect->raddr == 0 || redirect->rport == 0))
            redirect = NULL;
    }

    // Handle allowed traffic
    if (allowed) {
        if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6)
            handle_icmp(args, pkt, length, payload, uid, epoll_fd);
        else if (protocol == IPPROTO_UDP)
            handle_udp(args, pkt, length, payload, uid, redirect, epoll_fd);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, uid, allowed, redirect, epoll_fd);
    } else {
        if (protocol == IPPROTO_UDP)
            block_udp(args, pkt, length, payload, uid);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, uid, allowed, redirect, epoll_fd);

        log_android(ANDROID_LOG_WARN, "Address v%d p%d %s/%u syn %d not allowed",
                    version, protocol, dest, dport, syn);
    }
}

jint get_uid(const int version, const int protocol,
             const void *saddr, const uint16_t sport,
             const void *daddr, const uint16_t dport) {
    jint uid = -1;

    char source[INET6_ADDRSTRLEN + 1];
    char dest[INET6_ADDRSTRLEN + 1];
    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));

    struct timeval time;
    gettimeofday(&time, NULL);
    long now = (time.tv_sec * 1000) + (time.tv_usec / 1000);

    // Check IPv6 table first
    if (version == 4) {
        int8_t saddr128[16];
        memset(saddr128, 0, 10);
        saddr128[10] = (uint8_t) 0xFF;
        saddr128[11] = (uint8_t) 0xFF;
        memcpy(saddr128 + 12, saddr, 4);

        int8_t daddr128[16];
        memset(daddr128, 0, 10);
        daddr128[10] = (uint8_t) 0xFF;
        daddr128[11] = (uint8_t) 0xFF;
        memcpy(daddr128 + 12, daddr, 4);

        uid = get_uid_sub(6, protocol, saddr128, sport, daddr128, dport, source, dest, now);
        log_android(ANDROID_LOG_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d as inet6",
                    version, protocol, source, sport, dest, dport, uid);
    }

    if (uid == -1) {
        uid = get_uid_sub(version, protocol, saddr, sport, daddr, dport, source, dest, now);
        log_android(ANDROID_LOG_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d fallback",
                    version, protocol, source, sport, dest, dport, uid);
    }

    if (uid == -1)
        log_android(ANDROID_LOG_WARN, "uid v%d p%d %s/%u > %s/%u => not found",
                    version, protocol, source, sport, dest, dport);
    else if (uid >= 0)
        log_android(ANDROID_LOG_INFO, "uid v%d p%d %s/%u > %s/%u => %d",
                    version, protocol, source, sport, dest, dport, uid);

    return uid;
}

int uid_cache_size = 0;
struct uid_cache_entry *uid_cache = NULL;

jint get_uid_sub(const int version, const int protocol,
                 const void *saddr, const uint16_t sport,
                 const void *daddr, const uint16_t dport,
                 const char *source, const char *dest,
                 long now) {
    // NETLINK is not available on Android due to SELinux policies :-(
    // http://stackoverflow.com/questions/27148536/netlink-implementation-for-the-android-ndk
    // https://android.googlesource.com/platform/system/sepolicy/+/master/private/app.te (netlink_tcpdiag_socket)

    static uint8_t zero[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    int ws = (version == 4 ? 1 : 4);

    // Check cache
    for (int i = 0; i < uid_cache_size; i++)
        if (now - uid_cache[i].time <= UID_MAX_AGE &&
            uid_cache[i].version == version &&
            uid_cache[i].protocol == protocol &&
            uid_cache[i].sport == sport &&
            (uid_cache[i].dport == dport || uid_cache[i].dport == 0) &&
            (memcmp(uid_cache[i].saddr, saddr, (size_t) (ws * 4)) == 0 ||
             memcmp(uid_cache[i].saddr, zero, (size_t) (ws * 4)) == 0) &&
            (memcmp(uid_cache[i].daddr, daddr, (size_t) (ws * 4)) == 0 ||
             memcmp(uid_cache[i].daddr, zero, (size_t) (ws * 4)) == 0)) {

            log_android(ANDROID_LOG_INFO, "uid v%d p%d %s/%u > %s/%u => %d (from cache)",
                        version, protocol, source, sport, dest, dport, uid_cache[i].uid);

            return uid_cache[i].uid;
        }

    // Get proc file name
    char *fn = NULL;
    if (protocol == IPPROTO_ICMP && version == 4)
        fn = "/proc/net/icmp";
    else if (protocol == IPPROTO_ICMPV6 && version == 6)
        fn = "/proc/net/icmp6";
    else if (protocol == IPPROTO_TCP)
        fn = (version == 4 ? "/proc/net/tcp" : "/proc/net/tcp6");
    else if (protocol == IPPROTO_UDP)
        fn = (version == 4 ? "/proc/net/udp" : "/proc/net/udp6");
    else
        return -1;

    // Open proc file
    FILE *fd = fopen(fn, "re");
    if (fd == NULL) {
        log_android(ANDROID_LOG_ERROR, "fopen %s error %d: %s", fn, errno, strerror(errno));
        return -2;
    }

    jint uid = -1;

    char line[250];
    int fields;

    char shex[16 * 2 + 1];
    uint8_t _saddr[16];
    int _sport;

    char dhex[16 * 2 + 1];
    uint8_t _daddr[16];
    int _dport;

    jint _uid;

    // Scan proc file
    int l = 0;
    *line = 0;
    int c = 0;
    const char *fmt = (version == 4
                       ? "%*d: %8s:%X %8s:%X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld"
                       : "%*d: %32s:%X %32s:%X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld");
    while (fgets(line, sizeof(line), fd) != NULL) {
        if (!l++)
            continue;

        fields = sscanf(line, fmt, shex, &_sport, dhex, &_dport, &_uid);
        if (fields == 5 && strlen(shex) == ws * 8 && strlen(dhex) == ws * 8) {
            hex2bytes(shex, _saddr);
            hex2bytes(dhex, _daddr);

            for (int w = 0; w < ws; w++)
                ((uint32_t *) _saddr)[w] = htonl(((uint32_t *) _saddr)[w]);

            for (int w = 0; w < ws; w++)
                ((uint32_t *) _daddr)[w] = htonl(((uint32_t *) _daddr)[w]);

            if (_sport == sport &&
                (_dport == dport || _dport == 0) &&
                (memcmp(_saddr, saddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_saddr, zero, (size_t) (ws * 4)) == 0) &&
                (memcmp(_daddr, daddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_daddr, zero, (size_t) (ws * 4)) == 0))
                uid = _uid;

            for (; c < uid_cache_size; c++)
                if (now - uid_cache[c].time > UID_MAX_AGE)
                    break;

            if (c >= uid_cache_size) {
                if (uid_cache_size == 0)
                    uid_cache = ng_malloc(sizeof(struct uid_cache_entry), "uid_cache init");
                else
                    uid_cache = ng_realloc(uid_cache,
                                           sizeof(struct uid_cache_entry) *
                                           (uid_cache_size + 1), "uid_cache extend");
                c = uid_cache_size;
                uid_cache_size++;
            }

            uid_cache[c].version = (uint8_t) version;
            uid_cache[c].protocol = (uint8_t) protocol;
            memcpy(uid_cache[c].saddr, _saddr, (size_t) (ws * 4));
            uid_cache[c].sport = (uint16_t) _sport;
            memcpy(uid_cache[c].daddr, _daddr, (size_t) (ws * 4));
            uid_cache[c].dport = (uint16_t) _dport;
            uid_cache[c].uid = _uid;
            uid_cache[c].time = now;
        } else {
            log_android(ANDROID_LOG_ERROR, "Invalid field #%d: %s", fields, line);
            return -2;
        }
    }

    if (fclose(fd))
        log_android(ANDROID_LOG_ERROR, "fclose %s error %d: %s", fn, errno, strerror(errno));

    return uid;
}

jint restore_uid(const struct arguments *args,
                 const int version,
                 const int protocol,
                 const void *saddr,
                 const uint16_t sport,
                 const void *daddr,
                 const uint16_t dport,
                 const char *source,
                 const char *dest,
                 const char *flags,
                 const uint8_t *payload) {

    struct ng_session *cur = args->ctx->ng_session;
    jint uid = -1;

    if (protocol == IPPROTO_TCP) {
        while (cur != NULL &&
               !(cur->protocol == IPPROTO_TCP &&
                 cur->tcp.uid >= 0 &&
                 cur->tcp.version == version &&
                 cur->tcp.dest == htons(dport) &&
                 (version == 4 ? cur->tcp.saddr.ip4 == *((int32_t *) saddr) &&
                                 cur->tcp.daddr.ip4 == *((int32_t *) daddr)
                               : memcmp(&cur->tcp.saddr.ip6, saddr, 16) == 0 &&
                                 memcmp(&cur->tcp.daddr.ip6, daddr, 16) == 0)))
            cur = cur->next;
        if (cur != NULL) {
            uid = cur->tcp.uid;
        }
    } else if (protocol == IPPROTO_UDP) {
        while (cur != NULL &&
               !(cur->protocol == IPPROTO_UDP &&
                 cur->udp.uid >= 0 &&
                 cur->udp.version == version &&
                 cur->udp.dest == htons(dport) &&
                 (version == 4 ? cur->udp.saddr.ip4 == *((int32_t *) saddr) &&
                                 cur->udp.daddr.ip4 == *((int32_t *) daddr)
                               : memcmp(&cur->udp.saddr.ip6, saddr, 16) == 0 &&
                                 memcmp(&cur->udp.daddr.ip6, daddr, 16) == 0)))
            cur = cur->next;
        if (cur != NULL) {
            uid = cur->udp.uid;
        }
    } else if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) {

        struct icmp *icmp = (struct icmp *) payload;

        if (icmp->icmp_type != ICMP_ECHO) {
            log_android(ANDROID_LOG_WARN, "ICMP type %d code %d from %s to %s not supported",
                        icmp->icmp_type, icmp->icmp_code, source, dest);
            return uid;
        }

        while (cur != NULL &&
               !((cur->protocol == IPPROTO_ICMP || cur->protocol == IPPROTO_ICMPV6) &&
                 cur->icmp.uid >= 0 &&
                 !cur->icmp.stop && cur->icmp.version == version &&
                 cur->icmp.id == icmp->icmp_id &&
                 (version == 4 ? cur->icmp.saddr.ip4 == *((int32_t *) saddr) &&
                                 cur->icmp.daddr.ip4 == *((int32_t *) daddr)
                               : memcmp(&cur->icmp.saddr.ip6, saddr, 16) == 0 &&
                                 memcmp(&cur->icmp.daddr.ip6, daddr, 16) == 0)))
            cur = cur->next;
        if (cur != NULL) {
            uid = cur->icmp.uid;
        }
    } else {
        return uid;
    }

    if (uid < 0) {
        log_android(ANDROID_LOG_ERROR,
                    "Packet uid can not be restored v%d %s/%u > %s/%u proto %d flags %s uid %d",
                    version, source, sport, dest, dport, protocol, flags, uid);
    } else {
        log_android(ANDROID_LOG_ERROR,
                    "Packet uid restored v%d %s/%u > %s/%u proto %d flags %s uid %d",
                    version, source, sport, dest, dport, protocol, flags, uid);
    }

    return uid;
}

#pragma clang diagnostic pop
