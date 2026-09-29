import { create } from 'zustand'
import { sfx } from '../audio/sfx'
import { fromSnapshot, reduce, type Fx, type GameView } from '../game/reduce'
import { send, subscribeGame, subscribeRoom, unsubscribeGame, type RoomMessage } from '../net/connection'
import type { GameSnapshot, Room, ServerMessage, Session } from '../types'
import { readStorage, writeStorage } from '../util'

type Screen = 'home' | 'room' | 'game'

interface LogEntry {
  id: number
  text: string
}

interface Toast {
  id: number
  text: string
}

export interface Floater {
  id: number
  playerId: string
  delta: number
}

interface AppState {
  screen: Screen
  session: Session | null
  room: Room | null
  game: GameView | null
  connected: boolean
  /** ?demo 로 연 화면 미리보기 모드 (서버 없이 동작) */
  demo: boolean
  log: LogEntry[]
  toasts: Toast[]
  floaters: Floater[]
  /** playerId → 흔들기 카운터 (값이 바뀔 때마다 좌석이 흔들린다) */
  shakes: Record<string, number>

  setSession: (session: Session | null) => void
  enterRoom: (room: Room) => void
  handleRoomMessage: (message: RoomMessage) => void
  handleGameMessage: (message: ServerMessage) => void
  setConnected: (connected: boolean) => void
  toast: (text: string) => void
  backToRoom: () => void
  startDemo: (snapshot: GameSnapshot) => void
}

const SESSION_KEY = 'session'
export const INVITE_KEY = 'last-invite'

let nextId = 1

export const useApp = create<AppState>((set, get) => {
  function runFx(fx: Fx[]) {
    for (const f of fx) {
      switch (f.kind) {
        case 'sound':
          sfx.play(f.name)
          break
        case 'log':
          set((s) => ({ log: [...s.log.slice(-49), { id: nextId++, text: f.text }] }))
          break
        case 'toast':
          get().toast(f.text)
          break
        case 'floater': {
          const id = nextId++
          set((s) => ({ floaters: [...s.floaters, { id, playerId: f.playerId, delta: f.delta }] }))
          setTimeout(() => set((s) => ({ floaters: s.floaters.filter((x) => x.id !== id) })), 1200)
          break
        }
        case 'shake':
          set((s) => ({ shakes: { ...s.shakes, [f.playerId]: (s.shakes[f.playerId] ?? 0) + 1 } }))
          break
        case 'resync': {
          const game = get().game
          if (game && !get().demo) {
            send(`/app/games/${game.gameId}/sync`, {})
          }
          break
        }
      }
    }
  }

  return {
    screen: 'home',
    session: readStorage<Session>(SESSION_KEY),
    room: null,
    game: null,
    connected: false,
    demo: false,
    log: [],
    toasts: [],
    floaters: [],
    shakes: {},

    setSession: (session) => {
      writeStorage(SESSION_KEY, session)
      set({ session })
    },

    enterRoom: (room) => {
      writeStorage(INVITE_KEY, room.inviteCode)
      set({ room, screen: 'room' })
      subscribeRoom(room.roomId)
      if (room.status === 'IN_GAME' && room.gameId) {
        subscribeGame(room.gameId) // 게임 중에 새로고침했으면 바로 게임으로 복귀
      }
    },

    handleRoomMessage: (message) => {
      if (message.type !== 'ROOM_UPDATED' || !message.room) {
        return
      }
      const room = message.room
      const { game } = get()
      set({ room })
      if (room.status === 'IN_GAME' && room.gameId && game?.gameId !== room.gameId) {
        set({ game: null, log: [], floaters: [], shakes: {} })
        subscribeGame(room.gameId)
      }
    },

    handleGameMessage: (message) => {
      if (message.type === 'SNAPSHOT') {
        set({ game: fromSnapshot(message.payload as unknown as GameSnapshot), screen: 'game' })
        return
      }
      const game = get().game
      if (!game) {
        return // 스냅샷을 받기 전 이벤트는 스냅샷에 포함돼 있다
      }
      const { view, fx } = reduce(game, message)
      set({ game: view })
      runFx(fx)
    },

    setConnected: (connected) => set({ connected }),

    toast: (text) => {
      const id = nextId++
      set((s) => ({ toasts: [...s.toasts, { id, text }] }))
      setTimeout(() => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })), 3000)
    },

    backToRoom: () => {
      unsubscribeGame()
      set({ screen: 'room', game: null, log: [] })
    },

    startDemo: (snapshot) => set({ demo: true, game: fromSnapshot(snapshot), screen: 'game' }),
  }
})
