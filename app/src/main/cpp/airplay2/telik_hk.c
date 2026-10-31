#include "telik_hk.h"

#include <stdlib.h>
#include <string.h>

#include <openssl/evp.h>
#include <openssl/kdf.h>
#include <stdio.h>

#include "pair.h"

#define STREAM_TAG_SIZE 16
#define STREAM_KEY_SIZE 32

static int airplay2_enabled = 1;

void telik_airplay2_set_enabled(int enabled) {
    airplay2_enabled = enabled;
}

int telik_airplay2_enabled(void) {
    return airplay2_enabled;
}

const char *telik_version(void) {
    return airplay2_enabled ? "366.0" : "220.68";
}

const char *telik_server_name(void) {
    return airplay2_enabled ? "AirTunes/366.0" : "AirTunes/220.68";
}

struct telik_hk {
    char *device_id;
    struct pair_setup_context *setup;
    struct pair_cipher_context *pending;
    struct pair_cipher_context *cipher;
    const char *error;
    uint8_t *rx;
    size_t rx_len;
    uint8_t secret[64];
    size_t secret_len;
};

telik_hk_t *telik_hk_new(const char *device_id) {
    telik_hk_t *hk = calloc(1, sizeof(*hk));
    if (hk) hk->device_id = strdup(device_id);
    return hk;
}

void telik_hk_free(telik_hk_t *hk) {
    if (!hk) return;
    if (hk->setup) pair_setup_free(hk->setup);
    if (hk->pending) pair_cipher_free(hk->pending);
    if (hk->cipher) pair_cipher_free(hk->cipher);
    free(hk->device_id);
    free(hk->rx);
    free(hk);
}

const char *telik_hk_error(const telik_hk_t *hk) {
    return hk->error ? hk->error : "unknown error";
}

int telik_hk_pair_setup(telik_hk_t *hk, const uint8_t *in, size_t in_len, uint8_t **out, size_t *out_len) {
    struct pair_result *result = NULL;
    /* Step 1 starts a new pairing. A sender can retry on the same connection. */
    if (hk->setup && pair_state_get(PAIR_SERVER_HOMEKIT, &hk->error, in, in_len) == 1) {
        pair_setup_free(hk->setup);
        hk->setup = NULL;
    }
    if (!hk->setup) {
        /* NULL selects the fixed transient code. */
        hk->setup = pair_setup_new(PAIR_SERVER_HOMEKIT, NULL, NULL, NULL, hk->device_id);
        if (!hk->setup) {
            hk->error = "cannot create the pairing context";
            return -1;
        }
    }
    if (pair_setup(out, out_len, hk->setup, in, in_len) < 0) {
        hk->error = pair_setup_errmsg(hk->setup);
        return -1;
    }
    if (pair_setup_result(NULL, &result, hk->setup) == 0 && result->shared_secret_len > 0) {
        if (hk->pending) pair_cipher_free(hk->pending);
        hk->pending = pair_cipher_new(PAIR_SERVER_HOMEKIT, PAIR_CHANNEL_CONTROL, result->shared_secret,
                                      result->shared_secret_len, NULL);
        if (!hk->pending) {
            hk->error = "cannot create the cipher";
            free(*out);
            *out = NULL;
            return -1;
        }
        hk->secret_len = result->shared_secret_len < sizeof(hk->secret) ? result->shared_secret_len : sizeof(hk->secret);
        memcpy(hk->secret, result->shared_secret, hk->secret_len);
    }
    return 0;
}

const uint8_t *telik_hk_secret(const telik_hk_t *hk, size_t *len) {
    if (!hk->secret_len) return NULL;
    *len = hk->secret_len;
    return hk->secret;
}

void telik_hk_reply_sent(telik_hk_t *hk) {
    if (!hk->pending) return;
    if (hk->cipher) pair_cipher_free(hk->cipher);
    hk->cipher = hk->pending;
    hk->pending = NULL;
    hk->rx_len = 0;
}

int telik_hk_decrypt(telik_hk_t *hk, const char *in, size_t in_len, char **out, size_t *out_len) {
    uint8_t *plain = NULL;
    size_t plain_len = 0;
    ssize_t used;
    uint8_t *grown;
    if (!hk->cipher) return 0;
    grown = realloc(hk->rx, hk->rx_len + in_len);
    if (!grown) return -1;
    hk->rx = grown;
    memcpy(hk->rx + hk->rx_len, in, in_len);
    hk->rx_len += in_len;
    used = pair_decrypt(&plain, &plain_len, hk->rx, hk->rx_len, hk->cipher);
    if (used < 0) {
        hk->error = pair_cipher_errmsg(hk->cipher);
        return -1;
    }
    hk->rx_len -= (size_t) used;
    memmove(hk->rx, hk->rx + used, hk->rx_len);
    *out = (char *) plain;
    *out_len = plain ? plain_len : 0;
    return 1;
}

