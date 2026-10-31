#!/usr/bin/env python3
"""Check AirPlay 2 stage 2 on a real receiver without an iPhone.

Usage: check_transient.py HOST [PORT]. It needs pyatv.
It does transient pairing, then sends GET /info on the encrypted channel.
"""
import binascii
import plistlib
import socket
import sys

from pyatv.auth.hap_session import HAPSession
from pyatv.auth import hap_srp
from pyatv.auth.hap_srp import SRPAuthHandler, hkdf_expand
from pyatv.auth import hap_tlv8

# pyatv passes the private key by position. New srptools versions accept it only by keyword.
_srp_session = hap_srp.SRPClientSession
hap_srp.SRPClientSession = lambda context, private: _srp_session(context, private=private)

HEADERS = "User-Agent: AirPlay/1005.13.1\r\nConnection: keep-alive\r\nX-Apple-HKP: 4\r\n"


def read_response(read):
    data = b""
    while b"\r\n\r\n" not in data:
        data += read()
    head, _, body = data.partition(b"\r\n\r\n")
    lines = head.decode("latin1").split("\r\n")
    fields = {k.strip().lower(): v.strip() for k, v in (x.split(":", 1) for x in lines[1:])}
    size = int(fields.get("content-length", 0))
    while len(body) < size:
        body += read()
    return lines[0], fields, body


def request(method, path, cseq, body=b"", content_type="application/octet-stream"):
    head = f"{method} {path} RTSP/1.0\r\nCSeq: {cseq}\r\n{HEADERS}Content-Type: {content_type}\r\n"
    return (head + f"Content-Length: {len(body)}\r\n\r\n").encode() + body


def main():
    host = sys.argv[1]
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 7000
    sock = socket.create_connection((host, port), timeout=10)
    plain = lambda: sock.recv(4096) or sys.exit("connection closed")

    sock.sendall(request("POST", "/pair-pin-start", 1))
    status, _, _ = read_response(plain)
    print("pair-pin-start:", status)
    if " 200 " not in status + " ":
        sys.exit("FAIL: pair-pin-start")

    srp = SRPAuthHandler()
    srp.initialize()
    m1 = hap_tlv8.write_tlv({
        hap_tlv8.TlvValue.Method: b"\x00",
        hap_tlv8.TlvValue.SeqNo: b"\x01",
        hap_tlv8.TlvValue.Flags: bytes([hap_tlv8.Flags.TransientPairing.value]),
    })
    print("M1 length:", len(m1), "hex:", m1.hex())
    sock.sendall(request("POST", "/pair-setup", 2, m1))
    status, _, body = read_response(plain)
    print("M2:", status, len(body), "bytes")
    pairing = hap_tlv8.read_tlv(body)
    if hap_tlv8.TlvValue.Error in pairing:
        sys.exit(f"FAIL: M2 error {pairing[hap_tlv8.TlvValue.Error].hex()}")

    srp.step1(3939)
    public, proof = srp.step2(pairing[hap_tlv8.TlvValue.PublicKey], pairing[hap_tlv8.TlvValue.Salt])
    m3 = hap_tlv8.write_tlv({
        hap_tlv8.TlvValue.SeqNo: b"\x03",
        hap_tlv8.TlvValue.PublicKey: public,
        hap_tlv8.TlvValue.Proof: proof,
    })
    sock.sendall(request("POST", "/pair-setup", 3, m3))
    status, _, body = read_response(plain)
    print("M4:", status, len(body), "bytes")
    pairing = hap_tlv8.read_tlv(body)
    if hap_tlv8.TlvValue.Error in pairing:
        sys.exit(f"FAIL: M4 error {pairing[hap_tlv8.TlvValue.Error].hex()}")
    if hap_tlv8.TlvValue.Proof not in pairing:
        sys.exit("FAIL: M4 has no proof")

    shared = binascii.unhexlify(srp.shared_key)
    session = HAPSession()
    session.enable(
        hkdf_expand("Control-Salt", "Control-Write-Encryption-Key", shared),
        hkdf_expand("Control-Salt", "Control-Read-Encryption-Key", shared),
    )

    def encrypted():
        data = sock.recv(4096)
        if not data:
            sys.exit("FAIL: the receiver closed the encrypted channel")
        return session.decrypt(data)

    sock.sendall(session.encrypt(f"GET /info RTSP/1.0\r\nCSeq: 4\r\n{HEADERS}\r\n".encode()))
    status, _, body = read_response(encrypted)
    print("GET /info:", status, len(body), "bytes")
    info = plistlib.loads(body)
    print("encrypted plist keys:", sorted(info)[:8], "features:", hex(info.get("features", 0)))
    if "features" not in info:
        sys.exit("FAIL: /info has no features")
    print("PASS")


if __name__ == "__main__":
    main()
