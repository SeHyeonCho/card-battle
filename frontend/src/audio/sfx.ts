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

/**
 * 카드 고유 소리가 나는 동안 덮지 않는 기본 소리. 카드를 내면 서버가 체력 변화·차례 넘김을 곧바로 이어 보내서
 * 맞음·회복·차례 소리가 카드 소리와 겹친다. 탈락·승리·오류는 중요한 알림이라 그대로 낸다
 */
const YIELD_TO_CARD: ReadonlySet<SfxName> = new Set(['card', 'hit', 'heal', 'turn'])
/** 처음 재생하는 카드 소리는 불러오는 동안 playing()이 거짓이라, 시작 직후 잠깐은 재생 중으로 본다 */
const CARD_START_GRACE_MS = 600
let cardSound: { howl: Howl; startedAt: number } | null = null

function cardSoundPlaying(): boolean {
  if (!cardSound) return false
  return cardSound.howl.playing() || Date.now() - cardSound.startedAt < CARD_START_GRACE_MS
}

let muted = readMuted()
Howler.mute(muted)

function howlFor(url: string): Howl {
  let howl = byUrl.get(url)
  if (!howl) {
    howl = new Howl({ src: [url], format: ['mp3'], volume: 0.7 })
    byUrl.set(url, howl)
  }
  return howl
}

export const sfx = {
  /**
   * 기본 효과음. override 가 있으면(카드팩이 기본 소리를 덮어씀, 대응표 키 "@<이름>") 그 소리를 낸다.
   * 카드 고유 소리가 나는 동안에는 겹치지 않게 건너뛴다
   */
  play(name: SfxName, override?: string) {
    if (YIELD_TO_CARD.has(name) && cardSoundPlaying()) return
    if (override) {
      howlFor(override).play()
    } else {
      sounds.get(name)?.play()
    }
  },
  /** 카드 고유 소리 */
  playUrl(url: string) {
    const howl = howlFor(url)
    howl.play()
    cardSound = { howl, startedAt: Date.now() }
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
