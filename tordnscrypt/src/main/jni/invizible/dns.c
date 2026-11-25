#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"
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

static int32_t read_qname(const uint8_t *data, const size_t datalen, uint16_t off,
                          char *qname, bool hostname) {
    *qname = 0;
    size_t ptr = off;
    size_t noff = 0;
    size_t wire_length = 1; // Include the terminating root label.
    int32_t next = -1;
    unsigned int jumps = 0;

    while (ptr < datalen) {
        uint8_t len = data[ptr++];
        if (len == 0)
            return (next < 0 ? (int32_t) ptr : next);

        if ((len & 0xC0) == 0xC0) {
            if (ptr >= datalen || jumps++ >= 128)
                break;
            if (next < 0)
                next = (int32_t) (ptr + 1);
            ptr = (size_t) (len & 0x3F) * 256 + data[ptr];
        } else {
            if ((len & 0xC0) != 0 || len > datalen - ptr ||
                wire_length + len + 1 > DNS_QNAME_MAX)
                break;
            wire_length += len + 1;
            if (noff > 0)
                qname[noff++] = '.';
            if (hostname) {
                for (size_t i = 0; i < len; i++) {
                    uint8_t c = data[ptr + i];
                    // Keep an unambiguous, case-insensitive hostname key. Binary
                    // labels (including embedded dots/NULs) are not inference evidence.
                    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                          (c >= '0' && c <= '9') || c == '-' || c == '_'))
                        return -1;
                    qname[noff + i] = (char) (c >= 'A' && c <= 'Z' ? c + ('a' - 'A') : c);
                }
            } else
                memcpy(qname + noff, data + ptr, len);
            noff += len;
            qname[noff] = 0;
            ptr += len;
        }
    }

    // Never leave a partial name for callers to treat as a valid string.
    *qname = 0;
    log_android(ANDROID_LOG_WARN, "DNS invalid qname");
    return -1;
}

int32_t get_qname(const uint8_t *data, const size_t datalen, uint16_t off, char *qname) {
    return read_qname(data, datalen, off, qname, false);
}

