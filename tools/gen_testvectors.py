"""PadLink 协议 v1 测试向量生成器。

这个脚本同时是协议的**参考实现**：
  encode() -> 字节 -> decode() -> 字段，自校验后写出 protocol/testvectors/frames.json。
C# 与 Kotlin 两端的编解码测试都以该文件为准。

用法: python tools/gen_testvectors.py
"""

from __future__ import annotations

import json
import struct
from pathlib import Path

MAGIC = b"PL"
VER = 1
FRAME_SIZE = 28

CONTROLLERS = {"xbox360": 0, "ds4": 1, "dualsense": 2, "switchpro": 3}
CONTROLLER_NAMES = {v: k for k, v in CONTROLLERS.items()}

RUMBLE_TYPE = 0x01
RUMBLE_SIZE = 8

# buttons 位掩码（物理位置语义，见 protocol.md §5.2）
BTN_A = 1 << 0
BTN_B = 1 << 1
BTN_X = 1 << 2
BTN_Y = 1 << 3
BTN_LB = 1 << 4
BTN_RB = 1 << 5
BTN_BACK = 1 << 6
BTN_START = 1 << 7
BTN_L3 = 1 << 8
BTN_R3 = 1 << 9
BTN_GUIDE = 1 << 10
BTN_TOUCHPAD = 1 << 11


class Field:
    """一帧解码后的字段值（与 protocol.md §5 一一对应）。"""

    __slots__ = ("player", "controller", "rumble_ack", "seq", "ts",
                 "buttons", "dpad", "lx", "ly", "rx", "ry", "lt", "rt")

    def __init__(self, player=0, controller="xbox360", rumble_ack=False, seq=0,
                 ts=0, buttons=0, dpad=0, lx=0, ly=0, rx=0, ry=0, lt=0, rt=0):
        self.player = player
        self.controller = controller
        self.rumble_ack = rumble_ack
        self.seq = seq
        self.ts = ts
        self.buttons = buttons
        self.dpad = dpad
        self.lx, self.ly, self.rx, self.ry = lx, ly, rx, ry
        self.lt, self.rt = lt, rt

    def to_dict(self) -> dict:
        return {
            "player": self.player,
            "controller": self.controller,
            "rumble_ack": self.rumble_ack,
            "seq": self.seq,
            "ts": self.ts,
            "buttons": self.buttons,
            "dpad": self.dpad,
            "lx": self.lx, "ly": self.ly, "rx": self.rx, "ry": self.ry,
            "lt": self.lt, "rt": self.rt,
        }


def encode(f: Field) -> bytes:
    if not 0 <= f.player <= 3:
        raise ValueError("player 0..3")
    if f.controller not in CONTROLLERS:
        raise ValueError(f"unknown controller {f.controller}")
    if not 0 <= f.buttons <= 0xFFFF:
        raise ValueError("buttons 16bit")
    if not 0 <= f.dpad <= 8:
        raise ValueError("dpad 0..8")
    for name in ("lx", "ly", "rx", "ry"):
        if not -32768 <= getattr(f, name) <= 32767:
            raise ValueError(f"{name} int16")
    for name in ("lt", "rt"):
        if not 0 <= getattr(f, name) <= 32767:
            raise ValueError(f"{name} uint16 0..32767")

    attr = (f.player & 0x03) | ((CONTROLLERS[f.controller] & 0x07) << 2)
    if f.rumble_ack:
        attr |= 1 << 5
    return b"".join([
        MAGIC,
        bytes([VER, attr]),
        struct.pack("<I", f.seq & 0xFFFFFFFF),
        struct.pack("<I", f.ts & 0xFFFFFFFF),
        struct.pack("<H", f.buttons),
        bytes([f.dpad, 0x00]),                      # dpad + reserved
        struct.pack("<hhhh", f.lx, f.ly, f.rx, f.ry),
        struct.pack("<HH", f.lt, f.rt),
    ])


def decode(buf: bytes) -> Field:
    if len(buf) != FRAME_SIZE:
        raise ValueError(f"frame size must be {FRAME_SIZE}, got {len(buf)}")
    if buf[0:2] != MAGIC:
        raise ValueError("bad magic")
    ver, attr = buf[2], buf[3]
    if ver != VER:
        raise ValueError(f"unsupported ver {ver}")
    if buf[15] != 0:
        raise ValueError("reserved byte must be 0")
    controller = CONTROLLER_NAMES.get((attr >> 2) & 0x07)
    if controller is None:
        raise ValueError("unknown controller id")
    lx, ly, rx, ry = struct.unpack_from("<hhhh", buf, 16)
    lt, rt = struct.unpack_from("<HH", buf, 24)
    return Field(
        player=attr & 0x03,
        controller=controller,
        rumble_ack=bool((attr >> 5) & 1),
        seq=struct.unpack_from("<I", buf, 4)[0],
        ts=struct.unpack_from("<I", buf, 8)[0],
        buttons=struct.unpack_from("<H", buf, 12)[0],
        dpad=buf[14],
        lx=lx, ly=ly, rx=rx, ry=ry, lt=lt, rt=rt,
    )


