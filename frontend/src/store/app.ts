import { create } from 'zustand'
import { api } from '../api/http'
import { sfx } from '../audio/sfx'
import { fromSnapshot, logFromHistory, reduce, type Fx, type GameView } from '../game/reduce'
import { send, subscribeGame, subscribeRoom, unsubscribeGame, unsubscribeRoom, type RoomMessage } from '../net/connection'
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
  /** 카드 ID → 효과음 주소 (카드팩에 카드별 소리가 있을 때) */
  cardSounds: Record<string, string>
  cardSoundsPack: string | null

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
/** 게임 로그에 남겨 두는 줄 수 */
const LOG_LIMIT = 50

let nextId = 1

export const useApp = create<AppState>((set, get) => {
  /** 카드팩의 카드별 효과음 주소를 한 번 받아 둔다. 없거나 실패하면 기본 소리를 쓴다 */
  function loadCardSounds(packCode: string) {
    if (get().cardSoundsPack === packCode) return
    set({ cardSounds: {}, cardSoundsPack: packCode })
    api
      .cardSounds(packCode)
      .then((cardSounds) => {
        if (get().cardSoundsPack === packCode) set({ cardSounds })
      })
      .catch(() => {
        // 효과음은 없어도 게임은 된다
      })
  }

  function runFx(fx: Fx[]) {
    for (const f of fx) {
      switch (f.kind) {
        case 'sound':
          sfx.play(f.name)
          break
        case 'cardSound': {
          const url = get().cardSounds[f.cardId]
          if (url) {
            sfx.playUrl(url)
          } else {
            sfx.play('card')
          }
          break
        }
        case 'log':
          set((s) => ({ log: [...s.log.slice(-(LOG_LIMIT - 1)), { id: nextId++, text: f.text }] }))
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
    cardSounds: {},
    cardSoundsPack: null,

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
      const { game, session } = get()
      if (session && !room.members.some((m) => m.playerId === session.playerId)) {
        // 방장이 강퇴함 → 처음 화면으로
        unsubscribeGame()
        unsubscribeRoom()
        writeStorage(INVITE_KEY, null)
        set({ screen: 'home', room: null, game: null, log: [] })
        get().toast('방장이 자리 비움으로 강퇴했습니다')
        return
      }
      set({ room })
      if (room.status === 'IN_GAME' && room.gameId && game?.gameId !== room.gameId) {
        set({ game: null, log: [], floaters: [], shakes: {} })
        subscribeGame(room.gameId)
      }
    },

    handleGameMessage: (message) => {
      if (message.type === 'SNAPSHOT') {
        const snapshot = message.payload as unknown as GameSnapshot
        const game = fromSnapshot(snapshot)
        // 새로고침·재동기화: 서버가 담아 준 최근 이벤트로 게임 로그를 다시 채운다
        const log = logFromHistory(game, snapshot.recentEvents ?? [])
          .slice(-LOG_LIMIT)
          .map((text) => ({ id: nextId++, text }))
        set({ game, log, screen: 'game' })
        loadCardSounds(snapshot.packCode)
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
