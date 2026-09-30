"""
배경음악 생성기. 외부 음원 없이 8비트(칩튠) 루프를 직접 합성한다 — 저작권 걱정 없는 자체 제작 음악.
numpy 와 ffmpeg 가 필요하다.

실행: python3 tools/gen_bgm.py <폴더>          # 후보 곡 전부를 <폴더>/<이름>.mp3 로
      python3 tools/gen_bgm.py <폴더> <이름>   # 한 곡만

곡은 처음과 끝이 이어지도록(루프) 만든다. 끝에서 넘친 음의 꼬리는 처음에 겹쳐 넣는다.
"""
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

RATE = 32000
NOTE_INDEX = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6, "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}


def freq(note):
    """'A4' → 440Hz, 'R' → 쉼표(None)"""
    if note == "R":
        return None
    name, octave = note[:-1], int(note[-1])
    return 440.0 * 2 ** ((NOTE_INDEX[name] + (octave - 4) * 12 - 9) / 12)


class Song:
    def __init__(self, bpm, beats):
        self.spb = 60.0 / bpm  # 박당 초
        self.length = int(beats * self.spb * RATE)
        self.buf = np.zeros(self.length + RATE * 2)  # 꼬리 여유

    def add(self, samples, beat):
        start = int(beat * self.spb * RATE)
        self.buf[start:start + len(samples)] += samples

    def tone(self, note, beat, beats, wave="square", vol=0.2, duty=0.5, release=0.06, slide=0.0):
        f = freq(note)
        if f is None:
            return
        n = int(beats * self.spb * RATE)
        t = np.arange(n + int(release * RATE)) / RATE
        f_t = f * (1 + slide * t / max(t[-1], 1e-9))  # slide: 끝으로 갈수록 음이 오르내림 (병맛용)
        phase = np.cumsum(2 * np.pi * f_t / RATE)
        cyc = (phase / (2 * np.pi)) % 1.0
        if wave == "square":
            s = np.where(cyc < duty, 1.0, -1.0)
        elif wave == "triangle":
            s = 4 * np.abs(cyc - 0.5) - 1
        else:
            s = np.sin(phase)
        env = np.ones_like(t)
        a = min(len(t), int(0.004 * RATE))
        env[:a] = np.linspace(0, 1, a)
        env[n:] = np.linspace(1, 0, len(t) - n)
        self.add(s * env * vol, beat)

    def noise(self, beat, length, vol=0.15, decay=0.05, seed=0, hi=True):
        rng = np.random.default_rng(seed)
        n = int(length * RATE)
        s = rng.uniform(-1, 1, n)
        if not hi:  # 낮은 잡음: 샘플을 붙들어 거칠게
            s = np.repeat(s[::8], 8)[:n]
        t = np.arange(n) / RATE
        self.add(s * np.exp(-t / decay) * vol, beat)

    def kick(self, beat, vol=0.5):
        t = np.arange(int(0.12 * RATE)) / RATE
        f = 120 * np.exp(-t * 30) + 40
        self.add(np.sin(np.cumsum(2 * np.pi * f / RATE)) * np.exp(-t * 25) * vol, beat)

    def seq(self, notes, start, step, **kw):
        """notes: 'C5 E5 G5 R ...' 공백 구분, 음 뒤 ':2' 면 step 의 2배 길이"""
        beat = start
        for tok in notes.split():
            name, _, mul = tok.partition(":")
            beats = step * (float(mul) if mul else 1)
            self.tone(name, beat, beats * 0.92, **kw)
            beat += beats
        return beat

    def render(self):
        out = self.buf[:self.length].copy()
        tail = self.buf[self.length:]
        out[:len(tail)] += tail[:self.length]  # 루프 이음새: 넘친 꼬리를 처음에 겹친다
        return out / max(1e-9, np.max(np.abs(out))) * 0.8


def drums(song, bars, kick_beats=(0, 2), snare_beats=(1, 3), hats=8):
    for bar in range(bars):
        b0 = bar * 4
        for k in kick_beats:
            song.kick(b0 + k)
        for sn in snare_beats:
            song.noise(b0 + sn, 0.15, vol=0.18, decay=0.06, seed=bar * 7 + sn, hi=False)
        for h in range(hats):
            song.noise(b0 + h * 4 / hats, 0.04, vol=0.05, decay=0.012, seed=100 + bar * 16 + h)


