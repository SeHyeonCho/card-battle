import { Client, ReconnectionTimeMode, type StompSubscription } from '@stomp/stompjs'
import type { Room, ServerMessage } from '../types'

/**
 * Spring 서버와의 STOMP 연결 하나를 관리한다.
 * - 끊기면 지수 백오프로 재접속한다 (1초 → 2초 → 4초 … 최대 30초, PRD FR-SYNC-03)
 * - 재접속하면 구독을 다시 만들고 onReconnect를 불러 스냅샷을 다시 받게 한다
 */

export interface RoomMessage {
  type: string
  room?: Room
}

interface Subscriptions {
  room?: { roomId: string; sub?: StompSubscription }
  game?: { gameId: string; subs: StompSubscription[] }
}

let client: Client | null = null
const subs: Subscriptions = {}
let handlers: {
  onRoom: (message: RoomMessage) => void
  onGame: (message: ServerMessage) => void
  onError: (message: { code: string; message: string }) => void
  onStatus: (connected: boolean) => void
} | null = null

function wsUrl() {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${location.host}/ws`
}

function parse<T>(body: string): T {
  return JSON.parse(body) as T
}

export function connect(token: string, h: NonNullable<typeof handlers>) {
  handlers = h
  if (client?.active) {
    return
  }
  client = new Client({
    brokerURL: wsUrl(),
    connectHeaders: { 'X-Session-Token': token },
    reconnectDelay: 1000,
    maxReconnectDelay: 30_000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    onConnect: () => {
      handlers?.onStatus(true)
      client?.subscribe('/user/queue/errors', (m) => handlers?.onError(JSON.parse(m.body)))
      if (subs.room) {
        subscribeRoom(subs.room.roomId)
      }
      if (subs.game) {
        subscribeGame(subs.game.gameId)
      }
    },
    onWebSocketClose: () => handlers?.onStatus(false),
    // 서버가 연결을 거부함 (대개 세션 만료) → 화면에서 닉네임을 다시 받게 한다
    onStompError: (frame) => handlers?.onError({ code: 'STOMP_ERROR', message: frame.headers.message ?? '' }),
  })
  client.activate()
}

export function subscribeRoom(roomId: string) {
  subs.room?.sub?.unsubscribe()
  subs.room = { roomId }
  if (client?.connected) {
    subs.room.sub = client.subscribe(`/topic/rooms/${roomId}`, (m) => handlers?.onRoom(parse<RoomMessage>(m.body)))
  }
}

export function unsubscribeRoom() {
  subs.room?.sub?.unsubscribe()
  subs.room = undefined
}

/** 게임 구독 후 바로 스냅샷을 요청한다 (새로고침·재접속 복구) */
export function subscribeGame(gameId: string) {
  subs.game?.subs.forEach((s) => s.unsubscribe())
  subs.game = { gameId, subs: [] }
  if (client?.connected) {
    subs.game.subs = [
      client.subscribe(`/topic/games/${gameId}`, (m) => handlers?.onGame(parse<ServerMessage>(m.body))),
      client.subscribe(`/user/queue/games/${gameId}`, (m) => handlers?.onGame(parse<ServerMessage>(m.body))),
    ]
    send(`/app/games/${gameId}/sync`, {})
  }
}

export function unsubscribeGame() {
  subs.game?.subs.forEach((s) => s.unsubscribe())
  subs.game = undefined
}

export function send(destination: string, body: unknown) {
  if (!client?.connected) {
    console.warn('not connected, dropped', destination)
    return
  }
  client.publish({ destination, body: JSON.stringify(body) })
}

export function disconnect() {
  void client?.deactivate()
  client = null
  subs.room = undefined
  subs.game = undefined
}
