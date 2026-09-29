import type {
  CardInfo,
  CardInstance,
  CardPlayability,
  FieldCard,
  GameSettings,
  GameSnapshot,
  PlayerView,
  RankingEntry,
  ServerMessage,
} from '../types'
import type { SfxName } from '../audio/sfx'

/**
 * 화면이 쓰는 게임 상태. 서버 스냅샷에서 시작해서 이벤트를 하나씩 반영한다.
 * 규칙 계산은 하지 않는다 — 서버가 보낸 결과를 그대로 옮겨 적기만 한다.
 */
export interface GameView {
  gameId: string
  version: number
  /** 마지막으로 반영한 이벤트 번호. 이보다 작거나 같은 이벤트는 이미 반영된 것이라 버린다 */
  lastSeq: number
  settings: GameSettings
  status: 'IN_PROGRESS' | 'FINISHED'
  turnNumber: number
  currentPlayerId: string
  direction: number
  currentAttack: number
  accumulatedDamage: number
  field: FieldCard[]
  players: PlayerView[]
  viewerId: string
  myHand: CardInstance[]
  playability: Record<string, CardPlayability>
  turnDeadlineEpochMs: number
  winnerIds: string[]
  ranking: RankingEntry[] | null
  draw: boolean
  cards: Record<string, CardInfo>
}

/** 상태 변화와 함께 일어나야 하는 연출 (소리, 떠오르는 숫자, 로그 등) */
export type Fx =
  | { kind: 'sound'; name: SfxName }
  | { kind: 'floater'; playerId: string; delta: number }
  | { kind: 'shake'; playerId: string }
  | { kind: 'log'; text: string }
  | { kind: 'toast'; text: string }
  | { kind: 'resync' }

export function fromSnapshot(s: GameSnapshot): GameView {
  return {
    gameId: s.gameId,
    version: s.version,
    lastSeq: s.lastSeq,
    settings: s.settings,
    status: s.status,
    turnNumber: s.turnNumber,
    currentPlayerId: s.currentPlayerId,
    direction: s.direction,
    currentAttack: s.currentAttack,
    accumulatedDamage: s.accumulatedDamage,
    field: s.field,
    players: s.players,
    viewerId: s.viewerId,
    myHand: s.myHand,
    playability: Object.fromEntries(s.playability.map((p) => [p.instanceId, p])),
    turnDeadlineEpochMs: s.turnDeadlineEpochMs,
    winnerIds: s.winnerIds,
    ranking: null,
    draw: false,
    cards: Object.fromEntries(s.cards.map((c) => [c.id, c])),
  }
}

const str = (v: unknown) => (typeof v === 'string' ? v : '')
const num = (v: unknown) => (typeof v === 'number' ? v : 0)

