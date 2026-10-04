#!/usr/bin/env python3
"""验证 ADB reverse 的 TCP 通道：PC 端做 server，手机端用 adb shell nc 连过来。

为什么需要这个：
    `adb forward / adb reverse` **只转发 TCP，不支持 UDP**。
    所以 PadLink 的 ADB 模式不能像 WiFi 模式那样用 UDP，必须走 TCP
    （见 protocol/protocol.md §1）。本脚本用来在写 App 之前先证明这条通道成立。

用法:
    python tools/adb_tcp_probe.py [port]        # 默认 42312，先起 server
    # 另开一个终端:
    adb reverse tcp:42312 tcp:42312
    adb shell "echo -n PING | nc 127.0.0.1 42312"
"""

from __future__ import annotations

import socket
import sys
import time

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 42312
TIMEOUT = float(sys.argv[2]) if len(sys.argv) > 2 else 30.0


def main() -> int:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", PORT))
    server.listen(4)
    server.settimeout(TIMEOUT)

    print(f"listening on 127.0.0.1:{PORT} (timeout {TIMEOUT:.0f}s)", flush=True)

    deadline = time.perf_counter() + TIMEOUT
    connections = 0
    while time.perf_counter() < deadline:
        try:
            conn, addr = server.accept()
        except socket.timeout:
            break

        connections += 1
        print(f"connection #{connections} from {addr[0]}:{addr[1]}", flush=True)
        conn.settimeout(3.0)
        try:
            chunk = conn.recv(4096)
            if chunk:
                print(f"  received {len(chunk)} bytes: {chunk!r}", flush=True)
                conn.sendall(b"PONG")
                print("  replied PONG", flush=True)
            else:
                print("  peer closed without data", flush=True)
        except socket.timeout:
            print("  no data within 3s", flush=True)
        finally:
            conn.close()

    server.close()
    print(f"done, {connections} connection(s)", flush=True)
    return 0 if connections else 2


if __name__ == "__main__":
    raise SystemExit(main())
