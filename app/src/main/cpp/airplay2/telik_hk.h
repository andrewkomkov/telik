#ifndef TELIK_HK_H
#define TELIK_HK_H

#include <stddef.h>
#include <stdint.h>

typedef struct telik_hk telik_hk_t;

telik_hk_t *telik_hk_new(const char *device_id);
void telik_hk_free(telik_hk_t *hk);

int telik_hk_pair_setup(telik_hk_t *hk, const uint8_t *in, size_t in_len, uint8_t **out, size_t *out_len);
const char *telik_hk_error(const telik_hk_t *hk);
const uint8_t *telik_hk_secret(const telik_hk_t *hk, size_t *len);

/* The reply that ends pairing goes out in plain text. Call this after it. */
void telik_hk_reply_sent(telik_hk_t *hk);

/* Return 1 when encrypted, 0 when the channel is plain, -1 on error. */
int telik_hk_decrypt(telik_hk_t *hk, const char *in, size_t in_len, char **out, size_t *out_len);
int telik_hk_encrypt(telik_hk_t *hk, const char *in, size_t in_len, char **out, size_t *out_len);

typedef struct telik_stream telik_stream_t;

telik_stream_t *telik_stream_new(const uint8_t *secret, size_t secret_len, uint64_t stream_connection_id);
/* A stream with a key that the sender gave in SETUP (buffered audio). The key is 32 bytes. */
telik_stream_t *telik_stream_new_key(const uint8_t *key, size_t key_len);
void telik_stream_free(telik_stream_t *stream);

int telik_stream_decrypt(telik_stream_t *stream, const uint8_t *header, size_t header_len, const uint8_t *in,
                         int in_len, uint8_t *out);
int telik_audio_decrypt(telik_stream_t *stream, const uint8_t *packet, int packet_len, uint8_t *out);

#endif
