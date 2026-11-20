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

uint16_t get_mtu(void) {
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
                // Positive read length is bounded by get_mtu() (1500), so it fits in int.
                max_tun_msg = (int) length;
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
static int is_lower_layer(int protocol) {
    // No Next Header (59) is intentionally excluded.
    return (protocol == 0 || // Hop-by-Hop Options
            protocol == 60 || // Destination Options (before or after routing)
            protocol == 43 || // Routing
            protocol == 44 || // Fragment
            protocol == 51 || // Authentication Header (AH)
            protocol == 50 || // Encapsulating Security Payload (ESP)
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

static bool retry_udp_uid(struct ng_session *s) {
    if (s == NULL ||
        !((s->udp.state == UDP_ACTIVE && s->socket >= 0) ||
          (s->udp.state == UDP_BLOCKED && s->socket < 0)) || (s->udp.uid >= 0 && !s->udp.uid_inferred) ||
        s->udp.uid_retries >= UDP_UID_RETRY_MAX || s->udp.uid_retry_at < 0)
        return false;

    long long now = get_ms();
    if (now < 0) {
        s->udp.uid_retries = UDP_UID_RETRY_MAX;
        return false; // A broken clock must not turn every packet into a proc/JNI query.
    }
    if (now < s->udp.uid_retry_at)
        return false;

    s->udp.uid_retries++;
    s->udp.uid_retry_at = now + (UDP_UID_RETRY_INTERVAL << s->udp.uid_retries);
    return true;
}

static bool udp_route_matches(const struct udp_session *session, const struct allowed *redirect) {
    if (redirect == NULL)
        return session->redirect_version == 0;
    if (session->redirect_version == 0)
        return false;
    const char *target = redirect->raddr;
    if (session->version == 6 && strcmp(target, LOOPBACK_ADDRESS) == 0)
        target = LOOPBACK_ADDRESS_IPv6; // Match the UDP socket setup's normalization.
    int version = strchr(target, ':') == NULL ? 4 : 6;
    struct in6_addr address = {0};
    return session->redirect_version == version && session->redirect_port == htons(redirect->rport) &&
           inet_pton(version == 4 ? AF_INET : AF_INET6, target, &address) == 1 &&
           memcmp(&session->redirect_addr, &address, version == 4 ? 4 : 16) == 0;
}

static bool same_uid_destination(const struct uid_observation *entry, int version,
                                  const void *source, const void *dest, uint16_t port) {
    return entry->version == version && entry->port == port &&
           memcmp(entry->source, source, version == 4 ? 4 : 16) == 0 &&
           memcmp(entry->dest, dest, version == 4 ? 4 : 16) == 0;
}

static int64_t uid_cache_now(void);

static int64_t uid_observation_time(struct context *ctx) {
    struct uid_observations *cache = &ctx->uid_observations;
    // Evidence and overflow deadlines must age while the device is suspended.
    int64_t now = uid_cache_now();
    if (now < 0 || now < cache->time) {
        memset(cache, 0, sizeof(*cache));
        memset(&ctx->uid_dns_mappings, 0, sizeof(ctx->uid_dns_mappings));
        return -1;
    }
    cache->time = now;
    if (cache->count > 0 && cache->entry[cache->last].expires <= now) {
        // Every entry and discarded observation has expired. Ignore stale storage.
        cache->count = 0;
        cache->last = 0;
        cache->incomplete_until = 0;
    }
    return now;
}

// Only authoritative proc/Android lookups call this, before policy or socket setup.
void record_uid_observation(struct context *ctx, int version, int protocol,
                             const void *source, const void *dest, uint16_t port, jint uid) {
    if (uid < 0 || (version != 4 && version != 6) ||
        (protocol != IPPROTO_TCP && protocol != IPPROTO_UDP))
        return;
    struct uid_observations *cache = &ctx->uid_observations;
    int64_t now = uid_observation_time(ctx);
    if (now < 0)
        return;
    if (cache->count > 0) {
        struct uid_observation *recent = &cache->entry[cache->last];
        if (recent->uid == uid && recent->protocol == protocol &&
            same_uid_destination(recent, version, source, dest, port)) {
            recent->expires = now + UID_OBSERVATION_MS;
            return;
        }
    }
    struct uid_observation *slot = NULL, *oldest = &cache->entry[0];
    for (size_t i = 0; i < cache->count; i++) {
        struct uid_observation *entry = &cache->entry[i];
        if (entry->expires > now && entry->uid == uid && entry->protocol == protocol &&
            same_uid_destination(entry, version, source, dest, port)) {
            entry->expires = now + UID_OBSERVATION_MS;
            cache->last = i;
            return;
        }
        if (slot == NULL && entry->expires <= now)
            slot = entry;
        if (entry->expires < oldest->expires)
            oldest = entry;
    }
    if (slot == NULL && cache->count < UID_OBSERVATIONS)
        slot = &cache->entry[cache->count++];
    if (slot == NULL) {
        // Missing evidence could hide a conflict. Keep its ORIGINAL expiry,
        // rather than starting another full window at eviction or on inference.
        slot = oldest;
        if (slot->expires > cache->incomplete_until)
            cache->incomplete_until = slot->expires;
    }
    slot->expires = now + UID_OBSERVATION_MS;
    slot->uid = uid;
    slot->version = (uint8_t) version;
    slot->protocol = (uint8_t) protocol;
    slot->port = port;
    memcpy(slot->source, source, version == 4 ? 4 : 16);
    memcpy(slot->dest, dest, version == 4 ? 4 : 16);
    cache->last = (size_t) (slot - cache->entry);
}

static int64_t uid_dns_retained_until(const struct uid_dns_mapping *entry) {
    return entry->expires > entry->incomplete_until ? entry->expires : entry->incomplete_until;
}

static bool uid_dns_address_matches(const struct uid_dns_mapping *entry, int version, const void *address) {
    return entry->version == version && memcmp(entry->address, address, version == 4 ? 4 : 16) == 0;
}

static bool remember_uid_dns_loss(struct uid_dns_mappings *cache, const char *domain,
                                  int version, const void *address, int64_t expires, int64_t now) {
    if (expires <= now)
        return true;
    struct uid_dns_mapping *slot = NULL;
    for (size_t i = 0; i < cache->lost_count; i++) {
        struct uid_dns_mapping *entry = &cache->lost[i];
        if (strcmp(entry->domain, domain) == 0 && uid_dns_address_matches(entry, version, address)) {
            if (entry->expires < expires) entry->expires = expires;
            return true;
        }
        if (slot == NULL && entry->expires <= now)
            slot = entry;
    }
    if (slot == NULL && cache->lost_count < UID_DNS_LOSSES)
        slot = &cache->lost[cache->lost_count++];
    if (slot == NULL) {
        // Never discard a live loss marker. Preserve affected resident keys in
        // place and stop admitting new keys that could overlap untracked evidence.
        if (cache->admit_after < expires) cache->admit_after = expires;
        for (size_t i = 0; i < cache->count; i++) {
            struct uid_dns_mapping *entry = &cache->entry[i];
            if ((strcmp(entry->domain, domain) == 0 || uid_dns_address_matches(entry, version, address)) &&
                entry->incomplete_until < expires)
                entry->incomplete_until = expires;
        }
        return false;
    }
    slot->expires = expires;
    slot->version = (uint8_t) version;
    memcpy(slot->address, address, version == 4 ? 4 : 16);
    strcpy(slot->domain, domain);
    return true;
}

void discard_uid_dns_answer(struct context *ctx, const char *domain, const struct uid_dns_answer *answer) {
    if (ctx == NULL || domain == NULL || *domain == 0 || strnlen(domain, 256) == 256 ||
        answer == NULL || (answer->version != 4 && answer->version != 6))
        return;
    int64_t now = uid_observation_time(ctx);
    if (now < 0)
        return;
    uint32_t ttl = answer->ttl > UID_DNS_MAX_TTL ? UID_DNS_MAX_TTL : answer->ttl;
    remember_uid_dns_loss(&ctx->uid_dns_mappings, domain, answer->version, answer->address,
                          now + (int64_t) ttl * 1000, now);
}

void record_uid_dns_answers(struct context *ctx, const char *domain,
                            const struct uid_dns_answer *answers, size_t count) {
    if (ctx == NULL || domain == NULL || *domain == 0 || strnlen(domain, 256) == 256 ||
        answers == NULL || count == 0 || count > UID_DNS_ANSWERS)
        return;
    for (size_t i = 0; i < count; i++)
        if (answers[i].version != 4 && answers[i].version != 6)
            return;
    int64_t now = uid_observation_time(ctx);
    if (now < 0)
        return;
    struct uid_dns_mappings *cache = &ctx->uid_dns_mappings;
    for (size_t a = 0; a < count; a++) {
        const struct uid_dns_answer *answer = &answers[a];
        uint32_t ttl = answer->ttl;
        for (size_t d = 0; d < count; d++)
            if (answers[d].version == answer->version && answers[d].ttl < ttl &&
                memcmp(answers[d].address, answer->address, answer->version == 4 ? 4 : 16) == 0)
                ttl = answers[d].ttl;
        struct uid_dns_mapping *slot = NULL, *oldest = &cache->entry[0];
        bool found = false;
        for (size_t i = 0; i < cache->count; i++) {
            struct uid_dns_mapping *entry = &cache->entry[i];
            if (entry->version == answer->version && strcmp(entry->domain, domain) == 0 &&
                memcmp(entry->address, answer->address, answer->version == 4 ? 4 : 16) == 0) {
                slot = entry;
                found = true;
                break;
            }
            if (slot == NULL && uid_dns_retained_until(entry) <= now)
                slot = entry;
            if (uid_dns_retained_until(entry) < uid_dns_retained_until(oldest))
                oldest = entry;
        }
        if (ttl == 0) {
            if (found) slot->expires = now;
            continue;
        }
        if (ttl > UID_DNS_MAX_TTL) ttl = UID_DNS_MAX_TTL;
        int64_t expires = now + (int64_t) ttl * 1000;
        if (!found && now < cache->admit_after) {
            remember_uid_dns_loss(cache, domain, answer->version, answer->address, expires, now);
            continue;
        }
        if (slot == NULL && cache->count < UID_DNS_MAPPINGS)
            slot = &cache->entry[cache->count++];
        if (slot == NULL) {
            if (!remember_uid_dns_loss(cache, oldest->domain, oldest->version, oldest->address,
                                       uid_dns_retained_until(oldest), now)) {
                remember_uid_dns_loss(cache, domain, answer->version, answer->address, expires, now);
                continue;
            }
            slot = oldest;
        }
        if (!found) slot->incomplete_until = 0;
        slot->expires = expires;
        slot->version = answer->version;
        memcpy(slot->address, answer->address, answer->version == 4 ? 4 : 16);
        strcpy(slot->domain, domain);
    }
}

static jint infer_uid_domain(const struct context *ctx, int version,
                              const void *source, const void *dest, uint16_t port, int64_t now) {
    const struct uid_dns_mappings *dns = &ctx->uid_dns_mappings;
    const char *domain = NULL;
    for (size_t i = 0; i < dns->count; i++) {
        const struct uid_dns_mapping *entry = &dns->entry[i];
        if (entry->expires <= now || entry->version != version ||
            memcmp(entry->address, dest, version == 4 ? 4 : 16) != 0)
            continue;
        // Shared addresses do not identify which hostname this connection uses.
        if (domain != NULL && strcmp(domain, entry->domain) != 0) {
            log_android(ANDROID_LOG_ERROR, "UID inference declined: dns_ambiguous names=%s,%s", domain, entry->domain);
            return -1;
        }
        domain = entry->domain;
    }
    if (domain == NULL) {
        // Temporary diagnostics: the UI can retain a name after native evidence expires.
        const struct uid_dns_mapping *expired = NULL;
        for (size_t i = 0; i < dns->count; i++) {
            const struct uid_dns_mapping *entry = &dns->entry[i];
            if (entry->expires <= now && uid_dns_address_matches(entry, version, dest) &&
                (expired == NULL || entry->expires > expired->expires))
                expired = entry;
        }
        log_android(ANDROID_LOG_ERROR,
                    "UID inference declined: dns_mapping_missing mappings=%zu losses=%zu admission_ms=%jd expired_name=%s expired_ms=%jd",
                    dns->count, dns->lost_count, (intmax_t) (dns->admit_after > now ? dns->admit_after - now : 0),
                    expired == NULL ? "-" : expired->domain, (intmax_t) (expired == NULL ? -1 : now - expired->expires));
        return -1;
    }
    for (size_t i = 0; i < dns->lost_count; i++) {
        const struct uid_dns_mapping *entry = &dns->lost[i];
        if (entry->expires > now &&
            (strcmp(entry->domain, domain) == 0 || uid_dns_address_matches(entry, version, dest))) {
            log_android(ANDROID_LOG_ERROR,
                        "UID inference declined: dns_discarded domain=%s lost_domain=%s scope=%s remaining_ms=%jd losses=%zu",
                        domain, entry->domain, strcmp(entry->domain, domain) == 0 ? "domain" : "address",
                        (intmax_t) (entry->expires - now), dns->lost_count);
            return -1;
        }
    }
    for (size_t i = 0; i < dns->count; i++) {
        const struct uid_dns_mapping *entry = &dns->entry[i];
        if (entry->incomplete_until > now &&
            (strcmp(entry->domain, domain) == 0 || uid_dns_address_matches(entry, version, dest))) {
            log_android(ANDROID_LOG_ERROR, "UID inference declined: dns_incomplete domain=%s remaining_ms=%jd",
                        domain, (intmax_t) (entry->incomplete_until - now));
            return -1;
        }
    }
    jint uid = -1;
    for (size_t i = 0; i < ctx->uid_observations.count; i++) {
        const struct uid_observation *owner = &ctx->uid_observations.entry[i];
        if (owner->expires <= now || owner->port != port ||
            (owner->version == version && memcmp(owner->source, source, version == 4 ? 4 : 16) != 0))
            continue;
        // Cross-family evidence is scoped to the VPN context, not a DNS socket UID.
        for (size_t d = 0; d < dns->count; d++) {
            const struct uid_dns_mapping *entry = &dns->entry[d];
            if (entry->expires > now && entry->version == owner->version &&
                memcmp(entry->address, owner->dest, owner->version == 4 ? 4 : 16) == 0 &&
                strcmp(domain, entry->domain) == 0) {
                if (uid >= 0 && uid != owner->uid) {
                    log_android(ANDROID_LOG_ERROR, "UID inference declined: dns_owner_conflict domain=%s uids=%d,%d",
                                domain, uid, owner->uid);
                    return -1;
                }
                uid = owner->uid;
                break;
            }
        }
    }
    if (uid < 0)
        log_android(ANDROID_LOG_ERROR, "UID inference declined: dns_owner_missing domain=%s observations=%zu",
                    domain, ctx->uid_observations.count);
    else if (uid < 10000 || uid == own_uid)
        log_android(ANDROID_LOG_ERROR, "UID inference declined: dns_owner_excluded domain=%s uid=%d", domain, uid);
    return uid >= 10000 && uid != own_uid ? uid : -1;
}

jint infer_uid_destination(struct context *ctx, int version, int protocol,
                            const void *source, const void *dest, uint16_t port) {
    if ((version != 4 && version != 6) || (protocol != IPPROTO_TCP && protocol != IPPROTO_UDP))
        return -1;
    struct uid_observations *cache = &ctx->uid_observations;
    if (cache->count == 0) {
        log_android(ANDROID_LOG_ERROR, "UID inference declined: uid_evidence_empty");
        return -1;
    }
    int64_t now = uid_observation_time(ctx);
    if (now < 0 || cache->count == 0) {
        log_android(ANDROID_LOG_ERROR, "UID inference declined: %s", now < 0 ? "clock_failure" : "uid_evidence_expired");
        return -1;
    }
    if (now < cache->incomplete_until) {
        log_android(ANDROID_LOG_ERROR, "UID inference declined: uid_evidence_overflow observations=%zu remaining_ms=%jd",
                    cache->count, (intmax_t) (cache->incomplete_until - now));
        return -1;
    }
    jint uid = -1;
    // Share exact endpoint evidence across TCP/UDP, including conflicting owners.
    // Observations retain independent expiry for each confirmed transport.
    for (size_t i = 0; i < cache->count; i++) {
        const struct uid_observation *entry = &cache->entry[i];
        if (entry->expires <= now || !same_uid_destination(entry, version, source, dest, port))
            continue;
        if (uid >= 0 && uid != entry->uid) {
            log_android(ANDROID_LOG_ERROR, "UID inference declined: ip_owner_conflict uids=%d,%d", uid, entry->uid);
            return -1;
        }
        uid = entry->uid;
    }
    if (uid >= 0) {
        if (uid < 10000 || uid == own_uid)
            log_android(ANDROID_LOG_ERROR, "UID inference declined: ip_owner_excluded uid=%d", uid);
        return uid >= 10000 && uid != own_uid ? uid : -1;
    }
    return infer_uid_domain(ctx, version, source, dest, port, now);
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
    size_t payload_offset;
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
        payload_offset = header_length;

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

        payload_offset = off;

        // IPv6 has no header checksum; validate the upper layer after extension headers.
    } else {
        log_android(ANDROID_LOG_ERROR, "Unknown version %d", version);
        return;
    }

    // Both IP parsers have validated the offset against the packet length.
    const uint8_t *payload = pkt + payload_offset;
    const size_t payload_length = length - payload_offset;
    struct log_address source = {.version = version, .address = saddr};
    struct log_address dest = {.version = version, .address = daddr};

    // Get ports & flags
    int syn = 0;
    uint16_t sport = 0;
    uint16_t dport = 0;
    *data = 0;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) {
        if (payload_length < ICMP_MINLEN) {
            log_android(ANDROID_LOG_WARN, "ICMP packet too short");
            return;
        }

        struct icmp *icmp = (struct icmp *) payload;
        if (!valid_transport_checksum(version, protocol, checksum_source, checksum_dest,
                                       payload, payload_length)) {
            log_android(ANDROID_LOG_WARN, "Invalid ICMP%d checksum", version);
            return;
        }

        sprintf(data, "type %d/%d", icmp->icmp_type, icmp->icmp_code);

        // http://lwn.net/Articles/443051/
        sport = ntohs(icmp->icmp_id);
        dport = ntohs(icmp->icmp_id);

    } else if (protocol == IPPROTO_UDP) {
        if (payload_length < sizeof(struct udphdr)) {
            log_android(ANDROID_LOG_WARN, "UDP packet too short");
            return;
        }

        struct udphdr *udp = (struct udphdr *) payload;

        if (ntohs(udp->len) != payload_length) {
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
        if (payload_length < sizeof(struct tcphdr)) {
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
                                       payload, payload_length)) {
            log_android(ANDROID_LOG_WARN, "Invalid TCP%d checksum", version);
            return;
        }
    } else if (protocol != IPPROTO_HOPOPTS && protocol != IPPROTO_IGMP && protocol != IPPROTO_ESP)
        log_android(ANDROID_LOG_WARN, "Unknown protocol %d", protocol);

    flags[flen] = 0;

    struct udp_session_lookup udp_lookup = {0};
    bool udp_existing = false;
    bool udp_uid_retry = false;
    if (protocol == IPPROTO_UDP) {
        udp_lookup.session = find_udp_session(args, pkt, payload);
        udp_existing = udp_lookup.session != NULL;
        // DNS resolves ownership and checks policy for every request in either mode.
        // Other flows retry unknown owners with bounded, traffic-driven backoff.
        if (dport != 53) {
            // Promoting a blocked record opens a socket, unlike updating a live flow.
            if (udp_existing &&
                (udp_lookup.session->udp.state != UDP_BLOCKED || sessions < maxsessions))
                udp_uid_retry = retry_udp_uid(udp_lookup.session);
        }
    }

    // Limit number of sessions
    if (sessions >= maxsessions) {
        // A valid retransmitted SYN can recover ownership without opening another socket.
        bool tcp_existing = false;
        if (protocol == IPPROTO_TCP && syn) {
            const struct tcphdr *tcp = (const struct tcphdr *) payload;
            size_t header_length = (size_t) tcp->doff * 4;
            if (!tcp->ack && !tcp->rst && !tcp->fin && !tcp->urg &&
                header_length >= sizeof(*tcp) && header_length <= payload_length) {
                struct ng_session *session = find_session(args->ctx, version, protocol,
                                                          saddr, tcp->source, daddr, tcp->dest);
                tcp_existing = session != NULL && session->socket >= 0 &&
                               session->tcp.state != TCP_CLOSING && session->tcp.state != TCP_CLOSE &&
                               session->tcp.remote_start == ntohl(tcp->seq);
            }
        }
        // Existing live ping flows need no additional session or socket.
        bool icmp_existing = false;
        if ((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) &&
            is_icmp_supported(pkt, payload)) {
            const struct icmp *icmp = (const struct icmp *) payload;
            struct ng_session *session = find_session(args->ctx, version, protocol,
                                                      saddr, icmp->icmp_id, daddr, 0);
            icmp_existing = session != NULL && session->socket >= 0;
        }
        if (((protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) && !icmp_existing) ||
            (protocol == IPPROTO_UDP && !udp_existing) ||
            (protocol == IPPROTO_TCP && syn && !tcp_existing)) {
            log_android(ANDROID_LOG_ERROR,
                        "%d of max %d sessions, dropping version %d protocol %d",
                        sessions, maxsessions, protocol, version);
            return;
        }
    }

    // Get uid
    jint uid = -1;
    bool inferred = false;
    if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6 ||
        (protocol == IPPROTO_UDP && (dport == 53 || !udp_existing || udp_uid_retry)) ||
        (protocol == IPPROTO_TCP && syn)) {
        if (args->ctx->sdk <= 28 || (compatibility_mode && can_filter)) { // Android 9 Pie
            uid = get_uid(version, protocol, saddr, sport, daddr, dport);
        } else {
            uid = get_uid_q(args, version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport);
        }

        record_uid_observation(args->ctx, version, protocol, saddr, daddr, dport, uid);
        if (uid < 0 && !udp_uid_retry) {
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
        if (uid < 0 && udp_existing && udp_lookup.session->udp.uid_inferred &&
            udp_lookup.session->udp.uid >= 0) {
            // A failed recovery does not replace this flow's provisional owner.
            // Observation expiry only limits inference for other flows.
            uid = udp_lookup.session->udp.uid;
            inferred = true;
        }
        if (uid < 0) {
            uid = infer_uid_destination(args->ctx, version, protocol, saddr, daddr, dport);
            inferred = uid >= 0;
        }
        if (uid < 0 && (protocol == IPPROTO_TCP || protocol == IPPROTO_UDP || is_icmp_supported(pkt, payload)))
            log_android(ANDROID_LOG_ERROR,
                    "Packet uid can not be restored v%d %s/%u > %s/%u proto %d flags %s uid %d",
                    version, format_log_address(&source), sport, format_log_address(&dest), dport, protocol, flags, uid);
    }

    // Retain a newly learned owner even if policy denies this packet or retires the flow.
    if (uid >= 0 && udp_existing && (udp_lookup.session->udp.uid < 0 || udp_lookup.session->udp.uid_inferred) &&
        (udp_uid_retry || (dport == 53 && udp_lookup.session->udp.state == UDP_ACTIVE &&
                          udp_lookup.session->socket >= 0))) {
        udp_lookup.session->udp.uid = uid;
        udp_lookup.session->udp.uid_inferred = inferred;
    }
    if (uid >= 0 && (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) &&
        is_icmp_supported(pkt, payload)) {
        const struct icmp *icmp = (const struct icmp *) payload;
        struct ng_session *session = find_session(args->ctx, version, protocol,
                                                  saddr, icmp->icmp_id, daddr, 0);
        if (session != NULL && session->icmp.uid < 0 && session->socket >= 0)
            session->icmp.uid = uid;
    }
    if (udp_uid_retry && (uid < 0 || inferred)) {
        udp_lookup.preserve_uid_retry = true;
        udp_lookup.uid_retry_at = udp_lookup.session->udp.uid_retry_at;
        udp_lookup.uid_retries = udp_lookup.session->udp.uid_retries;
    }

    log_android(ANDROID_LOG_DEBUG,
                "Packet v%d %s/%u > %s/%u proto %d flags %s uid %d",
                version, format_log_address(&source), sport, format_log_address(&dest), dport, protocol, flags, uid);

    // Check if allowed
    int allowed = 0;
    struct allowed *redirect = NULL;
    if (protocol == IPPROTO_UDP && dport != 53 && udp_existing && !udp_uid_retry) {
        allowed = 1; // could be a lingering/blocked session
    } else if (protocol == IPPROTO_TCP && !syn) {
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

    if (protocol == IPPROTO_UDP && udp_existing &&
        udp_lookup.session->udp.state == UDP_BLOCKED && (udp_uid_retry || dport == 53)) {
        if (allowed) {
            // Retain a resolved owner as metadata even if reopening must wait.
            if (uid >= 0 && (udp_lookup.session->udp.uid < 0 || udp_lookup.session->udp.uid_inferred)) {
                udp_lookup.session->udp.uid = uid;
                udp_lookup.session->udp.uid_inferred = inferred;
            }
            if (sessions >= maxsessions)
                return; // Replacing a socketless block requires a new active slot.
            // This record has no epoll registration. Retain its resolved owner for
            // accounting/cleanup while forwarding creates a fresh active record.
            udp_lookup.session->udp.state = UDP_CLOSED;
            udp_lookup.session->udp.time = time(NULL);
            unindex_session(args->ctx, udp_lookup.session);
            udp_lookup.session = NULL;
        } else if (udp_uid_retry && uid < 0) {
            // Keep the retry deadline/budget when policy still denies an unknown owner.
            write_connection_unreach(args, udp_lookup.session, EHOSTDOWN);
            return;
        }
    } else if ((udp_uid_retry &&
                (!allowed || !udp_route_matches(&udp_lookup.session->udp, redirect))) ||
               (protocol == IPPROTO_UDP && dport == 53 && !allowed && udp_existing &&
                udp_lookup.session->udp.state == UDP_ACTIVE)) {
        // Preserve sockets (and their source ports) when the permitted route is unchanged.
        // Retired records stay alive until cleanup for queued epoll event pointers.
        if (epoll_ctl(epoll_fd, EPOLL_CTL_DEL, udp_lookup.session->socket, NULL))
            log_android(ANDROID_LOG_ERROR, "epoll del udp UID recovery error %d: %s",
                        errno, strerror(errno));
        udp_lookup.session->udp.state = UDP_FINISHING;
        unindex_session(args->ctx, udp_lookup.session);
        udp_lookup.session = NULL;
    }

    // Handle allowed traffic
    struct packet_owner owner = {.uid = uid, .inferred = inferred};
    if (allowed) {
        if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6)
            handle_icmp(args, pkt, length, payload, uid, epoll_fd);
        else if (protocol == IPPROTO_UDP)
            handle_udp_with_lookup(args, pkt, length, payload, owner, redirect, epoll_fd,
                                   &udp_lookup);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, owner, allowed, redirect, epoll_fd);
    } else {
        if (protocol == IPPROTO_UDP)
            block_udp_with_lookup(args, pkt, length, payload, owner, &udp_lookup);
        else if (protocol == IPPROTO_TCP)
            handle_tcp(args, pkt, length, payload, owner, allowed, redirect, epoll_fd);

        log_android(ANDROID_LOG_WARN, "Address v%d p%d %s/%u syn %d uid %d not allowed",
                    version, protocol, format_log_address(&dest), dport, syn, uid);
    }
}

