#!/usr/bin/env python3
"""Loopback-only-ish lab receiver for the emulator's 10.0.2.2 mapping.

The lab target connects to 10.0.2.2:8765 from an Android emulator. This is a
local development test and is not a remote command server.
"""
from __future__ import annotations

import socket

HOST = "127.0.0.1"
PORT = 8765

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen(5)
    print(f"SentinelDroid lab server listening on {HOST}:{PORT}")
    while True:
        conn, addr = server.accept()
        with conn:
            print("connection:", addr)
            data = conn.recv(4096)
            print("payload:", data.decode("utf-8", errors="replace"))