static void cache_uid_dns_response(struct context *ctx, const uint8_t *data, size_t length) {
    if (ctx == NULL || length < sizeof(struct dns_header) || length > UINT16_MAX ||
        (data[2] & 0xFA) != 0x80 || (data[3] & 0x0F) != 0 || read_net16(data + 4) != 1)
        return; // Only complete, successful standard responses can supply evidence.
    char domain[DNS_QNAME_MAX + 1], current[DNS_QNAME_MAX + 1];
    char name[DNS_QNAME_MAX + 1], target[DNS_QNAME_MAX + 1];
    int32_t off = read_qname(data, length, 12, domain, true);
    if (off < 0 || (size_t) off + 4 > length || *domain == 0 ||
        read_net16(data + off + 2) != DNS_QCLASS_IN ||
        (read_net16(data + off) != DNS_QTYPE_A && read_net16(data + off) != DNS_QTYPE_AAAA))
        return;
    off += 4;
    const int32_t answer_offset = off;
    strcpy(current, domain);
    struct uid_dns_answer answers[UID_DNS_ANSWERS];
    size_t chain_count = 0;
    uint16_t chain[8];
    uint32_t chain_ttl = UINT32_MAX;
    unsigned int answer_count = read_net16(data + 6);
    unsigned int records = answer_count + read_net16(data + 8) + read_net16(data + 10);
    // Use only records already in this response. Keep the owner fixed throughout
    // each scan so address collection never depends on where its CNAME appears.
    for (;;) {
        size_t count = 0;
        bool overflow = false;
        int32_t alias_offset = -1;
        uint16_t alias_owner = 0;
        uint32_t alias_ttl = UINT32_MAX;
        off = answer_offset;
        for (unsigned int r = 0; r < records; r++) {
            uint16_t owner_offset = (uint16_t) off;
            off = read_qname(data, length, owner_offset, name, true);
            if (off < 0 || (size_t) off + 10 > length)
                return;
            uint16_t type = read_net16(data + off), klass = read_net16(data + off + 2);
            uint32_t ttl; memcpy(&ttl, data + off + 4, sizeof(ttl)); ttl = ntohl(ttl);
            size_t size = read_net16(data + off + 8);
            off += 10;
            if (size > length - (size_t) off)
                return;
            if (type == 41 && (ttl >> 24) != 0)
                return; // Extended EDNS error code.
            if (klass == DNS_QCLASS_IN && type == DNS_QTYPE_CNAME) {
                int32_t end = read_qname(data, length, (uint16_t) off, target, true);
                if (end < 0 || (size_t) end != (size_t) off + size || *target == 0)
                    return;
                if (r < answer_count && strcmp(name, current) == 0) {
                    if (alias_offset >= 0 &&
                        (read_qname(data, length, (uint16_t) alias_offset, name, true) < 0 || strcmp(name, target) != 0))
                        return;
                    alias_offset = off;
                    alias_owner = owner_offset;
                    if (ttl < alias_ttl) alias_ttl = ttl;
                }
            } else if (klass == DNS_QCLASS_IN && (type == DNS_QTYPE_A || type == DNS_QTYPE_AAAA)) {
                if (size != (type == DNS_QTYPE_A ? 4u : 16u))
                    return;
                if (!overflow && r < answer_count && strcmp(name, current) == 0) {
                    uint8_t version = type == DNS_QTYPE_A ? 4 : 6;
                    uint32_t address_ttl = ttl < chain_ttl ? ttl : chain_ttl;
                    size_t index = 0;
                    while (index < count &&
                           (answers[index].version != version || memcmp(answers[index].address, data + off, size) != 0))
                        index++;
                    if (index < count) {
                        // Duplicates do not consume slots; zero TTL must also win.
                        if (address_ttl < answers[index].ttl) answers[index].ttl = address_ttl;
                    } else if (count < UID_DNS_ANSWERS) {
                        answers[count].version = version;
                        memcpy(answers[count].address, data + off, size);
                        answers[count].ttl = address_ttl;
                        count++;
                    } else
                        overflow = true;
                }
            }
            off += (int32_t) size;
        }
        if ((size_t) off != length)
            return;
        if (alias_offset < 0) {
            if (count != 0 && !overflow)
                record_uid_dns_unique_answers(ctx, domain, answers, count);
            else if (overflow) {
                // All record bounds have been validated. Retain loss evidence
                // for every address, including those beyond the bounded batch.
                // Reuse the existing buffer so each batch shares one clock read.
                count = 0;
                off = answer_offset;
                for (unsigned int r = 0; r < answer_count; r++) {
                    off = read_qname(data, length, (uint16_t) off, name, true);
                    if (off < 0) return;
                    uint16_t type = read_net16(data + off), klass = read_net16(data + off + 2);
                    uint32_t ttl; memcpy(&ttl, data + off + 4, sizeof(ttl)); ttl = ntohl(ttl);
                    size_t size = read_net16(data + off + 8);
                    off += 10;
                    if (klass == DNS_QCLASS_IN && (type == DNS_QTYPE_A || type == DNS_QTYPE_AAAA) &&
                        strcmp(name, current) == 0) {
                        answers[count].version = type == DNS_QTYPE_A ? 4 : 6;
                        answers[count].ttl = ttl < chain_ttl ? ttl : chain_ttl;
                        memcpy(answers[count].address, data + off, size);
                        if (++count == UID_DNS_ANSWERS) {
                            discard_uid_dns_answers(ctx, domain, answers, count);
                            count = 0;
                        }
                    }
                    off += (int32_t) size;
                }
                if (count != 0) discard_uid_dns_answers(ctx, domain, answers, count);
            }
            return;
        }
        if (count != 0 || chain_count == sizeof(chain) / sizeof(*chain) ||
            read_qname(data, length, (uint16_t) alias_offset, target, true) < 0 || strcmp(current, target) == 0)
            return;
        for (size_t i = 0; i < chain_count; i++)
            if (read_qname(data, length, chain[i], name, true) < 0 || strcmp(name, target) == 0)
                return;
        chain[chain_count++] = alias_owner;
        strcpy(current, target);
        if (alias_ttl < chain_ttl) chain_ttl = alias_ttl;
    }
}

bool dns_has_multiple_questions(const uint8_t *data, size_t datalen) {
    // RFC 9619 applies to standard QUERY messages, including responses. Zero is valid.
    return datalen >= sizeof(struct dns_header) && (data[2] & 0x78) == 0 &&
           read_net16(data + 4) > 1;
}