static int64_t uid_cache_now(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_BOOTTIME, &ts) != 0) {
        log_android(ANDROID_LOG_ERROR, "UID clock_gettime error %d: %s", errno, strerror(errno));
        return -1; // Resolve from proc without mixing clock domains or caching a bogus time.
    }
    return (int64_t) ts.tv_sec * INT64_C(1000) + ts.tv_nsec / INT64_C(1000000);
}

jint get_uid(const int version, const int protocol,
             const void *saddr, const uint16_t sport,
             const void *daddr, const uint16_t dport) {
    jint uid = -1;

    struct log_address source = {.version = version, .address = saddr};
    struct log_address dest = {.version = version, .address = daddr};
    // The proc helper only uses these strings for INFO cache-hit diagnostics.
    if (ANDROID_LOG_INFO >= LOG_LEVEL) {
        format_log_address(&source);
        format_log_address(&dest);
    }

    int64_t now = uid_cache_now();

    // Check IPv6 table first
    if (version == 4) {
        uint8_t saddr128[16];
        memset(saddr128, 0, 10);
        saddr128[10] = 0xFF;
        saddr128[11] = 0xFF;
        memcpy(saddr128 + 12, saddr, 4);

        uint8_t daddr128[16];
        memset(daddr128, 0, 10);
        daddr128[10] = 0xFF;
        daddr128[11] = 0xFF;
        memcpy(daddr128 + 12, daddr, 4);

        uid = get_uid_sub(6, protocol, saddr128, sport, daddr128, dport, source.text, dest.text, now);
        log_android(ANDROID_LOG_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d as inet6",
                    version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport, uid);
    }

    // A missing or unreadable IPv6 table must not prevent an IPv4 lookup.
    if (uid < 0) {
        uid = get_uid_sub(version, protocol, saddr, sport, daddr, dport, source.text, dest.text, now);
        log_android(ANDROID_LOG_DEBUG, "uid v%d p%d %s/%u > %s/%u => %d fallback",
                    version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport, uid);
    }

    if (uid == -1)
        log_android(ANDROID_LOG_WARN, "uid v%d p%d %s/%u > %s/%u => not found",
                    version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport);
    else if (uid >= 0)
        log_android(ANDROID_LOG_INFO, "uid v%d p%d %s/%u > %s/%u => %d",
                    version, protocol, format_log_address(&source), sport, format_log_address(&dest), dport, uid);

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
                      const uint8_t *daddr, uint16_t dport, jint uid, int64_t now,
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
        memcpy(address + (size_t) w * sizeof(word), &word, sizeof(word));
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
                 int64_t now) {
    // NETLINK is not available on Android due to SELinux policies :-(
    // http://stackoverflow.com/questions/27148536/netlink-implementation-for-the-android-ndk
    // https://android.googlesource.com/platform/system/sepolicy/+/master/private/app.te (netlink_tcpdiag_socket)

    static uint8_t zero[16] = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    int ws = (version == 4 ? 1 : 4);
    const size_t address_length = (size_t) ws * sizeof(uint32_t);

    // Check cache
    for (int i = 0; now >= 0 && i < uid_cache_size; i++)
        if (now - uid_cache[i].time <= UID_MAX_AGE &&
            uid_cache[i].version == version &&
            uid_cache[i].protocol == protocol &&
            uid_cache[i].sport == sport &&
            (uid_cache[i].dport == dport || uid_cache[i].dport == 0) &&
            (memcmp(uid_cache[i].saddr, saddr, address_length) == 0 ||
             memcmp(uid_cache[i].saddr, zero, address_length) == 0) &&
            (memcmp(uid_cache[i].daddr, daddr, address_length) == 0 ||
             memcmp(uid_cache[i].daddr, zero, address_length) == 0)) {

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
    bool header = true;
    bool malformed = false;
    *line = 0;
    while (fgets(line, sizeof(line), fd) != NULL) {
        // Discard an overlong physical row as a whole. Its continuation must not
        // become another socket record, nor may a truncated UID be accepted.
        bool truncated = false;
        if (strchr(line, '\n') == NULL) {
            int ch = fgetc(fd);
            truncated = ch != EOF;
            while (ch != '\n' && ch != EOF)
                ch = fgetc(fd);
        }
        if (header) {
            header = false;
            continue;
        }

        if (!truncated && parse_proc_uid(line, ws, _saddr, &_sport, _daddr, &_dport, &_uid)) {
            if (_sport == sport &&
                (_dport == dport || _dport == 0) &&
                (memcmp(_saddr, saddr, address_length) == 0 ||
                 memcmp(_saddr, zero, address_length) == 0) &&
                (memcmp(_daddr, daddr, address_length) == 0 ||
                 memcmp(_daddr, zero, address_length) == 0))
                uid = _uid;

            // A large accumulated cache can still have a one-row proc table.
            // Amortize scratch setup over a substantial scan, not just its cache size.
            if (now >= 0) {
                if (updates < UID_UPDATE_THRESHOLD)
                    updates++;
                if (!update_attempted && updates >= UID_UPDATE_THRESHOLD &&
                    uid_cache_size >= UID_UPDATE_THRESHOLD) {
                    update_attempted = true;
                    update = new_uid_update();
                }
                cache_uid(version, protocol, _saddr, (uint16_t) _sport,
                          _daddr, (uint16_t) _dport, _uid, now, update);
            }
        } else {
            log_android(ANDROID_LOG_ERROR, "Invalid proc UID row: %s", line);
            malformed = true;
        }
    }

    ng_free(update, __FILE__, __LINE__);
    if (fclose(fd))
        log_android(ANDROID_LOG_ERROR, "fclose %s error %d: %s", fn, errno, strerror(errno));

    return uid >= 0 || !malformed ? uid : -2;
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

    struct ng_session *cur;
    jint uid = -1;

    if (protocol == IPPROTO_TCP) {
        cur = find_session(args->ctx, version, protocol, saddr, htons(sport), daddr, htons(dport));
        const struct tcphdr *tcp = (const struct tcphdr *) payload;
        // A new SYN must not inherit the owner of a previous use of this tuple.
        if (cur != NULL && cur->tcp.uid >= 0 && !cur->tcp.uid_inferred && cur->socket >= 0 &&
            cur->tcp.state != TCP_CLOSING && cur->tcp.state != TCP_CLOSE &&
            (!tcp->syn || ntohl(tcp->seq) == cur->tcp.remote_start)) {
            uid = cur->tcp.uid;
        }
    } else if (protocol == IPPROTO_UDP) {
        cur = find_session(args->ctx, version, protocol, saddr, htons(sport), daddr, htons(dport));
        if (cur != NULL && cur->udp.uid >= 0 && !cur->udp.uid_inferred && cur->socket >= 0 && cur->udp.state == UDP_ACTIVE) {
            uid = cur->udp.uid;
        }
    } else if (protocol == IPPROTO_ICMP || protocol == IPPROTO_ICMPV6) {

        const struct icmp *icmp = (const struct icmp *) payload;

        if (icmp->icmp_type != ICMP_ECHO && icmp->icmp_type != 128 /* ICMP_ECHOV6 */) {
            log_android(ANDROID_LOG_WARN, "ICMP type %d code %d from %s to %s not supported",
                        icmp->icmp_type, icmp->icmp_code, source, dest);
            return uid;
        }

        cur = find_session(args->ctx, version, protocol, saddr, icmp->icmp_id, daddr, 0);
        if (cur != NULL && cur->icmp.uid >= 0 && cur->socket >= 0) {
            uid = cur->icmp.uid;
        }
    } else {
        return uid;
    }

    if (uid >= 0) {
        log_android(ANDROID_LOG_ERROR,
                    "Packet uid restored v%d %s/%u > %s/%u proto %d flags %s uid %d",
                    version, source, sport, dest, dport, protocol, flags, uid);
    }

    return uid;
}

#pragma clang diagnostic pop
