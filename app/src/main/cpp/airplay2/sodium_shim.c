#include "sodium.h"

#include <string.h>

#include <openssl/evp.h>
#include <openssl/rand.h>

int sodium_init(void) {
    return 0;
}

int crypto_scalarmult(unsigned char *q, const unsigned char *n, const unsigned char *p) {
    int result = -1;
    size_t len = crypto_scalarmult_BYTES;
    EVP_PKEY *key = EVP_PKEY_new_raw_private_key(EVP_PKEY_X25519, NULL, n, 32);
    EVP_PKEY *peer = EVP_PKEY_new_raw_public_key(EVP_PKEY_X25519, NULL, p, 32);
    EVP_PKEY_CTX *ctx = key ? EVP_PKEY_CTX_new(key, NULL) : NULL;
    if (ctx && peer && EVP_PKEY_derive_init(ctx) == 1 && EVP_PKEY_derive_set_peer(ctx, peer) == 1 &&
        EVP_PKEY_derive(ctx, q, &len) == 1) {
        result = 0;
    }
    EVP_PKEY_CTX_free(ctx);
    EVP_PKEY_free(peer);
    EVP_PKEY_free(key);
    return result;
}

static int public_from_private(int type, unsigned char *pk, const unsigned char *raw) {
    size_t len = 32;
    EVP_PKEY *key = EVP_PKEY_new_raw_private_key(type, NULL, raw, 32);
    int ok = key && EVP_PKEY_get_raw_public_key(key, pk, &len) == 1;
    EVP_PKEY_free(key);
    return ok ? 0 : -1;
}

int crypto_box_keypair(unsigned char *pk, unsigned char *sk) {
    if (RAND_bytes(sk, crypto_box_SECRETKEYBYTES) != 1) return -1;
    return public_from_private(EVP_PKEY_X25519, pk, sk);
}

int crypto_sign_seed_keypair(unsigned char *pk, unsigned char *sk, const unsigned char *seed) {
    unsigned char copy[crypto_sign_SEEDBYTES];
    memcpy(copy, seed, sizeof(copy));
    if (public_from_private(EVP_PKEY_ED25519, pk, copy) != 0) return -1;
    memcpy(sk, copy, sizeof(copy));
    memcpy(sk + sizeof(copy), pk, crypto_sign_PUBLICKEYBYTES);
    return 0;
}

int crypto_sign_keypair(unsigned char *pk, unsigned char *sk) {
    unsigned char seed[crypto_sign_SEEDBYTES];
    if (RAND_bytes(seed, sizeof(seed)) != 1) return -1;
    return crypto_sign_seed_keypair(pk, sk, seed);
}

int crypto_sign_detached(unsigned char *sig, unsigned long long *siglen, const unsigned char *m,
                         unsigned long long mlen, const unsigned char *sk) {
    int result = -1;
    size_t len = crypto_sign_BYTES;
    EVP_PKEY *key = EVP_PKEY_new_raw_private_key(EVP_PKEY_ED25519, NULL, sk, 32);
    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (key && ctx && EVP_DigestSignInit(ctx, NULL, NULL, NULL, key) == 1 &&
        EVP_DigestSign(ctx, sig, &len, m, (size_t) mlen) == 1) {
        if (siglen) *siglen = len;
        result = 0;
    }
    EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(key);
    return result;
}

int crypto_sign_verify_detached(const unsigned char *sig, const unsigned char *m, unsigned long long mlen,
                                const unsigned char *pk) {
    int result = -1;
    EVP_PKEY *key = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, NULL, pk, 32);
    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (key && ctx && EVP_DigestVerifyInit(ctx, NULL, NULL, NULL, key) == 1 &&
        EVP_DigestVerify(ctx, sig, crypto_sign_BYTES, m, (size_t) mlen) == 1) {
        result = 0;
    }
    EVP_MD_CTX_free(ctx);
    EVP_PKEY_free(key);
    return result;
}

int crypto_sign_ed25519_sk_to_pk(unsigned char *pk, const unsigned char *sk) {
    memcpy(pk, sk + crypto_sign_SEEDBYTES, crypto_sign_PUBLICKEYBYTES);
    return 0;
}