export function reduce(view: GameView, msg: ServerMessage): { view: GameView; fx: Fx[] } {
  if (msg.seq !== undefined && msg.seq <= view.lastSeq) {
    return { view, fx: [] } // 스냅샷에 이미 들어 있는 이벤트
  }
  const p = msg.payload ?? {}
  const fx: Fx[] = []
  const nick = (id: unknown) => view.players.find((pl) => pl.playerId === id)?.nickname ?? '?'
  const cardName = (id: unknown) => view.cards[str(id)]?.name ?? '?'
  let next: GameView = {
    ...view,
    version: msg.version ? Math.max(view.version, msg.version) : view.version,
    lastSeq: msg.seq ?? view.lastSeq,
  }

  switch (msg.type) {
    case 'TURN_STARTED': {
      const playerId = str(p.playerId)
      next = {
        ...next,
        currentPlayerId: playerId,
        turnNumber: num(p.turnNumber),
        turnDeadlineEpochMs: num(p.deadlineEpochMs),
        playability: playerId === view.viewerId ? next.playability : {},
      }
      if (playerId === view.viewerId) {
        fx.push({ kind: 'sound', name: 'turn' })
      }
      break
    }
    case 'CARD_PLAYED': {
      const instanceId = str(p.instanceId)
      // 필드를 바로 바꿔서, 내 손패의 같은 카드가 필드로 "날아가는" 애니메이션이 한 번에 일어나게 한다
      next = {
        ...next,
        myHand: next.myHand.filter((c) => c.instanceId !== instanceId),
        field: [{ instanceId, cardId: str(p.cardId), ownerId: str(p.playerId), attack: num(p.attack), playedTurn: next.turnNumber }],
        playability: {},
      }
      const attack = typeof p.attack === 'number' ? ` (공격력 ${p.attack})` : ''
      const target = p.targetId ? ` → ${nick(p.targetId)}` : ''
      fx.push({ kind: 'sound', name: 'card' }, { kind: 'log', text: `${nick(p.playerId)}: [${cardName(p.cardId)}]${attack}${target}` })
      break
    }
    case 'CARD_DISCARDED':
    case 'TURN_TIMED_OUT': {
      next = { ...next, myHand: next.myHand.filter((c) => c.instanceId !== str(p.instanceId)), playability: {} }
      fx.push({ kind: 'log', text: `${nick(p.playerId)}: ${msg.type === 'TURN_TIMED_OUT' ? '시간 초과' : '카드 버림'}` })
      break
    }
    case 'ACCUMULATION_CHANGED':
      next = { ...next, currentAttack: num(p.currentAttack), accumulatedDamage: num(p.accumulatedDamage) }
      break
    case 'HP_CHANGED': {
      const playerId = str(p.playerId)
      const delta = num(p.delta)
      next = { ...next, players: next.players.map((pl) => (pl.playerId === playerId ? { ...pl, hp: num(p.hp) } : pl)) }
      fx.push({ kind: 'floater', playerId, delta })
      if (delta < 0) {
        fx.push({ kind: 'sound', name: 'hit' }, { kind: 'shake', playerId })
        if (p.cause === 'ACCUMULATED') {
          fx.push({ kind: 'log', text: `${nick(playerId)} 누적 데미지 ${-delta} 받음` })
        }
      } else {
        fx.push({ kind: 'sound', name: 'heal' })
      }
      break
    }
    case 'FIELD_CHANGED':
      next = { ...next, field: (p.field as FieldCard[]) ?? [] }
      break
    case 'HAND_UPDATED':
      next = { ...next, myHand: (p.hand as CardInstance[]) ?? [] }
      break
    case 'HAND_COUNT_CHANGED': {
      const playerId = str(p.playerId)
      next = { ...next, players: next.players.map((pl) => (pl.playerId === playerId ? { ...pl, handCount: num(p.count) } : pl)) }
      break
    }
    case 'PLAYABILITY_UPDATED': {
      const cards = (p.cards as CardPlayability[]) ?? []
      next = { ...next, playability: Object.fromEntries(cards.map((c) => [c.instanceId, c])) }
      break
    }
    case 'PLAYER_ELIMINATED': {
      const playerId = str(p.playerId)
      next = { ...next, players: next.players.map((pl) => (pl.playerId === playerId ? { ...pl, eliminated: true, handCount: 0 } : pl)) }
      fx.push({ kind: 'sound', name: 'eliminate' }, { kind: 'log', text: `💀 ${nick(playerId)} 탈락` })
      break
    }
    case 'GAME_ENDED': {
      const winnerIds = (p.winnerIds as string[]) ?? []
      next = { ...next, status: 'FINISHED', winnerIds, ranking: (p.ranking as RankingEntry[]) ?? [], draw: p.draw === true, playability: {} }
      fx.push({ kind: 'sound', name: 'win' }, { kind: 'log', text: p.draw ? '무승부!' : `🏆 ${winnerIds.map(nick).join(', ')} 승리!` })
      break
    }
    case 'ACTION_REJECTED':
      fx.push({ kind: 'sound', name: 'error' }, { kind: 'toast', text: str(p.message) || str(p.code) })
      if (p.code === 'STALE_VERSION') {
        fx.push({ kind: 'resync' })
      }
      break
    default:
      // GAME_STARTED, TURN_ENDED 등은 화면에 따로 반영할 것이 없다
      break
  }
  return { view: next, fx }
}