def encode_json(f: Field) -> dict:
    """§5.1 调试用的 JSON 形式，字段名与二进制一致。"""
    d = f.to_dict()
    return {
        "v": VER,
        "player": d["player"],
        "controller": d["controller"],
        "seq": d["seq"],
        "ts": d["ts"],
        "buttons": d["buttons"],
        "dpad": d["dpad"],
        "lx": d["lx"], "ly": d["ly"], "rx": d["rx"], "ry": d["ry"],
        "lt": d["lt"], "rt": d["rt"],
    }


def encode_rumble(player: int, left: int, right: int) -> bytes:
    return bytes([MAGIC[0], MAGIC[1], VER, RUMBLE_TYPE, player, left, right, 0x00])


# ---------------------------------------------------------------- 向量定义

VALID = [
    ("idle", "初始静默帧：全部归零，Xbox 360，player 0",
     Field()),

    ("a_pressed", "A(bit0) 按下",
     Field(seq=1, ts=1000, buttons=BTN_A)),

    ("sticks_corners", "左摇杆推到右上极限、右摇杆推左下极限",
     Field(seq=2, ts=1016, lx=32767, ly=32767, rx=-32767, ry=-32767)),

    ("triggers_full", "双扳机踩满",
     Field(seq=3, ts=1032, lt=32767, rt=32767)),

    ("ds4_dpad_east", "DS4 + 十字键向东",
     Field(controller="ds4", seq=4, ts=1048, dpad=3)),

    ("all_buttons", "bit0-11 全按下（含 Touchpad），player 2",
     Field(player=2, seq=5, ts=1064, buttons=0x0FFF, rumble_ack=True)),

    ("seq_wrap", "seq 回绕边界，player 3",
     Field(player=3, seq=0xFFFFFFFF, ts=0xFFFFFFFF)),

    ("ds4_negative_quarter", "DS4，左摇杆左上四分之一，Y 轴为负",
     Field(controller="ds4", seq=6, ts=1080, lx=-8192, ly=-8192, rt=16384)),
]

INVALID = [
    ("bad_magic", "magic 被改坏", b"LP" + encode(Field(seq=7))[2:]),
    ("short_frame", "长度只有 20 字节", encode(Field(seq=8))[:20]),
    ("long_frame", "长度 30 字节", encode(Field(seq=8)) + b"\x00\x00"),
    ("bad_reserved", "offset 15 保留字节非 0",
     encode(Field(seq=9))[:15] + b"\xff" + encode(Field(seq=9))[16:]),
    ("bad_ver", "ver = 9",
     encode(Field(seq=10))[:2] + b"\x09" + encode(Field(seq=10))[3:]),
    ("bad_controller", "controller id = 5（未定义）",
     encode(Field(seq=11))[:3] + bytes([5 << 2]) + encode(Field(seq=11))[4:]),
]


def main() -> None:
    vectors = []
    for name, desc, f in VALID:
        raw = encode(f)
        if len(raw) != FRAME_SIZE:
            raise AssertionError(f"{name}: encoded {len(raw)} bytes, want {FRAME_SIZE}")
        back = decode(raw)
        if back.to_dict() != f.to_dict():
            raise AssertionError(f"{name}: roundtrip mismatch\n  {back.to_dict()}\n  {f.to_dict()}")
        vectors.append({
            "name": name,
            "desc": desc,
            "bytes": raw.hex(),
            "fields": f.to_dict(),
            "json": encode_json(f),
        })

    # 非法帧必须解码失败
    for name, desc, raw in INVALID:
        try:
            decode(raw)
        except Exception:
            pass
        else:
            raise AssertionError(f"{name}: expected decode failure")

    # 震动帧
    rumble = encode_rumble(player=0, left=178, right=76)
    if len(rumble) != RUMBLE_SIZE:
        raise AssertionError("rumble size")

    doc = {
        "protocol": "padlink",
        "ver": VER,
        "frame_size": FRAME_SIZE,
        "rumble_size": RUMBLE_SIZE,
        "magic": "504c",
        "button_bits": {
            "a": BTN_A, "b": BTN_B, "x": BTN_X, "y": BTN_Y,
            "lb": BTN_LB, "rb": BTN_RB, "back": BTN_BACK, "start": BTN_START,
            "l3": BTN_L3, "r3": BTN_R3, "guide": BTN_GUIDE, "touchpad": BTN_TOUCHPAD,
        },
        "controllers": CONTROLLERS,
        "vectors": vectors,
        "invalid": [{"name": n, "desc": d, "bytes": h.hex()} for n, d, h in INVALID],
        "rumble": {
            "desc": "PC -> 手机，left=178 right=76",
            "bytes": rumble.hex(),
            "player": 0, "left": 178, "right": 76,
        },
    }

    out = Path(__file__).resolve().parent.parent / "protocol" / "testvectors" / "frames.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out}")
    print(f"valid={len(vectors)} invalid={len(INVALID)} frame_size={FRAME_SIZE}")


if __name__ == "__main__":
    main()
