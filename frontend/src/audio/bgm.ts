import { Howl } from 'howler'

/**
 * 배경음악. 화면마다 곡을 정해 반복 재생하고, 곡이 바뀌면 서서히 넘긴다.
 * 곡은 public/bgm/ 에 있고 출처는 README 와 첫 화면에 적는다 (Kevin MacLeod, CC BY 4.0).
 * 브라우저는 사용자가 한 번 누르기 전까지 소리를 막으므로, 클릭할 때마다 resume()으로 멈춰 있던 곡을 이어 튼다.
 */

export type BgmName = 'lobby'

const TRACKS: Record<BgmName, string> = {
  lobby: '/bgm/lobby.mp3', // "Fluffing a Duck" Kevin MacLeod (incompetech.com), CC BY 4.0. 지금은 게임 중에도 이 곡
}
const VOLUME = 0.35
const FADE_MS = 600
/** 카드 소리가 날 때 배경음악을 줄이는 시간 (빨리 비켜 준다) */
const DUCK_MS = 150
const ENABLED_KEY = 'bgm-enabled'

function readEnabled(): boolean {
  try {
    return localStorage.getItem(ENABLED_KEY) !== '0'
  } catch {
    return true
  }
}

const howls = new Map<BgmName, Howl>()
let wanted: BgmName | null = null
let enabled = readEnabled()
/** 지금 나고 있는 카드 소리 수. 0보다 크면 배경음악을 멈춰 둔다 */
let ducks = 0

function howlFor(name: BgmName): Howl {
  let howl = howls.get(name)
  if (!howl) {
    howl = new Howl({ src: [TRACKS[name]], loop: true, volume: 0 })
    howls.set(name, howl)
  }
  return howl
}

function fadeOut(howl: Howl, ms = FADE_MS) {
  if (!howl.playing()) return
  howl.fade(howl.volume(), 0, ms)
  howl.once('fade', () => {
    if (howl.volume() === 0) howl.pause()
  })
}

function start(name: BgmName) {
  if (ducks > 0) return // 카드 소리가 끝나면 unduck()이 다시 튼다
  const howl = howlFor(name)
  if (howl.playing()) {
    howl.fade(howl.volume(), VOLUME, FADE_MS) // 꺼지던 중이면 다시 올린다
    return
  }
  howl.volume(0)
  howl.play() // 멈춰 둔 곡이면 멈춘 자리부터 이어진다
  howl.fade(0, VOLUME, FADE_MS)
}

export const bgm = {
  /** 지금 화면에 맞는 곡. null 이면 끈다 */
  play(name: BgmName | null) {
    wanted = name
    howls.forEach((howl, n) => n !== name && fadeOut(howl))
    if (name && enabled) start(name)
  },
  /** 소리가 막혀 있다가 풀렸을 때 등, 틀어야 하는데 멈춰 있으면 다시 튼다 */
  resume() {
    if (wanted && enabled) start(wanted)
  },
  /** 카드 고유 소리가 시작될 때: 배경음악을 빨리 줄이고 멈춘다 */
  duck() {
    ducks++
    howls.forEach((howl) => fadeOut(howl, DUCK_MS))
  },
  /** 카드 고유 소리가 끝날 때: 남은 카드 소리가 없으면 멈춘 자리부터 다시 튼다 */
  unduck() {
    ducks = Math.max(0, ducks - 1)
    if (ducks === 0) bgm.resume()
  },
  enabled: () => enabled,
  setEnabled(value: boolean) {
    enabled = value
    try {
      localStorage.setItem(ENABLED_KEY, value ? '1' : '0')
    } catch {
      // 저장이 막힌 브라우저에서는 이번 접속 동안만 유지
    }
    if (value) bgm.resume()
    else howls.forEach((howl) => fadeOut(howl))
  },
}
