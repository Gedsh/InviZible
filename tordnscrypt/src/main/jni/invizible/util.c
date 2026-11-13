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

#if defined(__x86_64__)
//https://github.com/intel/ARM_NEON_2_x86_SSE
//https://github.com/android/ndk-samples/tree/main/hello-neon
#include "neon2sse.h"
#else
#include <arm_neon.h>
#endif
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

    // Consume an odd leading byte before reading 16-bit words.
    const uint8_t *byte_buf = buffer;
    while (((uintptr_t) byte_buf & 1) && length > 0) {
        sum += *byte_buf++;
        length--;
    }

    // Process with NEON
    const uint16_t *buf = (const uint16_t *) byte_buf;
    size_t vec_len = length / 8; // Number of 64-bit chunks (four 16-bit words).
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

uint8_t *get_packet_buffer(const struct arguments *args, enum packet_buffer_kind kind,
                           size_t length, const char *tag) {
    if (args->buffers == NULL)
        return ng_malloc(length, tag);

    struct packet_buffer *buffer = &args->buffers->buffer[kind];
    if (buffer->data == NULL || buffer->capacity < length) {
        // Small ACKs should not make the output buffer grow for every larger packet.
        size_t capacity = length < get_mtu() ? get_mtu() : length;
        uint8_t *data = (buffer->data == NULL ? ng_malloc(capacity, tag)
                                             : ng_realloc(buffer->data, capacity, tag));
        if (data == NULL)
            return NULL; // A failed resize leaves the old allocation owned by this scope.
        buffer->data = data;
        buffer->capacity = capacity;
    }
    return buffer->data;
}

void release_packet_buffer(const struct arguments *args, uint8_t *buffer) {
    if (args->buffers == NULL)
        ng_free(buffer, __FILE__, __LINE__);
}

void clear_packet_buffers(struct packet_buffers *buffers) {
    for (size_t i = 0; i < PACKET_BUFFER_COUNT; i++) {
        ng_free(buffers->buffer[i].data, __FILE__, __LINE__);
        buffers->buffer[i].data = NULL;
        buffers->buffer[i].capacity = 0;
    }
}

const char *format_log_address(struct log_address *address) {
    int saved_errno = errno;
    if (address->text[0] == 0 &&
        inet_ntop(address->version == 4 ? AF_INET : AF_INET6, address->address,
                  address->text, sizeof(address->text)) == NULL)
        strcpy(address->text, "?");
    errno = saved_errno;
    return address->text;
}

void log_android_write(int prio, const char *fmt, ...) {
    if (prio >= LOG_LEVEL) {
        char line[1024];
        va_list argptr;
        va_start(argptr, fmt);
        vsnprintf(line, sizeof(line), fmt, argptr);
        __android_log_print(prio, TAG, "%s", line);
        va_end(argptr);
    }
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
    if (len > (SIZE_MAX - 1) / 3)
        return NULL;
    hexout = (char *) ng_malloc(len * 3 + 1, "hex");
    if (hexout == NULL)
        return NULL;

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