int telik_hk_encrypt(telik_hk_t *hk, const char *in, size_t in_len, char **out, size_t *out_len) {
    uint8_t *cipher_text = NULL;
    size_t cipher_len = 0;
    if (!hk->cipher) return 0;
    if (pair_encrypt(&cipher_text, &cipher_len, (const uint8_t *) in, in_len, hk->cipher) < 0) {
        hk->error = pair_cipher_errmsg(hk->cipher);
        return -1;
    }
    *out = (char *) cipher_text;
    *out_len = cipher_len;
    return 1;
}

struct telik_stream {
    uint8_t key[STREAM_KEY_SIZE];
    uint64_t count;
    EVP_CIPHER_CTX *ctx;
};

static int derive_stream_key(uint8_t *key, const uint8_t *secret, size_t secret_len, uint64_t id) {
    static const char info[] = "DataStream-Output-Encryption-Key";
    char salt[64];
    size_t key_len = STREAM_KEY_SIZE;
    int salt_len = snprintf(salt, sizeof(salt), "DataStream-Salt%llu", (unsigned long long) id);
    EVP_PKEY_CTX *ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_HKDF, NULL);
    int ok = ctx && EVP_PKEY_derive_init(ctx) == 1 && EVP_PKEY_CTX_set_hkdf_md(ctx, EVP_sha512()) == 1 &&
             EVP_PKEY_CTX_set1_hkdf_salt(ctx, (const unsigned char *) salt, salt_len) == 1 &&
             EVP_PKEY_CTX_set1_hkdf_key(ctx, secret, (int) secret_len) == 1 &&
             EVP_PKEY_CTX_add1_hkdf_info(ctx, (const unsigned char *) info, sizeof(info) - 1) == 1 &&
             EVP_PKEY_derive(ctx, key, &key_len) == 1;
    EVP_PKEY_CTX_free(ctx);
    return ok ? 0 : -1;
}

telik_stream_t *telik_stream_new(const uint8_t *secret, size_t secret_len, uint64_t stream_connection_id) {
    telik_stream_t *stream = calloc(1, sizeof(*stream));
    if (!stream) return NULL;
    stream->ctx = EVP_CIPHER_CTX_new();
    if (!stream->ctx || derive_stream_key(stream->key, secret, secret_len, stream_connection_id) != 0) {
        telik_stream_free(stream);
        return NULL;
    }
    return stream;
}

void telik_stream_free(telik_stream_t *stream) {
    if (!stream) return;
    EVP_CIPHER_CTX_free(stream->ctx);
    free(stream);
}

static int open_frame(telik_stream_t *stream, const uint8_t *nonce, const uint8_t *aad, size_t aad_len,
                      const uint8_t *in, int plain_len, uint8_t *out) {
    int len = 0, tail = 0;
    if (EVP_DecryptInit_ex(stream->ctx, EVP_chacha20_poly1305(), NULL, stream->key, nonce) != 1 ||
        EVP_DecryptUpdate(stream->ctx, NULL, &len, aad, (int) aad_len) != 1 ||
        EVP_DecryptUpdate(stream->ctx, out, &len, in, plain_len) != 1 ||
        EVP_CIPHER_CTX_ctrl(stream->ctx, EVP_CTRL_AEAD_SET_TAG, STREAM_TAG_SIZE, (void *) (in + plain_len)) != 1 ||
        EVP_DecryptFinal_ex(stream->ctx, out + len, &tail) != 1) {
        return -1;
    }
    return len + tail;
}

int telik_stream_decrypt(telik_stream_t *stream, const uint8_t *header, size_t header_len, const uint8_t *in,
                         int in_len, uint8_t *out) {
    uint8_t nonce[12] = { 0 };
    int plain_len = in_len - STREAM_TAG_SIZE;
    int result;
    if (!stream || plain_len < 0) return -1;
    for (int i = 0; i < 8; i++) nonce[4 + i] = (uint8_t) (stream->count >> (8 * i));
    result = open_frame(stream, nonce, header, header_len, in, plain_len, out);
    if (result >= 0) stream->count++;
    return result;
}

int telik_audio_decrypt(telik_stream_t *stream, const uint8_t *packet, int packet_len, uint8_t *out) {
    enum { HEADER = 12, NONCE_TAIL = 8 };
    uint8_t nonce[12] = { 0 };
    int plain_len = packet_len - HEADER - NONCE_TAIL - STREAM_TAG_SIZE;
    if (!stream || plain_len < 0) return -1;
    memcpy(nonce + 4, packet + packet_len - NONCE_TAIL, NONCE_TAIL);
    return open_frame(stream, nonce, packet + 4, 8, packet + HEADER, plain_len, out);
}
