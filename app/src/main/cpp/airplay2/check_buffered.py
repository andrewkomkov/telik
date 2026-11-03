#!/usr/bin/env python3
"""Send buffered audio (SETUP type 103) to a receiver, as an iPhone does on AirPlay 2.

Usage: check_buffered.py HOST [SECONDS]. It needs pyatv, cryptography and ffmpeg.
The receiver must advertise feature bit 40: turn on "Buffered AirPlay audio" in the Telik settings and restart.
The script plays a 440 Hz tone (AAC-LC). It answers the receiver's NTP requests on UDP 7011, sends the
whole tone ahead of time and starts it 1.5 s later with SETRATEANCHORTIME. Then read the receiver log:
"buffered audio packet ... lead" lines show how far ahead of the clock each packet plays.
Pass: you hear the tone and the lead falls by about the packet time with no jump.
"""
import plistlib
import socket
import struct
import subprocess
import sys
import threading
import time

from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305

from check_mirror import Rtsp, pair

NTP_EPOCH = 2208988800
BINARY = "application/x-apple-binary-plist"
TIMING_PORT = 7011


def ntp_now():
    t = time.time() + NTP_EPOCH
    return int(t), int((t % 1) * (1 << 32))


def serve_timing():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", TIMING_PORT))
    while True:
        request, peer = sock.recvfrom(64)
        if len(request) < 32:
            continue
        secs, frac = ntp_now()
        reply = bytearray(32)
        reply[0:4] = bytes([0x80, 0xD3, 0x00, 0x07])
        reply[8:16] = request[24:32]
        reply[16:24] = struct.pack(">II", secs, frac)
        reply[24:32] = struct.pack(">II", secs, frac)
        sock.sendto(bytes(reply), peer)


def aac_frames(seconds):
    """Raw AAC-LC frames of a sine tone, from ADTS."""
    adts = subprocess.run(
        ["ffmpeg", "-v", "error", "-f", "lavfi", "-i", f"sine=frequency=440:duration={seconds}", "-ar", "44100",
         "-ac", "2", "-c:a", "aac", "-f", "adts", "-"], check=True, capture_output=True).stdout
    pos = 0
    while pos + 7 <= len(adts):
        size = ((adts[pos + 3] & 3) << 11) | (adts[pos + 4] << 3) | (adts[pos + 5] >> 5)
        header = 7 if adts[pos + 1] & 1 else 9
        yield adts[pos + header:pos + size]
        pos += size


def main():
    host = sys.argv[1]
    seconds = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    threading.Thread(target=serve_timing, daemon=True).start()
    key = bytes(range(32))
    rtsp = Rtsp(host)
    pair(rtsp)
    rtsp.call("SETUP", "rtsp://telik/1", plistlib.dumps({
        "timingProtocol": "NTP", "timingPort": TIMING_PORT, "deviceID": "02:00:00:00:00:01",
        "name": "check_buffered", "model": "iPhone18,4", "sourceVersion": "1005.13.1",
        "sessionUUID": "00000000-0000-0000-0000-000000000002",
    }, fmt=plistlib.FMT_BINARY), BINARY)
    rtsp.call("RECORD", "rtsp://telik/1")
    reply = plistlib.loads(rtsp.call("SETUP", "rtsp://telik/1", plistlib.dumps({
        "streams": [{"type": 103, "ct": 4, "audioFormat": 0x400000, "spf": 1024, "shk": key,
                     "audioBufferSize": 8388608, "streamConnectionID": 1, "controlPort": 0}],
    }, fmt=plistlib.FMT_BINARY), BINARY))
    print("stream SETUP reply:", reply)
    stream = reply["streams"][0]
    if stream.get("type") != 103 or not stream.get("dataPort"):
        sys.exit("FAIL: the receiver did not accept type 103")

    data = socket.create_connection((host, stream["dataPort"]), timeout=10)
    cipher = ChaCha20Poly1305(key)
    start_rtp = 1 << 20
    secs, frac = ntp_now()
    secs_start = secs + 1
    rtsp.call("SETRATEANCHORTIME", "rtsp://telik/1", plistlib.dumps({
        "rate": 1.0, "rtpTime": start_rtp, "networkTimeSecs": secs_start,
        "networkTimeFrac": frac << 32, "networkTimeTimelineID": 1,
    }, fmt=plistlib.FMT_BINARY), BINARY)
    count = 0
    for frame in aac_frames(seconds):
        header = bytes([0x80]) + count.to_bytes(3, "big") + struct.pack(">II", (start_rtp + 1024 * count) & 0xFFFFFFFF, 1)
        tail = struct.pack("<Q", count)
        sealed = cipher.encrypt(bytes(4) + tail, frame, header[4:12])
        packet = header + sealed + tail
        data.sendall(struct.pack(">H", len(packet) + 2) + packet)
        count += 1
    print("sent", count, "packets, ahead of time")
    time.sleep(seconds + 2)
    rtsp.call("TEARDOWN", "rtsp://telik/1", plistlib.dumps({"streams": [{"type": 103}]}, fmt=plistlib.FMT_BINARY), BINARY)
    print("DONE: read the receiver log")


if __name__ == "__main__":
    main()
