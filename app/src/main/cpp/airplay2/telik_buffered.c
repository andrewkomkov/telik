#include "telik_buffered.h"

#include <errno.h>
#include <netinet/in.h>
#include <poll.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "telik_hk.h"

#define QUEUE_MAX 2048
#define MAX_PACKET 65535
#define HEADER 12

typedef struct {
    uint8_t *data;
    int len;
    uint32_t rtp_ts;
    uint32_t seq;
} queued_t;

struct telik_buffered {
    telik_stream_t *stream;
    telik_buffered_sink sink;
    void *cls;

    pthread_mutex_t lock;
    int anchored;
    int playing;
    uint32_t anchor_rtp;
    uint64_t anchor_ns;
    queued_t queue[QUEUE_MAX];
    unsigned queued;

    uint8_t *rx;
    size_t rx_len;
    unsigned delivered;
    unsigned bad;

    int listen_fd;
    pthread_t thread;
    int started;
    volatile int stop;
};

telik_buffered_t *telik_buffered_new(const uint8_t *key, size_t key_len, telik_buffered_sink sink, void *cls) {
    telik_buffered_t *b;
    if (!sink) return NULL;
    b = calloc(1, sizeof(*b));
    if (!b) return NULL;
    b->stream = telik_stream_new_key(key, key_len);
    if (!b->stream) {
        free(b);
        return NULL;
    }
    b->sink = sink;
    b->cls = cls;
    b->listen_fd = -1;
    pthread_mutex_init(&b->lock, NULL);
    return b;
}

static void drop_queue(telik_buffered_t *b) {
    for (unsigned i = 0; i < b->queued; i++) free(b->queue[i].data);
    b->queued = 0;
}

void telik_buffered_free(telik_buffered_t *b) {
    if (!b) return;
    b->stop = 1;
    if (b->started) pthread_join(b->thread, NULL);
    if (b->listen_fd >= 0) close(b->listen_fd);
    drop_queue(b);
    telik_stream_free(b->stream);
    pthread_mutex_destroy(&b->lock);
    free(b->rx);
    free(b);
}

static uint64_t remote_time(const telik_buffered_t *b, uint32_t rtp_ts) {
    int64_t delta = (int32_t) (rtp_ts - b->anchor_rtp);
    return (uint64_t) ((int64_t) b->anchor_ns + delta * 1000000000LL / TELIK_BUFFERED_RATE);
}

/* Call with the lock held. */
static void deliver(telik_buffered_t *b, const uint8_t *data, int len, uint32_t rtp_ts, uint32_t seq) {
    b->sink(b->cls, data, len, rtp_ts, seq, remote_time(b, rtp_ts));
    b->delivered++;
}

static void deliver_queue(telik_buffered_t *b) {
    for (unsigned i = 0; i < b->queued; i++) {
        deliver(b, b->queue[i].data, b->queue[i].len, b->queue[i].rtp_ts, b->queue[i].seq);
        free(b->queue[i].data);
    }
    b->queued = 0;
}

void telik_buffered_set_anchor(telik_buffered_t *b, uint32_t rtp_ts, uint64_t remote_ns, int playing) {
    pthread_mutex_lock(&b->lock);
    b->anchored = 1;
    b->playing = playing;
    b->anchor_rtp = rtp_ts;
    b->anchor_ns = remote_ns;
    if (playing) deliver_queue(b);
    pthread_mutex_unlock(&b->lock);
}

void telik_buffered_flush(telik_buffered_t *b) {
    pthread_mutex_lock(&b->lock);
    drop_queue(b);
    b->anchored = 0;
    b->playing = 0;
    pthread_mutex_unlock(&b->lock);
}

