#!/usr/bin/env python3
"""PadLink 假手机：按 protocol.md 发包，在没有 Android 端时验证 PC 端链路。

用法:
  python tools/fake_pad.py                 # 5 秒演示序列（末尾自动停止，可看到 fail-safe 归零）
  python tools/fake_pad.py --mode hold     # 一直按住 A + 左摇杆推满，Ctrl+C 退出
  python tools/fake_pad.py --mode discover # 广播探测 PC，打印应答
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

DEFAULT_PORT = 42313
DISCOVER_PROBE = b"PADLINK?1"
DEMO_SECONDS = 5.0
STEP_SECONDS = 1.0


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
        ramp = (t - 2.6)                               # 右扳机 0 -> 1，同时按 RB
        frame.rt = int(ramp * 32767)
        frame.buttons = BTN_RB
    elif t < 4.6:
        step = int((t - 3.6) * 8) % 8                   # 十字键顺时针走一圈
        frame.dpad = step + 1
        frame.buttons = BTN_A | BTN_B | BTN_LB | BTN_START
    else:
        pass                                            # 最后归零

    return frame


def demo(sock: socket.socket, target, rate: int, controller: str, player: int) -> None:
    interval = 1.0 / rate
    seq = 0
    started = time.perf_counter()
    next_tick = started

    print(f"向 {target[0]}:{target[1]} 发送演示序列，{rate}Hz，{controller}，player {player}")

    while True:
        elapsed = time.perf_counter() - started
        if elapsed >= DEMO_SECONDS:
            break

        sock.sendto(encode(state_at(elapsed, seq, controller, player)), target)
        seq += 1

        next_tick += interval
        delay = next_tick - time.perf_counter()
        if delay > 0:
            time.sleep(delay)

    print(f"演示结束，共发 {seq} 帧。停发 1 秒观察 PC 端 fail-safe 归零…")
    time.sleep(1.0)


def hold(sock: socket.socket, target, rate: int, controller: str, player: int) -> None:
    interval = 1.0 / rate
    seq = 0
    print(f"持续按住 A、左摇杆推满右上，{rate}Hz。Ctrl+C 退出。")

    while True:
        frame = Field(
            controller=controller, player=player, seq=seq,
            ts=int(time.perf_counter() * 1000) & 0xFFFFFFFF,
            buttons=BTN_A, lx=32767, ly=32767,
        )
        sock.sendto(encode(frame), target)
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
    parser.add_argument("--host", default="127.0.0.1", help="PC 地址，默认 127.0.0.1")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--rate", type=int, default=60, help="发送节拍 Hz，默认 60")
    parser.add_argument("--controller", default="xbox360", choices=["xbox360", "ds4"])
    parser.add_argument("--player", type=int, default=0, choices=[0, 1, 2, 3])
    parser.add_argument("--mode", default="demo", choices=["demo", "hold", "discover"])
    args = parser.parse_args()

    if args.mode == "discover":
        discover(args.port, timeout=2.0)
        return 0

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    target = (args.host, args.port)
    try:
        if args.mode == "demo":
            demo(sock, target, args.rate, args.controller, args.player)
        else:
            hold(sock, target, args.rate, args.controller, args.player)
    except KeyboardInterrupt:
        print("\n已中断。")
    finally:
        sock.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
