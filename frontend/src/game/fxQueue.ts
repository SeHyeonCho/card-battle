import type { Fx } from './reduce'

/**
 * 이벤트 연출 큐 (PRD FR-UI-09).
 * 서버 이벤트 하나가 만든 연출 묶음(소리·떠오르는 숫자·흔들기·로그·차례 배너)을 한 "장면"으로 보고,
 * 받은 순서대로 한 장면씩 재생한다. 화면의 숫자(체력·누적)는 이벤트를 받자마자 서버 상태로 맞추고,
 * 여기서는 연출만 늦게 따라간다. 밀린 장면이 많으면 짧게 줄이고, 더 많으면 로그만 남기고 건너뛴다.
 */

/** 큐를 거치지 않고 바로 하는 것 (알림·재동기화는 늦으면 안 된다) */
const IMMEDIATE: ReadonlySet<Fx['kind']> = new Set(['toast', 'resync'])

/** 뒤에 이만큼 밀려 있으면 장면을 반으로 줄인다 */
const HURRY_BACKLOG = 3
/** 뒤에 이만큼 밀려 있으면 로그만 남기고 바로 넘긴다 */
const SKIP_BACKLOG = 8

/** 배너는 전환 시간 동안 보여 주되, 너무 짧거나 길지 않게 */
const BANNER_MIN_MS = 700
const BANNER_MAX_MS = 2000

/** 장면 길이: 그 장면에서 가장 긴 연출에 맞춘다 */
function sceneMs(scene: Fx[], now: number): number {
  let ms = 0
  for (const f of scene) {
    switch (f.kind) {
      case 'cardSound':
        ms = Math.max(ms, 550)
        break
      case 'sound':
        ms = Math.max(ms, f.name === 'eliminate' ? 900 : f.name === 'hit' ? 380 : f.name === 'heal' ? 300 : 0)
        break
      case 'turnBanner':
        ms = Math.max(ms, Math.min(BANNER_MAX_MS, Math.max(BANNER_MIN_MS, f.until - now)))
        break
      case 'log':
        ms = Math.max(ms, 160)
        break
      default:
        break
    }
  }
  return ms
}

export interface FxPlayer {
  /** 연출 하나를 실제로 한다. 반환값이 있으면 장면이 끝날 때 부른다 (배너 내리기 등) */
  run: (fx: Fx) => (() => void) | void
}

export function createFxQueue(player: FxPlayer) {
  const scenes: Fx[][] = []
  let timer: ReturnType<typeof setTimeout> | null = null
  let finishScene: (() => void)[] = []

  function endScene() {
    timer = null
    finishScene.forEach((f) => f())
    finishScene = []
    next()
  }

  function next() {
    if (timer !== null) return
    const scene = scenes.shift()
    if (!scene) return
    const backlog = scenes.length
    if (backlog >= SKIP_BACKLOG) {
      // 많이 밀림: 로그만 남기고 소리·숫자·배너는 건너뛴다 (숫자는 이미 서버 상태로 맞춰져 있다)
      scene.filter((f) => f.kind === 'log').forEach((f) => player.run(f))
      next()
      return
    }
    for (const f of scene) {
      const done = player.run(f)
      if (done) finishScene.push(done)
    }
    const ms = sceneMs(scene, Date.now()) * (backlog >= HURRY_BACKLOG ? 0.5 : 1)
    timer = setTimeout(endScene, ms)
  }

  return {
    /** 이벤트 하나의 연출을 넣는다. 알림·재동기화는 바로 하고 나머지는 한 장면으로 줄 세운다 */
    push(fx: Fx[]) {
      fx.filter((f) => IMMEDIATE.has(f.kind)).forEach((f) => player.run(f))
      const scene = fx.filter((f) => !IMMEDIATE.has(f.kind))
      if (scene.length === 0) return
      scenes.push(scene)
      next()
    },
    /** 새로고침·게임 전환: 밀린 연출을 버린다 */
    clear() {
      scenes.length = 0
      if (timer !== null) clearTimeout(timer)
      timer = null
      finishScene.forEach((f) => f())
      finishScene = []
    },
    /** 테스트·디버그용 */
    size: () => scenes.length,
  }
}