static void handle_packet(telik_buffered_t *b, const uint8_t *packet, int len) {
    uint8_t *plain = malloc((size_t) len);
    int plain_len;
    uint32_t seq, rtp_ts;
    if (!plain) return;
    plain_len = telik_audio_decrypt(b->stream, packet, len, plain);
    if (plain_len < 0) {
        b->bad++;
        free(plain);
        return;
    }
    seq = ((uint32_t) packet[1] << 16) | ((uint32_t) packet[2] << 8) | packet[3];
    rtp_ts = ((uint32_t) packet[4] << 24) | ((uint32_t) packet[5] << 16) | ((uint32_t) packet[6] << 8) | packet[7];
    pthread_mutex_lock(&b->lock);
    if (b->anchored && b->playing) {
        deliver(b, plain, plain_len, rtp_ts, seq);
        free(plain);
    } else if (b->queued < QUEUE_MAX) {
        b->queue[b->queued++] = (queued_t) { plain, plain_len, rtp_ts, seq };
    } else {
        free(plain);
    }
    pthread_mutex_unlock(&b->lock);
}

int telik_buffered_feed(telik_buffered_t *b, const uint8_t *bytes, size_t len) {
    int packets = 0;
    uint8_t *grown = realloc(b->rx, b->rx_len + len);
    if (!grown) return -1;
    b->rx = grown;
    memcpy(b->rx + b->rx_len, bytes, len);
    b->rx_len += len;
    while (b->rx_len >= 2) {
        size_t frame = ((size_t) b->rx[0] << 8) | b->rx[1];
        if (frame < 2 + HEADER) return -1;
        if (b->rx_len < frame) break;
        handle_packet(b, b->rx + 2, (int) (frame - 2));
        packets++;
        b->rx_len -= frame;
        memmove(b->rx, b->rx + frame, b->rx_len);
    }
    return packets;
}

void telik_buffered_stats(const telik_buffered_t *b, unsigned *delivered, unsigned *bad, unsigned *queued) {
    if (delivered) *delivered = b->delivered;
    if (bad) *bad = b->bad;
    if (queued) *queued = b->queued;
}

static void *reader(void *arg) {
    telik_buffered_t *b = arg;
    struct pollfd pfd = { .fd = b->listen_fd, .events = POLLIN };
    int fd = -1;
    uint8_t buffer[MAX_PACKET];
    while (!b->stop && fd < 0) {
        if (poll(&pfd, 1, 200) > 0) fd = accept(b->listen_fd, NULL, NULL);
    }
    close(b->listen_fd);
    b->listen_fd = -1;
    pfd.fd = fd;
    while (!b->stop && fd >= 0) {
        ssize_t n;
        if (poll(&pfd, 1, 200) <= 0) continue;
        n = recv(fd, buffer, sizeof(buffer), 0);
        if (n <= 0 || telik_buffered_feed(b, buffer, (size_t) n) < 0) break;
    }
    if (fd >= 0) close(fd);
    return NULL;
}

int telik_buffered_listen(telik_buffered_t *b, unsigned short *port) {
    struct sockaddr_in6 addr6 = { 0 };
    struct sockaddr_in addr4 = { 0 };
    socklen_t addr_len = sizeof(addr6);
    int off = 0;
    unsigned short bound;
    int fd = socket(AF_INET6, SOCK_STREAM, 0);
    addr6.sin6_family = AF_INET6;
    addr6.sin6_addr = in6addr_any;
    if (fd >= 0) setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &off, sizeof(off));
    if (fd >= 0 && bind(fd, (struct sockaddr *) &addr6, sizeof(addr6)) == 0 && listen(fd, 1) == 0 &&
        getsockname(fd, (struct sockaddr *) &addr6, &addr_len) == 0) {
        bound = ntohs(addr6.sin6_port);
    } else {
        /* No IPv6 on this device. */
        if (fd >= 0) close(fd);
        fd = socket(AF_INET, SOCK_STREAM, 0);
        addr4.sin_family = AF_INET;
        addr4.sin_addr.s_addr = htonl(INADDR_ANY);
        addr_len = sizeof(addr4);
        if (fd < 0 || bind(fd, (struct sockaddr *) &addr4, sizeof(addr4)) < 0 || listen(fd, 1) < 0 ||
            getsockname(fd, (struct sockaddr *) &addr4, &addr_len) < 0) {
            if (fd >= 0) close(fd);
            return -1;
        }
        bound = ntohs(addr4.sin_port);
    }
    b->listen_fd = fd;
    if (pthread_create(&b->thread, NULL, reader, b) != 0) {
        close(fd);
        b->listen_fd = -1;
        return -1;
    }
    b->started = 1;
    *port = bound;
    return 0;
}
