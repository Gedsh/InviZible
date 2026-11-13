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

int max_tun_msg = 0;
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
        uint8_t *buffer = get_packet_buffer(args, PACKET_INPUT, get_mtu(), "tun read");
        if (buffer == NULL) {
            report_exit(args, "Cannot allocate tunnel read buffer");
            return -1;
        }
        ssize_t length = read(args->tun, buffer, get_mtu());
        if (length < 0) {
            release_packet_buffer(args, buffer);

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

            release_packet_buffer(args, buffer);
        } else {
            // tun eof
            release_packet_buffer(args, buffer);

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

static bool valid_transport_checksum(uint8_t version, uint8_t protocol,
                                      const void *source, const void *dest,
                                      const uint8_t *payload, size_t length) {
    _Alignas(8) uint8_t pseudo[40] = {0};
    size_t pseudo_length = 0;
    if (protocol != IPPROTO_ICMP) {
        if (version == 4) {
            memcpy(pseudo, source, 4);
            memcpy(pseudo + 4, dest, 4);
            pseudo[9] = protocol;
            uint16_t size = htons((uint16_t) length);
            memcpy(pseudo + 10, &size, sizeof(size));
            pseudo_length = 12;
        } else {
            memcpy(pseudo, source, 16);
            memcpy(pseudo + 16, dest, 16);
            uint32_t size = htonl((uint32_t) length);
            memcpy(pseudo + 32, &size, sizeof(size));
            pseudo[39] = protocol;
            pseudo_length = 40;
        }
    }
    uint16_t sum = calc_checksum(0, pseudo, pseudo_length);
    return calc_checksum(sum, payload, length) == UINT16_MAX;
}

void handle_ip(const struct arguments *args,
               const uint8_t *pkt, const size_t length,
               const int epoll_fd,
               int sessions, int maxsessions) {
    uint8_t protocol;
    void *saddr;
    void *daddr;
    char flags[10];
    char data[16];
    int flen = 0;
    uint8_t *payload;
    const void *checksum_source;
    const void *checksum_dest;

    if (length == 0)
        return;

    // Get protocol, addresses & payload
    uint8_t version = (*pkt) >> 4;
    if (version == 4) {
        if (length < sizeof(struct iphdr)) {
            log_android(ANDROID_LOG_WARN, "IP4 packet too short length %zu", length);
            return;
        }

        struct iphdr *ip4hdr = (struct iphdr *) pkt;

        protocol = ip4hdr->protocol;
        saddr = &ip4hdr->saddr;
        daddr = &ip4hdr->daddr;
        checksum_source = saddr;
        checksum_dest = daddr;

        if (ntohs(ip4hdr->frag_off) & (IP_MF | IP_OFFMASK)) {
            // Fragment reassembly is not supported.
            log_android(ANDROID_LOG_WARN, "IP4 fragmented packet");
            return;
        }

        size_t header_length = (size_t) ip4hdr->ihl * 4;
        if (header_length < sizeof(struct iphdr) || header_length > length) {
            log_android(ANDROID_LOG_WARN, "IP4 invalid header length %zu", header_length);
            return;
        }
        payload = (uint8_t *) (pkt + header_length);

        if (ntohs(ip4hdr->tot_len) != length) {
            log_android(ANDROID_LOG_ERROR, "IP4 packet length %zu differs from total length %u",
                        length, ntohs(ip4hdr->tot_len));
            return;
        }

        if (calc_checksum(0, pkt, header_length) != UINT16_MAX) {
            log_android(ANDROID_LOG_WARN, "Invalid IP4 header checksum");
            return;
        }
        // Source routing uses the final destination in the transport pseudo-header.
        for (size_t off = sizeof(struct iphdr); off < header_length;) {
            uint8_t kind = pkt[off];
            if (kind == 0) break;
            if (kind == 1) { off++; continue; }
            if (header_length - off < 2 || pkt[off + 1] < 2 || pkt[off + 1] > header_length - off)
                return;
            size_t size = pkt[off + 1];
            if (kind == 131 || kind == 137) { // Loose/strict source route.
                if (size < 7 || (size - 3) % 4 || pkt[off + 2] < 4 ||
                    pkt[off + 2] > size + 1 || (pkt[off + 2] - 4) % 4)
                    return;
                if (pkt[off + 2] <= size)
                    checksum_dest = pkt + off + size - 4;
            }
            off += size;
        }
    } else if (version == 6) {
        if (length < sizeof(struct ip6_hdr)) {
            log_android(ANDROID_LOG_WARN, "IP6 packet too short length %zu", length);
            return;
        }

        struct ip6_hdr *ip6hdr = (struct ip6_hdr *) pkt;
        checksum_source = &ip6hdr->ip6_src;
        checksum_dest = &ip6hdr->ip6_dst;

        if (ntohs(ip6hdr->ip6_plen) != length - sizeof(struct ip6_hdr)) {
            log_android(ANDROID_LOG_WARN, "IP6 invalid payload length");
            return;
        }

        // Skip complete extension headers, using each header's length format.
        size_t off = sizeof(struct ip6_hdr);
        protocol = ip6hdr->ip6_nxt;
        while (is_lower_layer(protocol)) {
            // ESP is encrypted and does not have a generic extension header.
            if (protocol == IPPROTO_ESP || length - off < 2) {
                log_android(ANDROID_LOG_WARN, "IP6 unsupported or truncated extension %u", protocol);
                return;
            }

            const uint8_t *ext = pkt + off;
            size_t ext_length;
            if (protocol == IPPROTO_FRAGMENT)
                ext_length = 8;
            else if (protocol == IPPROTO_AH)
                ext_length = ((size_t) ext[1] + 2) * 4;
            else
                ext_length = ((size_t) ext[1] + 1) * 8;

            if (ext_length > length - off ||
                (protocol == IPPROTO_AH && ext_length < 12)) {
                log_android(ANDROID_LOG_WARN, "IP6 invalid extension length %zu", ext_length);
                return;
            }
            if (protocol == IPPROTO_FRAGMENT &&
                (((uint16_t) ext[2] << 8 | ext[3]) & 0xFFF9)) {
                // Only atomic fragments can be handled without reassembly.
                log_android(ANDROID_LOG_WARN, "IP6 fragmented packet");
                return;
            }
            if (protocol == IPPROTO_ROUTING && ext[3] != 0) {
                // Only routing formats whose final destination is known can be checked.
                if (ext[2] == 0 || ext[2] == 2) {
                    if (ext_length < 24 || (ext_length - 8) % 16 ||
                        ext[3] > (ext_length - 8) / 16 || (ext[2] == 2 && ext_length != 24))
                        return;
                    checksum_dest = ext + ext_length - 16;
                } else if (ext[2] == 4) { // Segment Routing: Segment List[0] is the final destination.
                    if (ext_length < 24 || (size_t) ext[4] + 1 > (ext_length - 8) / 16 ||
                        ext[3] > (unsigned int) ext[4] + 1)
                        return;
                    checksum_dest = ext + 8;
                } else {
                    log_android(ANDROID_LOG_WARN, "IP6 unsupported routing type %u", ext[2]);
                    return;
                }
            }
            if (protocol == IPPROTO_DSTOPTS) {
                for (size_t pos = 2; pos < ext_length;) {
                    if (ext[pos] == 0) { pos++; continue; } // Pad1.
                    if (ext_length - pos < 2 || ext[pos + 1] > ext_length - pos - 2)
                        return;
                    if (ext[pos] == 201) { // Mobile IPv6 Home Address option.
                        if (ext[pos + 1] != 16) return;
                        checksum_source = ext + pos + 2;
                    }
                    pos += (size_t) ext[pos + 1] + 2;
                }
            }
            protocol = ext[0];
            off += ext_length;
        }

        saddr = &ip6hdr->ip6_src;
        daddr = &ip6hdr->ip6_dst;

        payload = (uint8_t *) (pkt + off);

        // IPv6 has no header checksum; validate the upper layer after extension headers.
    } else {
        log_android(ANDROID_LOG_ERROR, "Unknown version %d", version);
        return;
    }

    struct log_address source = {.version = version, .address = saddr};
    struct log_address dest = {.version = version, .address = daddr};

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
        if (!valid_transport_checksum(version, protocol, checksum_source, checksum_dest,
                                       payload, length - (payload - pkt))) {
            log_android(ANDROID_LOG_WARN, "Invalid ICMP%d checksum", version);
            return;
        }

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

        if (ntohs(udp->len) != length - (payload - pkt)) {
            log_android(ANDROID_LOG_WARN, "UDP invalid length");
            return;
        }

        sport = ntohs(udp->source);
        dport = ntohs(udp->dest);

        // IPv4 permits an omitted UDP checksum; IPv6 requires a nonzero checksum.
        if ((udp->check == 0 && version == 6) ||
            (udp->check != 0 && !valid_transport_checksum(version, protocol, checksum_source,
                                                         checksum_dest, payload, ntohs(udp->len)))) {
            log_android(ANDROID_LOG_WARN, "Invalid UDP%d checksum", version);
            return;
        }
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

        if (!valid_transport_checksum(version, protocol, checksum_source, checksum_dest,
                                       payload, length - (payload - pkt))) {
            log_android(ANDROID_LOG_WARN, "Invalid TCP%d checksum", version);
            return;
        }
    } else if (protocol != IPPROTO_HOPOPTS && protocol != IPPROTO_IGMP && protocol != IPPROTO_ESP)
        log_android(ANDROID_LOG_WARN, "Unknown protocol %d", protocol);

    flags[flen] = 0;

    struct udp_session_lookup udp_lookup = {0};
    bool udp_existing = false;
    if (protocol == IPPROTO_UDP) {
        if (dport == 53)
            udp_existing = !args->fwd53; // DNS policy shortcut, not a flow lookup.
        else {
            udp_lookup.session = find_udp_session(args, pkt, payload);
            udp_existing = udp_lookup.session != NULL;
        }
    }

    // Limit number of sessions
    if (sessions >= maxsessions) {
        if ((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) ||
            (protocol == IPPROTO_UDP && !udp_existing) ||
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
        (protocol == IPPROTO_UDP && !udp_existing) ||
        (protocol == IPPROTO_TCP && syn)) {
        if (args->ctx->sdk <= 28 || (compatibility_mode && can_filter)) { // Android 9 Pie
            uid = get_uid(version, protocol, saddr, sport, daddr, dport);
        } else {
            uid = get_uid_q(args, version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport);
        }

        if (uid < 0 && dport != 53) {
            uid = restore_uid(args,
                              version,
                              protocol,
                              saddr,
                              sport,
                              daddr,
                              dport,
                              format_log_address(&source),
                              format_log_address(&dest),
                              flags,
                              payload);
        }
    }

    log_android(ANDROID_LOG_DEBUG,
                "Packet v%d %s/%u > %s/%u proto %d flags %s uid %d",
                version, format_log_address(&source), sport, format_log_address(&dest), dport, protocol, flags, uid);

    // Check if allowed
    int allowed = 0;
    struct allowed *redirect = NULL;
    if (protocol == IPPROTO_UDP && udp_existing) {
        allowed = 1; // could be a lingering/blocked session
    } else if (protocol == IPPROTO_TCP
               && (!syn || (!args->fwd53 && uid == 0 && dport == 53))) {
        allowed = 1; // assume existing session
    } else if ((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6)
                && !is_icmp_supported(pkt, payload)) {
        //Only ICMP ping traffic can be routed through the Android VPN service
        log_android(ANDROID_LOG_WARN, "ICMP v%d p%d %s/%u uid %d not supported",
                    version, protocol, format_log_address(&dest), dport, uid);
    } else if (protocol != IPPROTO_TCP && protocol != IPPROTO_UDP
                && protocol != IPPROTO_ICMP && protocol != IPPROTO_ICMPV6) {
        log_android(ANDROID_LOG_ERROR, "PROTOCOL v%d p%d %s/%u uid %d not supported",
                    version, protocol, format_log_address(&dest), dport, uid);
    } else {
        jobject objPacket = create_packet(
                args, version, protocol, flags, format_log_address(&source), sport, format_log_address(&dest), dport, data, uid, 0);
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
            handle_udp_with_lookup(args, pkt, length, payload, uid, redirect, epoll_fd,
                                   dport == 53 ? NULL : &udp_lookup);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, uid, allowed, redirect, epoll_fd);
    } else {
        if (protocol == IPPROTO_UDP)
            block_udp(args, pkt, length, payload, uid);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, uid, allowed, redirect, epoll_fd);

        log_android(ANDROID_LOG_WARN, "Address v%d p%d %s/%u syn %d uid %d not allowed",
                    version, protocol, format_log_address(&dest), dport, syn, uid);
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
int uid_cache_capacity = 0;
struct uid_cache_entry *uid_cache = NULL;

#define UID_UPDATE_BUCKETS 1024
#define UID_UPDATE_NONE UINT16_MAX
#define UID_UPDATE_THRESHOLD 64
_Static_assert(UID_CACHE_MAX < UID_UPDATE_NONE, "UID update indices must fit in uint16_t");

// Scan-local indices survive cache reallocations and require no retained memory.
// The heap orders replacement by (time, slot); expired slots still win in slot order.
struct uid_cache_update {
    uint16_t bucket[UID_UPDATE_BUCKETS];
    uint16_t next[UID_CACHE_MAX];
    uint16_t heap[UID_CACHE_MAX];
    uint16_t position[UID_CACHE_MAX];
    int size;
    int expired;
};

static unsigned int uid_update_bucket(int version, int protocol, const uint8_t *saddr,
                                       uint16_t sport, const uint8_t *daddr, uint16_t dport) {
    uint32_t hash = 2166136261u ^ (uint32_t) protocol ^ ((uint32_t) version << 8);
    for (size_t off = 0; off < (version == 4 ? 4 : 16); off++) {
        hash = (hash ^ saddr[off]) * 16777619u;
        hash = (hash ^ daddr[off]) * 16777619u;
    }
    hash = (hash ^ (((uint32_t) sport << 16) | dport)) * 16777619u;
    hash ^= hash >> 16;
    hash *= 0x85ebca6bu;
    hash ^= hash >> 13;
    return hash & (UID_UPDATE_BUCKETS - 1);
}

static unsigned int uid_entry_bucket(int slot) {
    const struct uid_cache_entry *entry = &uid_cache[slot];
    return uid_update_bucket(entry->version, entry->protocol, entry->saddr,
                              entry->sport, entry->daddr, entry->dport);
}

static bool uid_older(uint16_t a, uint16_t b) {
    return uid_cache[a].time < uid_cache[b].time ||
           (uid_cache[a].time == uid_cache[b].time && a < b);
}

static void uid_heap_swap(struct uid_cache_update *update, int a, int b) {
    uint16_t slot = update->heap[a];
    update->heap[a] = update->heap[b];
    update->heap[b] = slot;
    update->position[update->heap[a]] = (uint16_t) a;
    update->position[slot] = (uint16_t) b;
}

static void uid_heap_fix(struct uid_cache_update *update, int pos) {
    while (pos > 0 && uid_older(update->heap[pos], update->heap[(pos - 1) / 2])) {
        int parent = (pos - 1) / 2;
        uid_heap_swap(update, pos, parent);
        pos = parent;
    }
    while (pos * 2 + 1 < update->size) {
        int child = pos * 2 + 1;
        if (child + 1 < update->size && uid_older(update->heap[child + 1], update->heap[child]))
            child++;
        if (!uid_older(update->heap[child], update->heap[pos]))
            break;
        uid_heap_swap(update, pos, child);
        pos = child;
    }
}

static struct uid_cache_update *new_uid_update(void) {
    struct uid_cache_update *update = ng_malloc(sizeof(*update), "uid update scratch");
    if (update == NULL)
        return NULL;
    memset(update->bucket, 0xff, sizeof(update->bucket));
    update->size = 0;
    update->expired = 0;
    for (int i = 0; i < uid_cache_size; i++) {
        unsigned int bucket = uid_entry_bucket(i);
        update->next[i] = update->bucket[bucket];
        update->bucket[bucket] = (uint16_t) i;
        update->heap[i] = update->position[i] = (uint16_t) i;
        update->size++;
        uid_heap_fix(update, i);
    }
    return update;
}

static void cache_uid(int version, int protocol, const uint8_t *saddr, uint16_t sport,
                      const uint8_t *daddr, uint16_t dport, jint uid, long now,
                      struct uid_cache_update *update) {
    size_t address_size = (version == 4 ? 4 : 16);
    int slot = -1;
    int oldest = 0;
    unsigned int bucket = 0;
    if (update != NULL) {
        bucket = uid_update_bucket(version, protocol, saddr, sport, daddr, dport);
        for (uint16_t i = update->bucket[bucket]; i != UID_UPDATE_NONE; i = update->next[i]) {
            const struct uid_cache_entry *entry = &uid_cache[i];
            if (entry->version == version && entry->protocol == protocol &&
                entry->sport == sport && entry->dport == dport &&
                memcmp(entry->saddr, saddr, address_size) == 0 &&
                memcmp(entry->daddr, daddr, address_size) == 0) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            while (update->expired < uid_cache_size &&
                   now - uid_cache[update->expired].time <= UID_MAX_AGE)
                update->expired++;
            if (update->expired < uid_cache_size)
                slot = update->expired;
        }
        if (update->size > 0)
            oldest = update->heap[0];
    } else {
        for (int i = 0; i < uid_cache_size; i++) {
            struct uid_cache_entry *entry = &uid_cache[i];
            if (entry->version == version && entry->protocol == protocol &&
                entry->sport == sport && entry->dport == dport &&
                memcmp(entry->saddr, saddr, address_size) == 0 &&
                memcmp(entry->daddr, daddr, address_size) == 0) {
                slot = i;
                break;
            }
            if (slot < 0 && now - entry->time > UID_MAX_AGE)
                slot = i;
            if (entry->time < uid_cache[oldest].time)
                oldest = i;
        }
    }

    if (slot < 0 && uid_cache_size < UID_CACHE_MAX) {
        if (uid_cache_size == uid_cache_capacity) {
            // Double small caches, then retain fewer than 64 spare entries.
            int capacity = (uid_cache_capacity == 0 ? 1 : uid_cache_capacity < 64
                            ? uid_cache_capacity * 2 : uid_cache_capacity + 64);
            if (capacity > UID_CACHE_MAX)
                capacity = UID_CACHE_MAX;
            size_t bytes = sizeof(struct uid_cache_entry) * capacity;
            struct uid_cache_entry *resized = (uid_cache == NULL
                    ? ng_malloc(bytes, "uid_cache init")
                    : ng_realloc(uid_cache, bytes, "uid_cache extend"));
            // Caching is optional: preserve the old allocation on failure.
            if (resized == NULL)
                return;
            uid_cache = resized;
            uid_cache_capacity = capacity;
        }
        slot = uid_cache_size++;
    }
    if (slot < 0)
        slot = oldest;

    if (update != NULL && slot < update->size) {
        uint16_t *link = &update->bucket[uid_entry_bucket(slot)];
        while (*link != UID_UPDATE_NONE && *link != slot)
            link = &update->next[*link];
        if (*link != UID_UPDATE_NONE)
            *link = update->next[slot];
    }
    struct uid_cache_entry *entry = &uid_cache[slot];
    entry->version = (uint8_t) version;
    entry->protocol = (uint8_t) protocol;
    memcpy(entry->saddr, saddr, address_size);
    entry->sport = sport;
    memcpy(entry->daddr, daddr, address_size);
    entry->dport = dport;
    entry->uid = uid;
    entry->time = now;
    if (update != NULL) {
        update->next[slot] = update->bucket[bucket];
        update->bucket[bucket] = (uint16_t) slot;
        if (slot == update->size) {
            update->heap[slot] = update->position[slot] = (uint16_t) slot;
            update->size++;
        }
        uid_heap_fix(update, update->position[slot]);
    }
}

static const char *proc_space(const char *p) {
    while (isspace((unsigned char) *p))
        p++;
    return p;
}

static int proc_digit(unsigned char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

static bool proc_number(const char **cursor, unsigned int base, uint64_t limit,
                        uint64_t *value) {
    const char *p = *cursor;
    uint64_t result = 0;
    const uint64_t cutoff = limit / base;
    const unsigned int last_digit = (unsigned int) (limit % base);
    unsigned int count = 0;
    int digit;
    while ((digit = proc_digit((unsigned char) *p)) >= 0 && (unsigned int) digit < base) {
        if (result > cutoff || (result == cutoff && (unsigned int) digit > last_digit))
            return false;
        result = result * base + (unsigned int) digit;
        count++;
        p++;
    }
    if (count == 0)
        return false;
    *cursor = p;
    *value = result;
    return true;
}

static bool proc_field(const char **cursor, unsigned int base, uint64_t limit, char delimiter) {
    uint64_t ignored;
    const char *p = proc_space(*cursor);
    if (!proc_number(&p, base, limit, &ignored))
        return false;
    if (delimiter == ':' ? *p != ':' : !isspace((unsigned char) *p))
        return false;
    *cursor = (delimiter == ':' ? p + 1 : p);
    return true;
}

static bool proc_endpoint(const char **cursor, int words, uint8_t *address, uint16_t *port) {
    const char *p = proc_space(*cursor);
    for (int w = 0; w < words; w++) {
        uint32_t word = 0;
        for (int n = 0; n < 8; n++) {
            int digit = proc_digit((unsigned char) *p);
            if (digit < 0)
                return false;
            word = (word << 4) | (unsigned int) digit;
            p++;
        }
        // Proc addresses are printed as native-endian 32-bit words. memcpy also
        // keeps caller byte arrays safe on ARM when they are not word-aligned.
        memcpy(address + w * 4, &word, sizeof(word));
    }
    if (*p++ != ':')
        return false;
    uint64_t number;
    if (!proc_number(&p, 16, UINT16_MAX, &number) || !isspace((unsigned char) *p))
        return false;
    *port = (uint16_t) number;
    *cursor = p;
    return true;
}

static bool parse_proc_uid(const char *line, int words, uint8_t *saddr, uint16_t *sport,
                           uint8_t *daddr, uint16_t *dport, jint *uid) {
    const char *p = line;
    if (!proc_field(&p, 10, UINT32_MAX, ':') ||
        !proc_endpoint(&p, words, saddr, sport) || !proc_endpoint(&p, words, daddr, dport) ||
        !proc_field(&p, 16, UINT32_MAX, ' ') ||
        !proc_field(&p, 16, UINT64_MAX, ':') || !proc_field(&p, 16, UINT64_MAX, ' ') ||
        !proc_field(&p, 16, UINT32_MAX, ':') || !proc_field(&p, 16, UINT32_MAX, ' ') ||
        !proc_field(&p, 16, UINT32_MAX, ' '))
        return false;
    p = proc_space(p);
    bool negative = (*p == '-');
    if (*p == '-' || *p == '+') p++;
    uint64_t value;
    if (!proc_number(&p, 10, negative ? (uint64_t) INT32_MAX + 1 : INT32_MAX, &value) ||
        (*p != 0 && !isspace((unsigned char) *p)))
        return false;
    *uid = (jint) (negative ? -(int64_t) value : (int64_t) value);
    return true;
}

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
    else if (protocol == IPPROTO_ICMPV6 && version == 6 && !access("/proc/net/icmp6", F_OK))
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
    uint8_t _saddr[16];
    uint16_t _sport;
    uint8_t _daddr[16];
    uint16_t _dport;

    jint _uid;

    // Scan proc file
    struct uid_cache_update *update = NULL;
    bool update_attempted = false;
    unsigned int updates = 0;
    int l = 0;
    *line = 0;
    while (fgets(line, sizeof(line), fd) != NULL) {
        if (!l++)
            continue;

        if (parse_proc_uid(line, ws, _saddr, &_sport, _daddr, &_dport, &_uid)) {
            if (_sport == sport &&
                (_dport == dport || _dport == 0) &&
                (memcmp(_saddr, saddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_saddr, zero, (size_t) (ws * 4)) == 0) &&
                (memcmp(_daddr, daddr, (size_t) (ws * 4)) == 0 ||
                 memcmp(_daddr, zero, (size_t) (ws * 4)) == 0))
                uid = _uid;

            // A large accumulated cache can still have a one-row proc table.
            // Amortize scratch setup over a substantial scan, not just its cache size.
            if (updates < UID_UPDATE_THRESHOLD)
                updates++;
            if (!update_attempted && updates >= UID_UPDATE_THRESHOLD &&
                uid_cache_size >= UID_UPDATE_THRESHOLD) {
                update_attempted = true;
                update = new_uid_update();
            }
            cache_uid(version, protocol, _saddr, (uint16_t) _sport,
                      _daddr, (uint16_t) _dport, _uid, now, update);
        } else {
            log_android(ANDROID_LOG_ERROR, "Invalid proc UID row: %s", line);
            ng_free(update, __FILE__, __LINE__);
            fclose(fd);
            return -2;
        }
    }

    ng_free(update, __FILE__, __LINE__);
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

        if (icmp->icmp_type != ICMP_ECHO && icmp->icmp_type != 128 /* ICMP_ECHOV6 */) {
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
