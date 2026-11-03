#ifndef TELIK_BUFFERED_H
#define TELIK_BUFFERED_H

/* Buffered audio (SETUP type 103). The sender opens a TCP connection and pushes encrypted packets ahead of time.
 * There is no PTP: the receiver maps RTP time to the sender clock with the anchor from SETRATEANCHORTIME. */

#include <stddef.h>
#include <stdint.h>

#define TELIK_BUFFERED_RATE 44100

typedef struct telik_buffered telik_buffered_t;

/* Gets one decrypted packet. remote_ns is the play time on the sender clock, in nanoseconds. */
typedef void (*telik_buffered_sink)(void *cls, const uint8_t *data, int len, uint32_t rtp_ts, uint32_t seq,
                                    uint64_t remote_ns);

telik_buffered_t *telik_buffered_new(const uint8_t *key, size_t key_len, telik_buffered_sink sink, void *cls);

/* Stops the reader thread and frees everything. */
void telik_buffered_free(telik_buffered_t *b);

/* Opens a TCP port and starts the reader thread. Returns 0 on success. */
int telik_buffered_listen(telik_buffered_t *b, unsigned short *port);

/* Packets before the first anchor, or while paused, wait in a queue. */
void telik_buffered_set_anchor(telik_buffered_t *b, uint32_t rtp_ts, uint64_t remote_ns, int playing);
void telik_buffered_flush(telik_buffered_t *b);

/* Takes raw bytes from the connection: 2-byte big-endian length (it counts itself), then the packet.
 * Returns the number of packets decrypted, or -1 on a bad frame. */
int telik_buffered_feed(telik_buffered_t *b, const uint8_t *bytes, size_t len);

/* Counters for the drift log. */
void telik_buffered_stats(const telik_buffered_t *b, unsigned *delivered, unsigned *bad, unsigned *queued);

#endif
