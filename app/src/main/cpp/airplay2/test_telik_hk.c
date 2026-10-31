/* Host test: a pair_ap transient client pairs with telik_hk and exchanges encrypted data in both directions. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "pair.h"
#include "telik_hk.h"

int main(void) {
    telik_hk_t *server = telik_hk_new("AA:BB:CC:DD:EE:FF");
    struct pair_setup_context *client =
        pair_setup_new(PAIR_CLIENT_HOMEKIT_TRANSIENT, NULL, NULL, NULL, "client");
    struct pair_result *result = NULL;
    struct pair_cipher_context *cipher;
    uint8_t *reply = NULL, *request;
    size_t reply_len = 0, request_len = 0;
    uint8_t *cipher_text = NULL, *plain_bytes = NULL;
    size_t cipher_len = 0, plain_bytes_len = 0;
    char *out = NULL;
    size_t out_len = 0;
    const char *message = "GET /info RTSP/1.0\r\nCSeq: 1\r\n\r\n";
    const char *answer = "RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n";
    assert(server && client);

    request = pair_setup_request1(&request_len, client);
    assert(request);
    assert(telik_hk_pair_setup(server, request, request_len, &reply, &reply_len) == 0);
    assert(pair_setup_response1(client, reply, reply_len) == 0);
    free(request);
    free(reply);

    request = pair_setup_request2(&request_len, client);
    assert(request);
    assert(telik_hk_pair_setup(server, request, request_len, &reply, &reply_len) == 0);
    assert(pair_setup_response2(client, reply, reply_len) == 0);
    free(request);
    free(reply);

    /* The reply that ends pairing goes out in plain text. */
    assert(telik_hk_encrypt(server, answer, strlen(answer), &out, &out_len) == 0);
    telik_hk_reply_sent(server);

    assert(pair_setup_result(NULL, &result, client) == 0);
    assert(result->shared_secret_len == 64);
    cipher = pair_cipher_new(PAIR_CLIENT_HOMEKIT_TRANSIENT, PAIR_CHANNEL_CONTROL, result->shared_secret,
                             result->shared_secret_len, NULL);
    assert(cipher);

    assert(pair_encrypt(&cipher_text, &cipher_len, (const uint8_t *) message, strlen(message), cipher) > 0);
    /* A split frame must wait for the rest. */
    assert(telik_hk_decrypt(server, (const char *) cipher_text, 5, &out, &out_len) == 1);
    assert(out_len == 0);
    free(out);
    assert(telik_hk_decrypt(server, (const char *) cipher_text + 5, cipher_len - 5, &out, &out_len) == 1);
    assert(out_len == strlen(message) && !memcmp(out, message, out_len));
    free(out);
    free(cipher_text);

    assert(telik_hk_encrypt(server, answer, strlen(answer), &out, &out_len) == 1);
    assert(pair_decrypt(&plain_bytes, &plain_bytes_len, (const uint8_t *) out, out_len, cipher) == (ssize_t) out_len);
    assert(plain_bytes_len == strlen(answer) && !memcmp(plain_bytes, answer, plain_bytes_len));
    free(out);
    free(plain_bytes);

    {
        /* check_mirror.py tests real frames on a receiver. This test covers bad input. */
        static const uint8_t header[128] = { 1, 2, 3 };
        uint8_t secret[64];
        uint8_t in[32], plain[32];
        telik_stream_t *stream;
        memset(secret, 7, sizeof(secret));
        stream = telik_stream_new(secret, sizeof(secret), 15014746994705656022ULL);
        assert(stream);
        memset(in, 0, sizeof(in));
        /* A forged frame fails and does not use a nonce. */
        assert(telik_stream_decrypt(stream, header, sizeof(header), in, sizeof(in), plain) == -1);
        assert(telik_stream_decrypt(stream, header, sizeof(header), in, 8, plain) == -1);
        assert(telik_audio_decrypt(stream, in, 12, plain) == -1);
        assert(telik_audio_decrypt(stream, in, sizeof(in), plain) == -1);
        telik_stream_free(stream);
    }

    pair_cipher_free(cipher);
    pair_setup_free(client);
    telik_hk_free(server);
    puts("ok");
    return 0;
}