def adventure():
    """8비트 모험: C장조, 경쾌하게 (C–G–Am–F 두 바퀴 + 변주)"""
    s = Song(bpm=140, beats=64)
    chords = ["C", "G", "A", "F"] * 4
    roots = {"C": ("C3", "G3"), "G": ("G2", "D3"), "A": ("A2", "E3"), "F": ("F2", "C3")}
    for i, c in enumerate(chords):
        lo, hi = roots[c]
        s.seq(f"{lo} {lo} {hi} {lo} {lo} {hi} {lo} {hi}", i * 4, 0.5, wave="triangle", vol=0.35)
    melody = (
        "E5 G5 C6:2 B5 G5 A5:2 "      # C
        "G5 D5 G5:2 F5 E5 D5:2 "      # G
        "C5 E5 A5:2 G5 E5 C5:2 "      # Am
        "D5 F5 A5 C6 B5:2 G5:2 "      # F
        "E5 G5 C6:2 D6 C6 B5:2 "
        "G5 B5 D6:2 C6 B5 A5:2 "
        "A5 C6 E6:2 D6 C6 A5:2 "
        "F5 A5 G5 F5 E5:2 D5:2 "
    )
    end = s.seq(melody, 0, 0.5, wave="square", duty=0.25, vol=0.16)
    s.seq(melody, end, 0.5, wave="square", duty=0.5, vol=0.14)  # 두 번째 바퀴는 음색만 바꿔서
    for i, c in enumerate(chords[8:]):  # 두 번째 바퀴: 아르페지오 반주 추가
        lo = roots[c][0][:-1]
        arp = {"C": "C4 E4 G4 E4", "G": "G3 B3 D4 B3", "A": "A3 C4 E4 C4", "F": "F3 A3 C4 A3"}[c]
        s.seq(arp + " " + arp, 32 + i * 4, 0.5, wave="square", duty=0.125, vol=0.06)
    drums(s, 16)
    return s


def tension():
    """긴장감 배틀: A단조, 16분 아르페지오 + 낮게 깔리는 베이스 (Am–F–G–E)"""
    s = Song(bpm=128, beats=64)
    prog = [("A", "A3 C4 E4 A4"), ("F", "F3 A3 C4 F4"), ("G", "G3 B3 D4 G4"), ("E", "E3 G#3 B3 E4")] * 4
    for i, (root, arp) in enumerate(prog):
        notes = arp.split()
        back = notes[::-1][1:3]
        pattern = " ".join(notes + back + notes + back + notes[:2] + back)  # 16분음표 16개 = 한 마디
        s.seq(pattern, i * 4, 0.25, wave="square", duty=0.25, vol=0.09)
        s.seq(f"{root}2:2 {root}2 {root}2 {root}2:2 {root}3 {root}2", i * 4, 0.5, wave="triangle", vol=0.4)
    lead = ("A5:4 C6:2 B5:2 A5:4 G5:2 E5:2 F5:4 E5:2 D5:2 E5:6 R:2 "
            "A5:2 B5:2 C6:4 D6:2 C6:2 B5:4 G5:2 B5:2 C6:2 B5:2 A5:4 G#5:4 ")
    s.seq(lead, 32, 0.5, wave="square", duty=0.5, vol=0.13)
    drums(s, 16, kick_beats=(0, 1.5, 2), snare_beats=(1, 3), hats=16)
    return s


def silly_march():
    """병맛 행진곡: F장조 쿵짝쿵짝 폴카, 음이 미끄러지는 우스꽝스러운 멜로디"""
    s = Song(bpm=160, beats=64)
    prog = ["F", "C", "F", "C", "B", "F", "C", "F"] * 2
    bass = {"F": ("F2", "C3"), "C": ("C3", "G2"), "B": ("A#2", "F3")}
    chord = {"F": ["F4", "A4", "C5"], "C": ["E4", "G4", "C5"], "B": ["D4", "F4", "A#4"]}
    for i, c in enumerate(prog):
        b0 = i * 4
        s.tone(bass[c][0], b0, 0.8, wave="triangle", vol=0.45)
        s.tone(bass[c][1], b0 + 2, 0.8, wave="triangle", vol=0.45)
        for off in (1, 3):
            for n in chord[c]:
                s.tone(n, b0 + off, 0.3, wave="square", duty=0.5, vol=0.05)
    melody = ("C5 F5 A5 C6:2 A5 F5:2 G5 E5 G5 C6:2 G5:3 "
              "C5 F5 A5 C6:2 D6 C6:2 A#5 G5 E5 C5:2 F5:3 "
              "A5 A5 A5 A#5:2 A5 G5:2 A5 F5 D5 F5:2 R:3 "
              "C6 A#5 A5 G5:2 A5 A#5:2 C6 G5 E5 G5:2 F5:3 ")
    end = s.seq(melody, 0, 0.5, wave="square", duty=0.25, vol=0.15)
    # 두 번째 바퀴: 한 옥타브 낮게, 음 끝이 살짝 미끄러져서 우스꽝스럽게
    low = " ".join(tok.split(":")[0][:-1] + str(int(tok.split(":")[0][-1]) - 1) + (":" + tok.split(":")[1] if ":" in tok else "")
                   if tok[0] != "R" else tok for tok in melody.split())
    beat = end
    for tok in low.split():
        name, _, mul = tok.partition(":")
        beats = 0.5 * (float(mul) if mul else 1)
        s.tone(name, beat, beats * 0.9, wave="square", duty=0.5, vol=0.15, slide=-0.06 if beats > 0.5 else 0.0)
        beat += beats
    drums(s, 16, kick_beats=(0, 2), snare_beats=(1, 3), hats=4)
    return s


SONGS = {"adventure": adventure, "tension": tension, "silly_march": silly_march}


def save_mp3(samples, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes((samples * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", tmp.name, "-b:a", "96k", str(path)], check=True)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out = Path(sys.argv[1])
    names = sys.argv[2:] or list(SONGS)
    for name in names:
        song = SONGS[name]()
        save_mp3(song.render(), out / f"{name}.mp3")
        print(f"{name}: {song.length / RATE:.1f}초 → {out / (name + '.mp3')}")
