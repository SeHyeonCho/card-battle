import { Howl } from 'howler'

/**
 * 배경음악. 화면마다 곡을 정해 반복 재생하고, 곡이 바뀌면 서서히 넘긴다.
 * 곡은 public/bgm/ 에 있고 출처는 README 와 첫 화면에 적는다 (Kevin MacLeod, CC BY 4.0).
 * 브라우저는 사용자가 한 번 누르기 전까지 소리를 막으므로, 클릭할 때마다 resume()으로 멈춰 있던 곡을 이어 튼다.
 */

export type BgmName = 'lobby'

const TRACKS: Record<BgmName, string> = {
  lobby: '/bgm/lobby.mp3', // "Fluffing a Duck" Kevin MacLeod (incompetech.com), CC BY 4.0
}
const VOLUME = 0.35
const FADE_MS = 600
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

function howlFor(name: BgmName): Howl {
  let howl = howls.get(name)
  if (!howl) {
    howl = new Howl({ src: [TRACKS[name]], loop: true, volume: 0 })
    howls.set(name, howl)
  }
  return howl
}

function fadeOut(howl: Howl) {
  if (!howl.playing()) return
  howl.fade(howl.volume(), 0, FADE_MS)
  howl.once('fade', () => {
    if (howl.volume() === 0) howl.pause()
  })
}

function start(name: BgmName) {
  const howl = howlFor(name)
  if (howl.playing()) {
    howl.fade(howl.volume(), VOLUME, FADE_MS) // 꺼지던 중이면 다시 올린다
    return
  }
  howl.volume(0)
  howl.play()
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
  enabled: () => enabled,
  setEnabled(value: boolean) {
    enabled = value
    try {
      localStorage.setItem(ENABLED_KEY, value ? '1' : '0')
    } catch {
      // 저장이 막힌 브라우저에서는 이번 접속 동안만 유지
    }
    if (value) bgm.resume()
    else howls.forEach(fadeOut)
  },
}
