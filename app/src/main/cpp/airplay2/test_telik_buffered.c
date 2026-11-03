/* Host test: encrypted buffered audio packets split across reads come out in order with the right play time.
 * Build: gcc -I. -Ithird_party/pair_ap airplay2/test_telik_buffered.c airplay2/telik_buffered.c airplay2/telik_hk.c
 *        -DCONFIG_OPENSSL (link the same objects as test_telik_hk.c) -lcrypto -lpthread */
#include <assert.h>
#include <stdio.h>
#include <string.h>

#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>

#include <openssl/evp.h>

#include "telik_buffered.h"

static uint8_t key[32];
static int count;
static uint32_t last_ts;
static uint64_t last_ns;
static uint8_t last_plain[16];

static void sink(void *cls, const uint8_t *data, int len, uint32_t rtp_ts, uint32_t seq, uint64_t remote_ns) {
    assert(len == 16);
    memcpy(last_plain, data, len);
    last_ts = rtp_ts;
    last_ns = remote_ns;
    count++;
}

/* Frame: length(2) | header(12) | cipher | tag(16) | nonce tail(8). */
static size_t make_frame(uint8_t *out, uint32_t seq, uint32_t ts, const uint8_t *plain) {
    uint8_t *p = out + 2;
    uint8_t nonce[12] = { 0 };
    int len = 0;
    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    memset(p, 0, 12);
    p[0] = 0x80;
    p[1] = seq >> 16; p[2] = seq >> 8; p[3] = seq;
    p[4] = ts >> 24; p[5] = ts >> 16; p[6] = ts >> 8; p[7] = ts;
    nonce[4] = (uint8_t) seq;
    memcpy(p + 12 + 16 + 16, nonce + 4, 8);
    assert(EVP_EncryptInit_ex(ctx, EVP_chacha20_poly1305(), NULL, key, nonce) == 1);
    assert(EVP_EncryptUpdate(ctx, NULL, &len, p + 4, 8) == 1);
    assert(EVP_EncryptUpdate(ctx, p + 12, &len, plain, 16) == 1);
    assert(EVP_EncryptFinal_ex(ctx, p + 12 + 16, &len) == 1);
    assert(EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_GET_TAG, 16, p + 12 + 16) == 1);
    EVP_CIPHER_CTX_free(ctx);
    size_t total = 12 + 16 + 16 + 8;
    out[0] = (uint8_t) ((total + 2) >> 8);
    out[1] = (uint8_t) (total + 2);
    return total + 2;
}

int main(void) {
    uint8_t frames[3][128], plain[16];
    size_t sizes[3];
    uint8_t all[384];
    size_t all_len = 0;
    telik_buffered_t *b;
    unsigned delivered, bad, queued;
    for (int i = 0; i < 32; i++) key[i] = (uint8_t) i;
    b = telik_buffered_new(key, 32, sink, NULL);
    assert(b);
    for (int i = 0; i < 3; i++) {
        memset(plain, 'a' + i, 16);
        sizes[i] = make_frame(frames[i], i, 1000 + 352 * i, plain);
        memcpy(all + all_len, frames[i], sizes[i]);
        all_len += sizes[i];
    }
    /* Before the anchor the packets wait. Feed byte by byte to test reassembly. */
    for (size_t i = 0; i < all_len; i++) assert(telik_buffered_feed(b, all + i, 1) >= 0);
    telik_buffered_stats(b, &delivered, &bad, &queued);
    assert(count == 0 && queued == 3 && bad == 0);

    /* RTP 1000 plays at 5 s on the sender clock. */
    telik_buffered_set_anchor(b, 1000, 5000000000ULL, 1);
    assert(count == 3);
    assert(last_ts == 1000 + 352 * 2 && last_plain[0] == 'c');
    assert(last_ns == 5000000000ULL + 704ULL * 1000000000ULL / 44100);

    /* A flipped bit fails the tag. */
    frames[0][20] ^= 1;
    assert(telik_buffered_feed(b, frames[0], sizes[0]) == 1);
    telik_buffered_stats(b, &delivered, &bad, &queued);
    assert(bad == 1 && count == 3);

    /* A paused anchor holds packets again. */
    telik_buffered_set_anchor(b, 1000, 5000000000ULL, 0);
    assert(telik_buffered_feed(b, frames[1], sizes[1]) == 1);
    assert(count == 3);
    telik_buffered_flush(b);
    telik_buffered_stats(b, &delivered, &bad, &queued);
    assert(queued == 0);

    telik_buffered_free(b);

    /* The TCP reader takes a frame from a real connection. */
    count = 0;
    b = telik_buffered_new(key, 32, sink, NULL);
    unsigned short port = 0;
    assert(telik_buffered_listen(b, &port) == 0 && port);
    telik_buffered_set_anchor(b, 1000, 5000000000ULL, 1);
    struct sockaddr_in to = { .sin_family = AF_INET, .sin_port = htons(port), .sin_addr.s_addr = htonl(INADDR_LOOPBACK) };
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    assert(connect(fd, (struct sockaddr *) &to, sizeof(to)) == 0);
    memset(plain, 'z', 16);
    sizes[0] = make_frame(frames[0], 7, 1000, plain);
    assert(send(fd, frames[0], sizes[0], 0) == (ssize_t) sizes[0]);
    for (int i = 0; i < 50 && count == 0; i++) usleep(20000);
    assert(count == 1 && last_plain[0] == 'z');
    close(fd);
    telik_buffered_free(b);
    puts("ok");
    return 0;
}