bool check_dns_tcp_response(struct tcp_session *cur, const uint8_t *data, size_t datalen) {
    while (datalen > 0) {
        if (cur->dns_body_remaining) {
            size_t skip = (datalen < cur->dns_body_remaining ? datalen : cur->dns_body_remaining);
            cur->dns_body_remaining -= skip;
            data += skip;
            datalen -= skip;
        } else {
            size_t target = 2;
            if (cur->dns_header_used >= 2) {
                const uint16_t length = read_net16(cur->dns_header);
                target += (length < sizeof(struct dns_header) ? length : sizeof(struct dns_header));
            }
            size_t take = target - cur->dns_header_used;
            if (take > datalen)
                take = datalen;
            memcpy(cur->dns_header + cur->dns_header_used, data, take);
            cur->dns_header_used += take;
            data += take;
            datalen -= take;
            if (cur->dns_header_used >= 2) {
                const uint16_t length = read_net16(cur->dns_header);
                size_t header = (length < sizeof(struct dns_header) ? length : sizeof(struct dns_header));
                if (cur->dns_header_used == header + 2) {
                    if (dns_has_multiple_questions(cur->dns_header + 2, header))
                        return false;
                    // The wire length is 16-bit and header never exceeds it.
                    cur->dns_body_remaining = (uint16_t) (length - header);
                    cur->dns_header_used = 0;
                }
            }
        }
    }
    return true;
}

