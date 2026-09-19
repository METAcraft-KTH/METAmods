#!/usr/bin/env python3
"""Send one command to the Rivals dev server over RCON and print the reply.

    python3 mods/rivals-paint/tools/rcon.py 'rivals setup'
    python3 mods/rivals-paint/tools/rcon.py 'op Edvin'

Defaults match the dev run/server.properties (rcon.port=25576, rcon.password=rivals-dev);
override with RCON_HOST, RCON_PORT and RCON_PASSWORD. Standard library only.
"""
import os
import socket
import struct
import sys

HOST = os.environ.get("RCON_HOST", "127.0.0.1")
PORT = int(os.environ.get("RCON_PORT", "25576"))
PASSWORD = os.environ.get("RCON_PASSWORD", "rivals-dev")

LOGIN = 3
COMMAND = 2


def send(sock, request_id, kind, payload):
    body = struct.pack("<ii", request_id, kind) + payload.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)


def recv(sock):
    (length,) = struct.unpack("<i", sock.recv(4))
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    request_id, kind = struct.unpack("<ii", data[:8])
    return request_id, kind, data[8:-2].decode(errors="replace")


def main(command):
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        send(sock, 1, LOGIN, PASSWORD)
        request_id, _, _ = recv(sock)
        if request_id == -1:
            sys.exit("rcon: wrong password")
        send(sock, 2, COMMAND, command)
        _, _, reply = recv(sock)
        print(reply)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(" ".join(sys.argv[1:]))
