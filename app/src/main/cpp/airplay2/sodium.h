/* The subset of libsodium that pair_ap uses, implemented with OpenSSL. */
#ifndef TELIK_SODIUM_H
#define TELIK_SODIUM_H

#include <stddef.h>

#define crypto_scalarmult_BYTES 32U
#define crypto_box_PUBLICKEYBYTES 32U
#define crypto_box_SECRETKEYBYTES 32U
#define crypto_sign_BYTES 64U
#define crypto_sign_PUBLICKEYBYTES 32U
#define crypto_sign_SECRETKEYBYTES 64U
#define crypto_sign_SEEDBYTES 32U

int sodium_init(void);
int crypto_scalarmult(unsigned char *q, const unsigned char *n, const unsigned char *p);
int crypto_box_keypair(unsigned char *pk, unsigned char *sk);
int crypto_sign_keypair(unsigned char *pk, unsigned char *sk);
int crypto_sign_seed_keypair(unsigned char *pk, unsigned char *sk, const unsigned char *seed);
int crypto_sign_detached(unsigned char *sig, unsigned long long *siglen, const unsigned char *m,
                         unsigned long long mlen, const unsigned char *sk);
int crypto_sign_verify_detached(const unsigned char *sig, const unsigned char *m, unsigned long long mlen,
                                const unsigned char *pk);
int crypto_sign_ed25519_sk_to_pk(unsigned char *pk, const unsigned char *sk);

#endif
