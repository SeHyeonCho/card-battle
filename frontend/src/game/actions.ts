import { createDemo } from '../demo/demoGame'
import { send } from '../net/connection'
import { useApp } from '../store/app'
import type { RoomSettings } from '../types'
import { uid } from '../util'

/** 화면에서 부르는 행동들. 서버(또는 ?demo 모드의 가짜 서버)로 보낸다 */

let demo: ReturnType<typeof createDemo> | null = null

export function startDemo() {
  demo = createDemo((message) => useApp.getState().handleGameMessage(message))
  useApp.getState().startDemo(demo.snapshot)
}

export function playCard(instanceId: string, targetId?: string) {
  const { game, demo: isDemo } = useApp.getState()
  if (!game) return
  if (isDemo) {
    demo?.play(instanceId, targetId)
    return
  }
  send(`/app/games/${game.gameId}/play`, {
    actionId: uid(),
    cardInstanceId: instanceId,
    targetId: targetId ?? null,
    expectedVersion: game.version,
  })
}

export function discardCard(instanceId: string) {
  const { game, demo: isDemo } = useApp.getState()
  if (!game) return
  if (isDemo) {
    demo?.discard(instanceId)
    return
  }
  send(`/app/games/${game.gameId}/discard`, {
    actionId: uid(),
    cardInstanceId: instanceId,
    expectedVersion: game.version,
  })
}

export function setReady(ready: boolean) {
  const room = useApp.getState().room
  if (room) send(`/app/rooms/${room.roomId}/ready`, { ready })
}

export function startGame() {
  const room = useApp.getState().room
  if (room) send(`/app/rooms/${room.roomId}/start`, {})
}

/** 방장: 참가자 강퇴 — 대기실에서는 누구나(FR-ROOM-05), 게임 중에는 자리 비움인 사람만 (PRD 4.3) */
export function kickPlayer(playerId: string) {
  const room = useApp.getState().room
  if (room) send(`/app/rooms/${room.roomId}/kick`, { playerId })
}

/** 방장: 대기실에서 설정 바꾸기 (FR-ROOM-05). 준 값만 바뀐다 */
export function updateRoomSettings(settings: Partial<RoomSettings>) {
  const room = useApp.getState().room
  if (room) send(`/app/rooms/${room.roomId}/settings`, { settings })
}

export function leaveRoom() {
  const room = useApp.getState().room
  if (room) send(`/app/rooms/${room.roomId}/leave`, {})
}
