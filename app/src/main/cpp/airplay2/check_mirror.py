#!/usr/bin/env python3
"""Send encrypted H.264 mirroring to a receiver, as an iPhone does after transient pairing.

Usage: check_mirror.py HOST STREAM.h264 [SECONDS]. It needs pyatv and cryptography.
Make the stream with: ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=30 -t 5 -c:v libx264 -profile:v baseline
-bf 0 -g 30 -pix_fmt yuv420p -f h264 STREAM.h264
"""
import binascii
import plistlib
import re
import socket
import struct
import sys
import time

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from pyatv.auth import hap_srp, hap_tlv8
from pyatv.auth.hap_session import HAPSession
from pyatv.auth.hap_srp import SRPAuthHandler, hkdf_expand

_srp_session = hap_srp.SRPClientSession
hap_srp.SRPClientSession = lambda context, private: _srp_session(context, private=private)

AGENT = "User-Agent: AirPlay/1005.13.1\r\nConnection: keep-alive\r\n"
STREAM_ID = 15014746994705656022 % (1 << 63)
FP_REQUEST = None


def nal_units(data):
    parts = re.split(b"\x00\x00\x00\x01|\x00\x00\x01", data)
    return [part for part in parts if part]


class Rtsp:
    def __init__(self, host):
        self.sock = socket.create_connection((host, 7000), timeout=10)
        self.session = None
        self.buffer = b""
        self.cseq = 0

    def _read(self):
        data = self.sock.recv(65536)
        if not data:
            sys.exit("FAIL: the receiver closed the connection")
        return self.session.decrypt(data) if self.session else data

    def call(self, method, path, body=b"", content_type=None, hkp=False):
        self.cseq += 1
        head = f"{method} {path} RTSP/1.0\r\nCSeq: {self.cseq}\r\n{AGENT}"
        if hkp:
            head += "X-Apple-HKP: 4\r\n"
        if content_type:
            head += f"Content-Type: {content_type}\r\n"
        data = (head + f"Content-Length: {len(body)}\r\n\r\n").encode() + body
        self.sock.sendall(self.session.encrypt(data) if self.session else data)
        while b"\r\n\r\n" not in self.buffer:
            self.buffer += self._read()
        head, _, rest = self.buffer.partition(b"\r\n\r\n")
        lines = head.decode("latin1").split("\r\n")
        fields = {k.strip().lower(): v.strip() for k, v in (x.split(":", 1) for x in lines[1:])}
        size = int(fields.get("content-length", 0))
        while len(rest) < size:
            rest += self._read()
        self.buffer = rest[size:]
        print(method, path, "->", lines[0], size, "bytes")
        if " 200 " not in lines[0] + " ":
            sys.exit(f"FAIL: {method} {path}")
        return rest[:size]


def pair(rtsp):
    srp = SRPAuthHandler()
    srp.initialize()
    rtsp.call("POST", "/pair-pin-start", hkp=True)
    reply = rtsp.call("POST", "/pair-setup", hap_tlv8.write_tlv({
        hap_tlv8.TlvValue.Method: b"\x00",
        hap_tlv8.TlvValue.SeqNo: b"\x01",
        hap_tlv8.TlvValue.Flags: bytes([hap_tlv8.Flags.TransientPairing.value]),
    }), "application/x-apple-binary-plist", hkp=True)
    pairing = hap_tlv8.read_tlv(reply)
    srp.step1(3939)
    public, proof = srp.step2(pairing[hap_tlv8.TlvValue.PublicKey], pairing[hap_tlv8.TlvValue.Salt])
    reply = rtsp.call("POST", "/pair-setup", hap_tlv8.write_tlv({
        hap_tlv8.TlvValue.SeqNo: b"\x03",
        hap_tlv8.TlvValue.PublicKey: public,
        hap_tlv8.TlvValue.Proof: proof,
    }), "application/x-apple-binary-plist", hkp=True)
    if hap_tlv8.TlvValue.Proof not in hap_tlv8.read_tlv(reply):
        sys.exit("FAIL: M4 has no proof")
    shared = binascii.unhexlify(srp.shared_key)
    rtsp.session = HAPSession()
    rtsp.session.enable(
        hkdf_expand("Control-Salt", "Control-Write-Encryption-Key", shared),
        hkdf_expand("Control-Salt", "Control-Read-Encryption-Key", shared),
    )
    return shared


