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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
*/

#include <arm_neon.h>
#include "invizible.h"

#define CSUM_NEON_THRESHOLD 16

uint16_t calc_checksum(uint16_t start, const uint8_t *buffer, size_t length) {
    uint32_t sum = start;

    // Process small inputs with scalar loop
    if (length <= CSUM_NEON_THRESHOLD) {
        const uint8_t *byte_buf = buffer;
        while (length > 1) {
            sum += *(uint16_t *) byte_buf;
            byte_buf += 2;
            length -= 2;
        }
        if (length > 0) {
            sum += *byte_buf;
        }
        while (sum >> 16) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (uint16_t) sum;
    }

    // Align buffer to 16 bytes for NEON
    const uint8_t *byte_buf = buffer;
    while (((uintptr_t) byte_buf & 1) && length > 0) {
        sum += *byte_buf++;
        length--;
    }

    // Process with NEON
    const uint16_t *buf = (const uint16_t *) byte_buf;
    size_t vec_len = length / 8; // Number of 128-bit chunks
    size_t remainder = length % 8;

    uint32x4_t acc = vdupq_n_u32(0); // NEON accumulator
    while (vec_len--) {
        uint16x4_t data = vld1_u16(buf); // Load 4x16-bit values
        acc = vaddw_u16(acc, data); // Accumulate into 32-bit vector
        buf += 4;                        // Advance pointer
    }

    // Reduce NEON accumulator into scalar sum
    uint32_t temp[4];
    vst1q_u32(temp, acc);
    sum += temp[0] + temp[1] + temp[2] + temp[3];

    // Process remaining bytes
    byte_buf = (const uint8_t *) buf;
    while (remainder > 1) {
        sum += *(uint16_t *) byte_buf;
        byte_buf += 2;
        remainder -= 2;
    }
    if (remainder > 0) {
        sum += *byte_buf; // Add last byte if odd length
    }

    // Fold 32-bit sum into 16 bits
    while (sum >> 16) {
        sum = (sum & 0xFFFF) + (sum >> 16);
    }

    return (uint16_t) sum;
}

uint16_t do_csum_generic(uint16_t start, const uint8_t *buffer, size_t length) {
    register uint32_t sum = start;
    register uint16_t *buf = (uint16_t *) buffer;
    register size_t len = length;

    while (len > 1) {
        sum += *buf++;
        len -= 2;
    }

    if (len > 0)
        sum += *((uint8_t *) buf);

    while (sum >> 16)
        sum = (sum & 0xFFFF) + (sum >> 16);

    return (uint16_t) sum;
}

static inline uint16_t from64to16(uint64_t x) {
    x = (x & 0xffffffff) + (x >> 32);
    x = (x & 0xffffffff) + (x >> 32);
    x = ((uint32_t) x & 0xffff) + ((uint32_t) x >> 16);
    x = ((uint32_t) x & 0xffff) + ((uint32_t) x >> 16);
    return x;
}

uint16_t do_csum_neon(uint16_t start, const uint8_t *buff, size_t len) {
    unsigned int odd, count;
    uint64_t result = start;
    unsigned int count64;
    uint32x4_t vzero = (uint32x4_t) {0, 0, 0, 0};

    register uint32x4_t v0, v1, v2, v3;

    if (len <= 0)
        return result;

    odd = 1 & (unsigned long) buff;
    if (odd) {
        result += *(unsigned short *) buff;
        len--;
        buff++;
    }

    count = len >> 1;
    if (count) {
        if (2 & (unsigned long) buff) {
            result += *(unsigned short *) buff;
            count--;
            len -= 2;
            buff += 2;
        }
        count >>= 1;            /* nr of 32-bit words.. */
        if (count) {
            if (4 & (unsigned long) buff) {
                result += *(unsigned int *) buff;
                count--;
                len -= 4;
                buff += 4;
            }
            count >>= 1;    /* nr of 64-bit words.. */

            v0 = vzero;
            v1 = vzero;
            v2 = vzero;
            v3 = vzero;

            count64 = count >> 3;  /* compute 64 Byte circle */
            while (count64) {
                v0 = vpadalq_u16(v0,
                                 +vld1q_u16((uint16_t *) buff + 0));
                v1 = vpadalq_u16(v1,
                                 +vld1q_u16((uint16_t *) buff + 8));
                v2 = vpadalq_u16(v2,
                                 +vld1q_u16((uint16_t *) buff + 16));
                v3 = vpadalq_u16(v3,
                                 +vld1q_u16((uint16_t *) buff + 24));
                buff += 64;
                count64--;
            }
            v0 = vaddq_u32(v0, v1);
            v2 = vaddq_u32(v2, v3);
            v0 = vaddq_u32(v0, v2);

            count %= 8;
            while (count >= 2) { /* compute 16 byte circle */
                v0 = vpadalq_u16(v0,
                                 +vld1q_u16((uint16_t *) buff + 0));
                buff += 16;
                count -= 2;
            }

            result += vgetq_lane_u32(v0, 0);
            result += vgetq_lane_u32(v0, 1);
            result += vgetq_lane_u32(v0, 2);
            result += vgetq_lane_u32(v0, 3);
            if (count & 1) {
                result += *(unsigned long long *) buff;
                buff += 8;
            }
            if (len & 4) {
                result += *(unsigned int *) buff;
                buff += 4;
            }
        }
        if (len & 2) {
            result += *(unsigned short *) buff;
            buff += 2;
        }
    }
    if (len & 1)
        result += *buff;
    result = from64to16(result);
    if (odd)
        result = ((result >> 8) & 0xff) | ((result & 0xff) << 8);
    return result;
}

