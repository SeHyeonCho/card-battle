import { Howl, Howler } from 'howler'
import { bgm } from './bgm'

/**
 * 효과음 (PRD FR-UI-08). 기본 소리는 public/sfx/ 에 있고 tools/gen_sfx.py 로 만든 자체 제작 사운드다.
 * 카드팩에 카드별 소리가 있으면(원작 팩, 로컬 전용) 서버가 주소를 알려 주고 playUrl로 재생한다.
 * 브라우저는 사용자가 한 번 클릭하기 전까지 소리를 막는데, Howler가 첫 클릭 때 알아서 풀어준다.
 */

export type SfxName = 'card' | 'hit' | 'heal' | 'turn' | 'eliminate' | 'win' | 'error' | 'click'

const NAMES: SfxName[] = ['card', 'hit', 'heal', 'turn', 'eliminate', 'win', 'error', 'click']
/** 효과음 전체 음량 배율. 모든 효과음(기본·카드 고유)에 곱한다 */
const MASTER = 0.5
/** 효과음별 상대 음량 (없으면 0.7) */
const VOLUME: Partial<Record<SfxName, number>> = { card: 0.5, click: 0.4 }
const MUTE_KEY = 'sfx-muted'

const sounds = new Map<SfxName, Howl>(
  NAMES.map((name) => [name, new Howl({ src: [`/sfx/${name}.wav`], volume: (VOLUME[name] ?? 0.7) * MASTER, preload: true })]),
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
 * 카드 고유 소리가 나는 동안 덮지 않고 건너뛰는 기본 소리. 카드를 내면 서버가 체력 변화를 곧바로 이어 보내서
 * 맞음·회복 소리가 카드 소리와 겹친다. 탈락·승리·오류는 중요한 알림이라 그대로 낸다
 */
const YIELD_TO_CARD: ReadonlySet<SfxName> = new Set(['card', 'hit', 'heal'])
/**
 * 카드 소리가 끝날 때까지 미뤘다가 내는 기본 소리. '내 차례'는 버리면 차례가 온 줄 모르므로 미루기만 한다.
 * 카드 소리는 대부분 1.5초보다 길어서(중간값 약 1.9초), 턴 시간을 잡아먹지 않게 차례 전환 시간만큼만 기다린다
 */
const WAIT_FOR_CARD: ReadonlySet<SfxName> = new Set(['turn'])
const WAIT_FOR_CARD_MAX_MS = 1500
/** 처음 재생하는 카드 소리는 불러오는 동안 playing()이 거짓이라, 시작 직후 잠깐은 재생 중으로 본다 */
const CARD_START_GRACE_MS = 600
let cardSound: { howl: Howl; startedAt: number } | null = null

function cardSoundPlaying(): boolean {
  if (!cardSound) return false
  return cardSound.howl.playing() || Date.now() - cardSound.startedAt < CARD_START_GRACE_MS
}

/** 카드 소리가 끝나거나 WAIT_FOR_CARD_MAX_MS 가 지나면(먼저 오는 쪽) 한 번만 부른다 */
function afterCardSound(howl: Howl, action: () => void) {
  let done = false
  const fire = () => {
    if (done) return
    done = true
    clearTimeout(timer)
    howl.off('end', fire)
    howl.off('stop', fire)
    action()
  }
  const timer = setTimeout(fire, WAIT_FOR_CARD_MAX_MS)
  howl.once('end', fire)
  howl.once('stop', fire)
}

/** 카드 소리가 이보다 길어도 배경음악은 이만큼 뒤에 다시 튼다 (가장 긴 카드 소리 약 6.7초) */
const CARD_DUCK_MAX_MS = 8000

let muted = readMuted()
Howler.mute(muted)

function howlFor(url: string): Howl {
  let howl = byUrl.get(url)
  if (!howl) {
    howl = new Howl({ src: [url], format: ['mp3'], volume: 0.7 * MASTER })
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
    const start = () => (override ? howlFor(override).play() : sounds.get(name)?.play())
    if (cardSoundPlaying()) {
      if (YIELD_TO_CARD.has(name)) return
      if (WAIT_FOR_CARD.has(name) && cardSound) {
        afterCardSound(cardSound.howl, start)
        return
      }
    }
    start()
  },
  /** 카드 고유 소리. 나는 동안 배경음악은 멈췄다가 끝나면 이어서 튼다 */
  playUrl(url: string) {
    const howl = howlFor(url)
    const id = howl.play()
    cardSound = { howl, startedAt: Date.now() }
    bgm.duck()
    let done = false
    const release = () => {
      if (done) return
      done = true
      clearTimeout(safety)
      howl.off('end', release, id)
      howl.off('stop', release, id)
      howl.off('loaderror', release)
      howl.off('playerror', release, id)
      bgm.unduck()
    }
    // 끝 신호를 못 받는 경우(불러오기 실패 등)에도 배경음악이 영영 멈춰 있지 않게
    const safety = setTimeout(release, CARD_DUCK_MAX_MS)
    howl.once('end', release, id)
    howl.once('stop', release, id)
    howl.once('loaderror', release)
    howl.once('playerror', release, id)
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