def stream_key(shared):
    return HKDF(
        algorithm=hashes.SHA512(), length=32, salt=f"DataStream-Salt{STREAM_ID}".encode(),
        info=b"DataStream-Output-Encryption-Key",
    ).derive(shared)


def avcc(sps, pps):
    return (
        bytes([1, sps[1], sps[2], sps[3], 0xFF, 0xE1]) + struct.pack(">H", len(sps)) + sps
        + b"\x01" + struct.pack(">H", len(pps)) + pps
    )


def header(size, kind, option, timestamp, width=0.0, height=0.0):
    data = bytearray(128)
    struct.pack_into("<IHHQ", data, 0, size, kind, option, timestamp)
    if kind == 1:
        struct.pack_into("<ffff", data, 16, width, height, width, height)
        struct.pack_into("<ff", data, 40, width, height)
        struct.pack_into("<ff", data, 56, width, height)
    return bytes(data)


def access_units(nals):
    """Group NAL units into frames: SPS and PPS go alone, and each slice ends a frame."""
    sps = pps = None
    pending = []
    for nal in nals:
        kind = nal[0] & 0x1F
        if kind == 7:
            sps = nal
        elif kind == 8:
            pps = nal
        else:
            pending.append(nal)
            if kind in (1, 5):
                yield sps, pps, pending
                sps = pps = None
                pending = []


def main():
    host, path = sys.argv[1], sys.argv[2]
    seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 4
    rtsp = Rtsp(host)
    shared = pair(rtsp)
    binary = "application/x-apple-binary-plist"
    first = rtsp.call("SETUP", "rtsp://telik/1", plistlib.dumps({
        "timingProtocol": "NTP", "timingPort": 7011, "isScreenMirroringSession": True,
        "deviceID": "02:00:00:00:00:01", "name": "check_mirror", "model": "iPhone18,4",
        "sessionUUID": "00000000-0000-0000-0000-000000000001", "sourceVersion": "1005.13.1",
    }, fmt=plistlib.FMT_BINARY), binary)
    print("first SETUP reply:", plistlib.loads(first))
    rtsp.call("GET", "/info")
    rtsp.call("RECORD", "rtsp://telik/1")
    reply = plistlib.loads(rtsp.call("SETUP", "rtsp://telik/1", plistlib.dumps({
        "streams": [{"type": 110, "latencyMs": 100, "streamConnectionID": STREAM_ID}],
    }, fmt=plistlib.FMT_BINARY), binary))
    print("stream SETUP reply:", reply)
    port = reply["streams"][0]["dataPort"]
    if not port:
        sys.exit("FAIL: no data port")

    cipher = ChaCha20Poly1305(stream_key(shared))
    video = socket.create_connection((host, port), timeout=10)
    count = 0
    timestamp = 1 << 40
    start = time.time()
    nals = nal_units(open(path, "rb").read())
    for sps, pps, frame in access_units(nals):
        if time.time() - start > seconds:
            break
        timestamp += 33_000_000
        if sps and pps:
            data = avcc(sps, pps)
            video.sendall(header(len(data), 1, 0x116, timestamp, 1280.0, 720.0) + data)
        plain = b"".join(struct.pack(">I", len(nal)) + nal for nal in frame)
        kind_option = (0x1000, 0) if any(n[0] & 0x1F == 5 for n in frame) else (0, 0)
        size = len(plain) + 16
        head = header(size, kind_option[0], kind_option[1], timestamp)
        nonce = bytes(4) + struct.pack("<Q", count)
        video.sendall(head + cipher.encrypt(nonce, plain, head))
        count += 1
        time.sleep(1 / 30)
    print("sent", count, "encrypted frames")
    rtsp.call("TEARDOWN", "rtsp://telik/1", plistlib.dumps({"streams": [{"type": 110}]}, fmt=plistlib.FMT_BINARY), binary)
    print("DONE: read the receiver log")


if __name__ == "__main__":
    main()