int compare_u32(uint32_t s1, uint32_t s2) {
    // https://tools.ietf.org/html/rfc1982
    if (s1 == s2)
        return 0;

    uint32_t i1 = s1;
    uint32_t i2 = s2;
    if ((i1 < i2 && i2 - i1 < 0x7FFFFFFF) ||
        (i1 > i2 && i1 - i2 > 0x7FFFFFFF))
        return -1;
    else
        return 1;
}

int sdk_int(JNIEnv *env) {
    jclass clsVersion = jniFindClass(env, "android/os/Build$VERSION");
    jfieldID fid = (*env)->GetStaticFieldID(env, clsVersion, "SDK_INT", "I");
    return (*env)->GetStaticIntField(env, clsVersion, fid);
}

void log_android(int prio, const char *fmt, ...) {
    if (prio >= LOG_LEVEL) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsprintf(line, fmt, argptr);
        __android_log_print(prio, TAG, "%s", line);
        va_end(argptr);
    }
}

uint8_t char2nible(const char c) {
    if (c >= '0' && c <= '9') return (uint8_t) (c - '0');
    if (c >= 'a' && c <= 'f') return (uint8_t) ((c - 'a') + 10);
    if (c >= 'A' && c <= 'F') return (uint8_t) ((c - 'A') + 10);
    return 255;
}

void hex2bytes(const char *hex, uint8_t *buffer) {
    size_t len = strlen(hex);
    for (int i = 0; i < len; i += 2)
        buffer[i / 2] = (char2nible(hex[i]) << 4) | char2nible(hex[i + 1]);
}

char *trim(char *str) {
    while (isspace(*str))
        str++;
    if (*str == 0)
        return str;

    char *end = str + strlen(str) - 1;
    while (end > str && isspace(*end))
        end--;
    *(end + 1) = 0;
    return str;
}

const char *strstate(const int state) {
    switch (state) {
        case TCP_ESTABLISHED:
            return "ESTABLISHED";
        case TCP_SYN_SENT:
            return "SYN_SENT";
        case TCP_SYN_RECV:
            return "SYN_RECV";
        case TCP_FIN_WAIT1:
            return "FIN_WAIT1";
        case TCP_FIN_WAIT2:
            return "FIN_WAIT2";
        case TCP_TIME_WAIT:
            return "TIME_WAIT";
        case TCP_CLOSE:
            return "CLOSE";
        case TCP_CLOSE_WAIT:
            return "CLOSE_WAIT";
        case TCP_LAST_ACK:
            return "LAST_ACK";
        case TCP_LISTEN:
            return "LISTEN";
        case TCP_CLOSING:
            return "CLOSING";
        default:
            return "UNKNOWN";
    }
}

char *hex(const u_int8_t *data, const size_t len) {
    char hex_str[] = "0123456789ABCDEF";

    char *hexout;
    hexout = (char *) ng_malloc(len * 3 + 1, "hex");

    for (size_t i = 0; i < len; i++) {
        hexout[i * 3 + 0] = hex_str[(data[i] >> 4) & 0x0F];
        hexout[i * 3 + 1] = hex_str[(data[i]) & 0x0F];
        hexout[i * 3 + 2] = ' ';
    }
    hexout[len * 3] = 0;

    return hexout;
}

int32_t get_local_port(const int sock) {
    struct sockaddr_in sin;
    socklen_t len = sizeof(sin);
    if (getsockname(sock, (struct sockaddr *) &sin, &len) < 0) {
        log_android(ANDROID_LOG_ERROR, "getsockname error %d: %s", errno, strerror(errno));
        return -1;
    } else
        return ntohs(sin.sin_port);
}

int is_event(int fd, short event) {
    struct pollfd p;
    p.fd = fd;
    p.events = event;
    p.revents = 0;
    int r = poll(&p, 1, 0);
    if (r < 0) {
        log_android(ANDROID_LOG_ERROR, "poll readable error %d: %s", errno, strerror(errno));
        return 0;
    } else if (r == 0)
        return 0;
    else
        return (p.revents & event);
}

int is_readable(int fd) {
    return is_event(fd, POLLIN);
}

int is_writable(int fd) {
    return is_event(fd, POLLOUT);
}

long long get_ms() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000LL + ts.tv_nsec / 1e6;
}

int str_equal(const char *s, const char *f) {
    if (!s || !f)
        return 0;
    size_t slen = strlen(s);
    size_t flen = strlen(f);
    return slen == flen && !memcmp(s, f, flen);
}

int str_ends_with(const char *s, const char *suff) {
    if (!s || !suff)
        return 0;
    size_t slen = strlen(s);
    size_t sufflen = strlen(suff);
    return slen >= sufflen && !memcmp(s + slen - sufflen, suff, sufflen);
}

#pragma clang diagnostic pop
