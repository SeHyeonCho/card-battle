import { Howl, Howler } from 'howler'

/**
 * 효과음 (PRD FR-UI-08). 기본 소리는 public/sfx/ 에 있고 tools/gen_sfx.py 로 만든 자체 제작 사운드다.
 * 카드팩에 카드별 소리가 있으면(원작 팩, 로컬 전용) 서버가 주소를 알려 주고 playUrl로 재생한다.
 * 브라우저는 사용자가 한 번 클릭하기 전까지 소리를 막는데, Howler가 첫 클릭 때 알아서 풀어준다.
 */

export type SfxName = 'card' | 'hit' | 'heal' | 'turn' | 'eliminate' | 'win' | 'error'

const NAMES: SfxName[] = ['card', 'hit', 'heal', 'turn', 'eliminate', 'win', 'error']
const MUTE_KEY = 'sfx-muted'

const sounds = new Map<SfxName, Howl>(
  NAMES.map((name) => [name, new Howl({ src: [`/sfx/${name}.wav`], volume: name === 'card' ? 0.5 : 0.7, preload: true })]),
)

function readMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === '1'
  } catch {
    return false
  }
}

/** 카드별 소리: 주소 → Howl (처음 재생할 때 만든다) */
const byUrl = new Map<string, Howl>()

let muted = readMuted()
Howler.mute(muted)

export const sfx = {
  play(name: SfxName) {
    sounds.get(name)?.play()
  },
  playUrl(url: string) {
    let howl = byUrl.get(url)
    if (!howl) {
      howl = new Howl({ src: [url], format: ['mp3'], volume: 0.7 })
      byUrl.set(url, howl)
    }
    howl.play()
  },
  muted: () => muted,
  setMuted(value: boolean) {
    muted = value
    Howler.mute(value)
    try {
      localStorage.setItem(MUTE_KEY, value ? '1' : '0')
    } catch {
      // 저장이 막힌 브라우저에서는 이번 접속 동안만 유지
    }
  },
}
