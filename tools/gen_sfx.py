"""
효과음 생성기. 외부 음원 없이 파형을 직접 합성해서 frontend/public/sfx/*.wav 를 만든다.
저작권 걱정 없는 자체 제작 사운드. 나중에 마음에 드는 CC0 음원으로 바꿔도 된다.

실행: python3 tools/gen_sfx.py
      python3 tools/gen_sfx.py --candidates <폴더>   # 기본 효과음 후보(레트로 8비트 느낌)를 따로 만든다
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parent.parent / "frontend" / "public" / "sfx"


def write(name, samples, out=OUT):
    out.mkdir(parents=True, exist_ok=True)
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 0.85 / peak
    with wave.open(str(out / f"{name}.wav"), "wb") as w:
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
        if shape == "sine":
            v = math.sin(phase)
        elif shape == "square":
            v = (1.0 if math.sin(phase) >= 0 else -1.0) * 0.5
        else:  # triangle
            v = 2 / math.pi * math.asin(math.sin(phase))
        out.append(v * env(t, attack, decay))
    return out


def noise(duration, decay, seed=3, crush=1, pitch_fall=0.0):
    """8비트 잡음: crush 샘플마다 값을 유지해 거칠게, pitch_fall 이면 갈수록 더 거칠어진다"""
    rng = random.Random(seed)
    out, held = [], 0.0
    for i in range(int(RATE * duration)):
        t = i / RATE
        step = max(1, int(crush * (1 + pitch_fall * t)))
        if i % step == 0:
            held = rng.uniform(-1, 1)
        out.append(held * math.exp(-t / decay))
    return out


def arp(freqs, step, shape="square", decay=0.12, length=None):
    """아르페지오: 음을 step 간격으로 하나씩 (8비트 게임 효과음)"""
    tracks = [tone(lambda t, f=f: f, length or step * 2.5, attack=0.002, decay=decay, shape=shape) for f in freqs]
    return mix(*tracks, offsets=[i * step for i in range(len(freqs))])


def candidates():
    """기본 효과음 후보. 이름: <종류>_<번호>"""
    return {
        # 카드 내기
        "card_1": mix(tone(lambda t: 600 + 2400 * t / 0.08, 0.08, attack=0.002, decay=0.05, shape="square")),
        "card_2": mix(noise(0.07, 0.02, seed=5, crush=1), tone(lambda t: 1800, 0.03, decay=0.01)),
        "card_3": mix(thump(), noise(0.05, 0.015, seed=7, crush=2)),
        # 맞음
        "hit_1": noise(0.35, 0.09, seed=11, crush=2, pitch_fall=30),
        "hit_2": mix(tone(lambda t: 220 * math.exp(-t * 10) + 50, 0.25, attack=0.001, decay=0.07, shape="square"), noise(0.06, 0.02, seed=12)),
        "hit_3": tone(lambda t: 880 * math.exp(-t * 9), 0.22, attack=0.002, decay=0.09, shape="square"),
        # 회복
        "heal_1": arp([523.25, 659.25, 783.99, 1046.5], 0.05, shape="square", decay=0.08),
        "heal_2": arp([1318.5, 1567.98, 2093.0, 2637.0], 0.045, shape="sine", decay=0.15),
        "heal_3": tone(lambda t: 300 + 900 * t / 0.4, 0.4, attack=0.05, decay=0.25, shape="triangle"),
        # 내 차례
        "turn_1": mix(tone(lambda t: 1200, 0.5, attack=0.002, decay=0.08), tone(lambda t: 1200, 0.5, attack=0.002, decay=0.08), offsets=[0, 0.16]),
        "turn_2": arp([987.77, 1318.5], 0.08, shape="square", decay=0.08),
        "turn_3": mix(*(tone(lambda t: 1500, 0.06, attack=0.002, decay=0.03, shape="square") for _ in range(3)), offsets=[0, 0.09, 0.18]),
        # 탈락
        "eliminate_1": arp([783.99, 659.25, 523.25, 392.0, 261.63], 0.1, shape="square", decay=0.1),
        "eliminate_2": noise(0.9, 0.3, seed=21, crush=3, pitch_fall=12),
        "eliminate_3": mix(*(tone(lambda t, f=f: f * (1 - 0.06 * t), 0.45 if i < 3 else 0.9, attack=0.02, decay=0.25 if i < 3 else 0.5, shape="triangle")
                             for i, f in enumerate([392.0, 369.99, 349.23, 329.63])), offsets=[0, 0.35, 0.7, 1.05]),
        # 승리
        "win_1": arp([523.25, 523.25, 523.25, 698.46, 880.0, 1046.5], 0.11, shape="square", decay=0.12),
        "win_2": mix(arp([523.25, 659.25, 783.99, 1046.5], 0.07, shape="square", decay=0.1),
                     tone(lambda t: 1046.5, 0.8, attack=0.01, decay=0.4, shape="triangle"), offsets=[0, 0.3]),
        "win_3": arp([392.0, 523.25, 659.25, 783.99, 1046.5, 1318.5], 0.06, shape="triangle", decay=0.2),
        # 오류
        "error_1": mix(tone(lambda t: 110, 0.12, attack=0.002, decay=0.08, shape="square"), tone(lambda t: 110, 0.12, attack=0.002, decay=0.08, shape="square"), offsets=[0, 0.15]),
        "error_2": tone(lambda t: 2000, 0.06, attack=0.001, decay=0.03, shape="square"),
        "error_3": tone(lambda t: 600 * math.exp(-t * 8), 0.18, attack=0.002, decay=0.08, shape="square"),
    }


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
    if len(sys.argv) == 3 and sys.argv[1] == "--candidates":
        out = Path(sys.argv[2])
        for name, samples in candidates().items():
            write(name, samples, out)
        print("wrote", len(candidates()), "candidates to", out)
        sys.exit(0)
    write("card", swoosh())
    write("hit", thump())
    write("heal", chime([523.25, 659.25, 783.99], 0.07))
    write("turn", chime([880.0, 1318.5], 0.09, decay=0.12))
    write("eliminate", tone(lambda t: 440 * math.exp(-t * 2.2), 0.7, attack=0.01, decay=0.35, shape="square"))
    write("win", chime([523.25, 659.25, 783.99, 1046.5], 0.11, decay=0.4))
    write("error", tone(lambda t: 160, 0.15, attack=0.003, decay=0.06, shape="square"))
    print("wrote", sorted(p.name for p in OUT.glob("*.wav")))
