"""
효과음 생성기. 외부 음원 없이 파형을 직접 합성해서 frontend/public/sfx/*.wav 를 만든다.
저작권 걱정 없는 자체 제작 사운드. 나중에 마음에 드는 CC0 음원으로 바꿔도 된다.

실행: python3 tools/gen_sfx.py
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "frontend" / "public" / "sfx"


def write(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.85 / peak
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * scale)) * 32767)) for s in samples))


def env(t, attack, decay):
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) / decay)


def tone(freq_fn, duration, attack=0.005, decay=0.1, shape="sine"):
    out, phase = [], 0.0
    for i in range(int(RATE * duration)):
        t = i / RATE
        phase += 2 * math.pi * freq_fn(t) / RATE
        v = math.sin(phase) if shape == "sine" else (1.0 if math.sin(phase) >= 0 else -1.0) * 0.5
        out.append(v * env(t, attack, decay))
    return out


def mix(*tracks, offsets=None):
    offsets = offsets or [0.0] * len(tracks)
    length = max(int(o * RATE) + len(t) for t, o in zip(tracks, offsets))
    out = [0.0] * length
    for t, o in zip(tracks, offsets):
        start = int(o * RATE)
        for i, v in enumerate(t):
            out[start + i] += v
    return out


def swoosh(duration=0.18):
    rng = random.Random(1)
    out, lp = [], 0.0
    for i in range(int(RATE * duration)):
        t = i / RATE
        cutoff = 0.05 + 0.5 * (t / duration)  # 점점 밝아지는 바람 소리
        lp += cutoff * (rng.uniform(-1, 1) - lp)
        out.append(lp * math.sin(math.pi * t / duration))
    return out


def thump():
    body = tone(lambda t: 140 * math.exp(-t * 18) + 45, 0.28, attack=0.002, decay=0.08)
    rng = random.Random(2)
    click = [rng.uniform(-1, 1) * math.exp(-i / (RATE * 0.006)) * 0.6 for i in range(int(RATE * 0.03))]
    return mix(body, click)


def chime(notes, step, decay=0.25):
    tracks = [tone(lambda t, f=f: f, 0.6, attack=0.004, decay=decay) for f in notes]
    return mix(*tracks, offsets=[i * step for i in range(len(notes))])


if __name__ == "__main__":
    write("card", swoosh())
    write("hit", thump())
    write("heal", chime([523.25, 659.25, 783.99], 0.07))
    write("turn", chime([880.0, 1318.5], 0.09, decay=0.12))
    write("eliminate", tone(lambda t: 440 * math.exp(-t * 2.2), 0.7, attack=0.01, decay=0.35, shape="square"))
    write("win", chime([523.25, 659.25, 783.99, 1046.5], 0.11, decay=0.4))
    write("error", tone(lambda t: 160, 0.15, attack=0.003, decay=0.06, shape="square"))
    print("wrote", sorted(p.name for p in OUT.glob("*.wav")))
