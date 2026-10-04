#!/usr/bin/env python3
"""PadLink 假手机：按 protocol.md 发包，在没有 Android 端时验证 PC 端链路。

用法:
  python tools/fake_pad.py                    # UDP（WiFi 模式）5 秒演示序列
  python tools/fake_pad.py --transport tcp    # TCP（ADB 模式）5 秒演示序列
  python tools/fake_pad.py --mode hold        # 一直按住 A + 左摇杆推满，Ctrl+C 退出
  python tools/fake_pad.py --mode discover    # 广播探测 PC（仅 UDP 模式有意义）
  python tools/fake_pad.py --host 192.168.1.5 --rate 120 --controller ds4 --player 1
"""

from __future__ import annotations

import argparse
import json
import math
import socket
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_testvectors import (  # noqa: E402
    BTN_A, BTN_B, BTN_LB, BTN_RB, BTN_START, Field, encode,
)

UDP_PORT = 42313
TCP_PORT = 42312
DISCOVER_PROBE = b"PADLINK?1"
DEMO_SECONDS = 5.0


# ---------------------------------------------------------------- 两条传输

class UdpSender:
    """WiFi 模式：单播数据帧，源端口必须稳定（PC 靠它认会话）。"""

    def __init__(self, host: str, port: int):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.target = (host, port)

    def send(self, data: bytes) -> None:
        self.sock.sendto(data, self.target)

    def close(self) -> None:
        self.sock.close()

    def describe(self) -> str:
        return f"UDP {self.target[0]}:{self.target[1]}"


class TcpSender:
    """ADB 模式：PC 做 server，手机做 client 连 127.0.0.1（经 adb reverse 打到 PC）。

    一条连接上混跑 JSON 控制消息和二进制帧，服务端按首字节区分（protocol.md §1.1）。
    """

    def __init__(self, host: str, port: int, controller: str, player: int):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)  # 协议 §7 要求
        self.sock.settimeout(10)
        self.sock.connect((host, port))
        self._buffer = b""

        # 协议 §4.1：先 hello，等 welcome，再 start。
        self._send_line({
            "v": 1, "type": "hello", "device": "fake-pad",
            "codecs": ["binary"], "controller": controller, "players": player + 1,
        })
        print(f"  ← {self._read_line()}")
        self._send_line({"v": 1, "type": "start"})
        print(f"  ← {self._read_line()}")

    def send(self, data: bytes) -> None:
        self.sock.sendall(data)

    def close(self) -> None:
        try:
            self._send_line({"v": 1, "type": "bye"})
        except OSError:
            pass
        self.sock.close()

    def describe(self) -> str:
        return f"TCP {self.sock.getpeername()[0]}:{self.sock.getpeername()[1]}"

    def _send_line(self, payload: dict) -> None:
        self.sock.sendall(json.dumps(payload).encode("utf-8") + b"\n")

    def _read_line(self) -> str:
        while b"\n" not in self._buffer:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("对端关闭了连接")
            self._buffer += chunk
        line, self._buffer = self._buffer.split(b"\n", 1)
        return line.decode("utf-8", "replace")


def make_sender(args):
    if args.transport == "tcp":
        port = args.port or TCP_PORT
        return TcpSender(args.host, port, args.controller, args.player)
    port = args.port or UDP_PORT
    return UdpSender(args.host, port)


# ---------------------------------------------------------------- 输入序列