void parse_dns_response(const struct arguments *args, const struct ng_session *s,
                        uint8_t *data, size_t *datalen) {
    (void) s; // Used by the commented packet logging below.
    if (dns_has_multiple_questions(data, *datalen)) {
        log_android(ANDROID_LOG_WARN, "DNS response has multiple questions");
        return;
    }
    if (*datalen < sizeof(struct dns_header)) {
        log_android(ANDROID_LOG_WARN, "DNS response length %zu", *datalen);
        return;
    }

    // Check if standard DNS query
    struct dns_header *dns = (struct dns_header *) data;
    int qcount = ntohs(dns->q_count);
    int acount = ntohs(dns->ans_count);
    uint16_t rcode = dns->rcode;
    if (dns->qr == 1 && dns->opcode == 0 && qcount > 0) {
        log_android(ANDROID_LOG_DEBUG, "DNS response qcount %d acount %d", qcount, acount);

        // http://tools.ietf.org/html/rfc1035
        char name[DNS_QNAME_MAX + 1];
        int32_t off = sizeof(struct dns_header);

        uint16_t question_type;
        uint16_t question_class;
        char qname[DNS_QNAME_MAX + 1];
        char cname[DNS_QNAME_MAX + 1];
        const char *SPACE_SYMBOL = " ";

        off = get_qname(data, *datalen, (uint16_t) off, name);
        if (off <= 0 || (size_t) off + 4 > *datalen) {
            log_android(ANDROID_LOG_WARN,
                        "DNS response Q invalid off %d datalen %zu", off, *datalen);
            return;
        }
        strcpy(qname, name);
        question_type = read_net16(data + off);
        question_class = read_net16(data + off + 2);
        log_android(ANDROID_LOG_DEBUG,
                    "DNS question %d qtype %d qclass %d qname %s",
                    0, question_type, question_class, qname);
        off += 4;

        if (acount == 0 || rcode != 0) {
            dns_resolved(args, qname, name, SPACE_SYMBOL, SPACE_SYMBOL,
                         SPACE_SYMBOL, rcode);
            return;
        }

        short svcb = 0;
        const size_t aoff = (size_t) off; // The complete question was validated above.
        for (int a = 0; a < acount; a++) {
            off = get_qname(data, *datalen, (uint16_t) off, name);
            if (off > 0 && (size_t) off + 10 <= *datalen) {
                uint16_t qtype = read_net16(data + off);
                uint16_t qclass = read_net16(data + off + 2);
                //uint32_t ttl = ntohl(*((uint32_t *) (data + off + 4)));
                uint16_t rdlength = read_net16(data + off + 8);
                off += 10;

                if ((size_t) off + rdlength <= *datalen) {
                    if (qclass == DNS_QCLASS_IN &&
                        (qtype == DNS_QTYPE_A || qtype == DNS_QTYPE_AAAA)) {

                        char rd[INET6_ADDRSTRLEN + 1];
                        if (qtype == DNS_QTYPE_A) {
                            if (rdlength == sizeof(__be32))
                                inet_ntop(AF_INET, data + off, rd, sizeof(rd));
                            else
                                return;
                        } else if (qclass == DNS_QCLASS_IN && qtype == DNS_QTYPE_AAAA) {
                            if (rdlength == sizeof(struct in6_addr))
                                inet_ntop(AF_INET6, data + off, rd, sizeof(rd));
                            else
                                return;
                        }

                        dns_resolved(args, qname, name, SPACE_SYMBOL, SPACE_SYMBOL,
                                     rd, rcode);
                        log_android(ANDROID_LOG_DEBUG,
                                    "DNS answer %d qname %s qtype %d rcode %d data %s",
                                    a, name, qtype, rcode, rd);

                    } else if (qclass == DNS_QCLASS_IN && qtype == DNS_QTYPE_CNAME) {
                        int32_t end = get_qname(data, *datalen, (uint16_t) off, cname);
                        if (end < 0 || end != off + rdlength)
                            return;
                        dns_resolved(args, qname, name, cname, SPACE_SYMBOL,
                                     SPACE_SYMBOL, rcode);
                        log_android(ANDROID_LOG_DEBUG,
                                    "DNS answer %d qname %s cname %s qclass %d qtype %d rcode %d length %d",
                                    a, name, cname, qclass, qtype, rcode, rdlength);
                    } else if (qclass == DNS_QCLASS_IN && qtype == DNS_QTYPE_HINFO) {
                        char *hinfo = ng_malloc(rdlength, "hinfo");
                        if (hinfo == NULL)
                            return;
                        memset(hinfo, '\0', rdlength);
                        if (rdlength > 1) {
                            memcpy(hinfo, data + off + 1, (size_t) (rdlength - 1));
                            dns_resolved(args, qname, name, SPACE_SYMBOL, hinfo,
                                         SPACE_SYMBOL, rcode);
                            log_android(ANDROID_LOG_DEBUG,
                                        "DNS answer %d qname %s hinfo %s",
                                        a, name, hinfo);
                        }
                        ng_free(hinfo, __FILE__, __LINE__);

                    } else if (qclass == DNS_QCLASS_IN &&
                               (qtype == DNS_QTYPE_SVCB || qtype == DNS_QTYPE_HTTPS)) {
                        // https://tools.ietf.org/id/draft-ietf-dnsop-svcb-https-01.html
                        svcb = 1;
                        log_android(ANDROID_LOG_WARN,
                                    "SVCB answer %d qname %s qtype %d", a, name, qtype);
                    } else
                        log_android(ANDROID_LOG_DEBUG,
                                    "DNS answer %d qname %s qclass %d qtype %d rcode %d length %d",
                                    a, name, qclass, qtype, rcode, rdlength);

                    off += rdlength;
                } else {
                    log_android(ANDROID_LOG_WARN,
                                "DNS response A invalid off %d rdlength %d datalen %zu",
                                off, rdlength, *datalen);
                    return;
                }
            } else {
                log_android(ANDROID_LOG_WARN,
                            "DNS response A invalid off %d datalen %zu", off, *datalen);
                return;
            }
        }

        if (qcount > 0 && (svcb || is_domain_blocked(args, qname))) {
            dns->qr = 1;
            dns->aa = 0;
            dns->tc = 0;
            dns->rd = 0;
            dns->ra = 0;
            dns->z = 0;
            dns->ad = 0;
            dns->cd = 0;
            dns->rcode = (uint16_t) args->rcode;
            dns->ans_count = 0;
            dns->auth_count = 0;
            dns->add_count = 0;
            *datalen = aoff;

            // Disabled qname logging, including address preparation.
            /*int version;
            char source[INET6_ADDRSTRLEN + 1];
            char dest[INET6_ADDRSTRLEN + 1];
            uint16_t sport;
            uint16_t dport;

            if (s->protocol == IPPROTO_UDP) {
                version = s->udp.version;
                sport = ntohs(s->udp.source);
                dport = ntohs(s->udp.dest);
                if (s->udp.version == 4) {
                    inet_ntop(AF_INET, &s->udp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->udp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->udp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->udp.daddr.ip6, dest, sizeof(dest));
                }
            } else {
                version = s->tcp.version;
                sport = ntohs(s->tcp.source);
                dport = ntohs(s->tcp.dest);
                if (s->tcp.version == 4) {
                    inet_ntop(AF_INET, &s->tcp.saddr.ip4, source, sizeof(source));
                    inet_ntop(AF_INET, &s->tcp.daddr.ip4, dest, sizeof(dest));
                } else {
                    inet_ntop(AF_INET6, &s->tcp.saddr.ip6, source, sizeof(source));
                    inet_ntop(AF_INET6, &s->tcp.daddr.ip6, dest, sizeof(dest));
                }
            }

            char name[DNS_QNAME_MAX + 40 + 1];
            sprintf(name, "qtype %d qname %s rcode %d", question_type, qname, dns->rcode);
            jobject objPacket = create_packet(
                    args, version, s->protocol, "",
                    source, sport, dest, dport,
                    name, 0, 0);

            log_packet(args, objPacket);*/
        } else
            cache_uid_dns_response(args->ctx, data, *datalen);
    } else if (acount > 0)
        log_android(ANDROID_LOG_WARN,
                    "DNS response qr %d opcode %d qcount %d acount %d",
                    dns->qr, dns->opcode, qcount, acount);
}

#pragma clang diagnostic pop