def state_at(t: float, seq: int, controller: str, player: int) -> Field:
    """演示时间轴：返回 t 秒时刻应发送的状态。"""
    frame = Field(controller=controller, player=player, seq=seq, ts=int(t * 1000) & 0xFFFFFFFF)

    if t < 1.0:
        pass                                            # 1s 静默
    elif t < 1.6:
        frame.buttons = BTN_A                           # A 键
    elif t < 2.6:
        angle = (t - 1.6) * 2 * math.pi                 # 左摇杆画圆
        frame.lx = int(math.cos(angle) * 32767 * 0.8)
        frame.ly = int(math.sin(angle) * 32767 * 0.8)
    elif t < 3.6:
        ramp = (t - 2.6)                                # 右扳机 0 -> 1，同时按 RB
        frame.rt = int(ramp * 32767)
        frame.buttons = BTN_RB
    elif t < 4.6:
        step = int((t - 3.6) * 8) % 8                   # 十字键顺时针走一圈
        frame.dpad = step + 1
        frame.buttons = BTN_A | BTN_B | BTN_LB | BTN_START
    else:
        pass                                            # 最后归零

    return frame


def demo(sender, rate: int, controller: str, player: int) -> None:
    interval = 1.0 / rate
    seq = 0
    started = time.perf_counter()
    next_tick = started

    print(f"向 {sender.describe()} 发送演示序列，{rate}Hz，{controller}，player {player}")

    while True:
        elapsed = time.perf_counter() - started
        if elapsed >= DEMO_SECONDS:
            break

        sender.send(encode(state_at(elapsed, seq, controller, player)))
        seq += 1

        next_tick += interval
        delay = next_tick - time.perf_counter()
        if delay > 0:
            time.sleep(delay)

    print(f"演示结束，共发 {seq} 帧。停发 1 秒观察 PC 端 fail-safe 归零…")
    time.sleep(1.0)


def hold(sender, rate: int, controller: str, player: int) -> None:
    interval = 1.0 / rate
    seq = 0
    print(f"持续按住 A、左摇杆推满右上，{rate}Hz。Ctrl+C 退出。")

    while True:
        frame = Field(
            controller=controller, player=player, seq=seq,
            ts=int(time.perf_counter() * 1000) & 0xFFFFFFFF,
            buttons=BTN_A, lx=32767, ly=32767,
        )
        sender.send(encode(frame))
        seq += 1
        time.sleep(interval)


def discover(port: int, timeout: float) -> None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(timeout)
    sock.sendto(DISCOVER_PROBE, ("255.255.255.255", port))
    print(f"已广播 {DISCOVER_PROBE!r} 到 255.255.255.255:{port}，等待 {timeout}s…")

    deadline = time.perf_counter() + timeout
    while True:
        remaining = deadline - time.perf_counter()
        if remaining <= 0:
            break
        sock.settimeout(remaining)
        try:
            data, addr = sock.recvfrom(2048)
        except socket.timeout:
            break
        text = data.decode("utf-8", "replace")
        print(f"  {addr[0]}:{addr[1]} -> {text}")
        if text.startswith("PADLINK!1 "):
            print(f"  解析: {json.dumps(json.loads(text[len('PADLINK!1 '):]), ensure_ascii=False)}")

    sock.close()


def main() -> int:
    parser = argparse.ArgumentParser(description="PadLink 假手机")
    parser.add_argument("--host", default="127.0.0.1", help="PC 地址，ADB 模式下用 127.0.0.1")
    parser.add_argument("--port", type=int, default=None, help="默认 UDP 42313 / TCP 42312")
    parser.add_argument("--transport", default="udp", choices=["udp", "tcp"])
    parser.add_argument("--rate", type=int, default=60, help="发送节拍 Hz，默认 60")
    parser.add_argument("--controller", default="xbox360", choices=["xbox360", "ds4"])
    parser.add_argument("--player", type=int, default=0, choices=[0, 1, 2, 3])
    parser.add_argument("--mode", default="demo", choices=["demo", "hold", "discover"])
    args = parser.parse_args()

    if args.mode == "discover":
        discover(args.port or UDP_PORT, timeout=2.0)
        return 0

    sender = make_sender(args)
    try:
        if args.mode == "demo":
            demo(sender, args.rate, args.controller, args.player)
        else:
            hold(sender, args.rate, args.controller, args.player)
    except KeyboardInterrupt:
        print("\n已中断。")
    finally:
        sender.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
