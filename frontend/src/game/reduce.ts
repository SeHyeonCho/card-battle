import type {
  CardInfo,
  CardInstance,
  CardPlayability,
  ExtraPlayState,
  FieldCard,
  FieldLock,
  GameSettings,
  GameSnapshot,
  PlayerView,
  RankingEntry,
  ServerMessage,
  StatusView,
  TimeBomb,
} from '../types'
import { EXTRA_MODE_TEXT, STATUS_TEXT } from './describe'
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
  fieldLocks: FieldLock[]
  timeBomb: TimeBomb | null
  drawCountdown: number | null
  /** 추가 제출 중이면 그 내용 (현재 차례인 사람의 것) */
  extraPlay: ExtraPlayState | null
  players: PlayerView[]
  viewerId: string
  myHand: CardInstance[]
  playability: Record<string, CardPlayability>
  turnDeadlineEpochMs: number
  /**
   * 차례 전환이 끝나 카드를 낼 수 있게 되는 시각 (이 브라우저의 Date.now() 기준, FR-GAME-10).
   * 서버 시계와 어긋나지 않도록 서버가 알려 준 "남은 전환 시간"을 받은 순간부터 잰다
   */
  turnActiveAt: number
  winnerIds: string[]
  ranking: RankingEntry[] | null
  draw: boolean
  cards: Record<string, CardInfo>
}

/** 상태 변화와 함께 일어나야 하는 연출 (소리, 떠오르는 숫자, 로그 등) */
export type Fx =
  | { kind: 'sound'; name: SfxName }
  /** 카드를 냈을 때: 카드팩에 그 카드의 소리가 있으면 그것을, 없으면 기본 카드 소리를 낸다 */
  | { kind: 'cardSound'; cardId: string }
  | { kind: 'floater'; playerId: string; delta: number }
  /** 차례 전환 배너: until(Date.now() 기준)까지 "누구의 차례"를 크게 띄운다 */
  | { kind: 'turnBanner'; playerId: string; until: number }
  | { kind: 'shake'; playerId: string }
  | { kind: 'log'; text: string }
  | { kind: 'toast'; text: string }
  | { kind: 'resync' }

/** 체력 감소 원인별 로그 문구 (없으면 로그를 남기지 않는다) */
const HP_CAUSE: Record<string, string> = {
  ACCUMULATED: '누적 데미지',
  TRANSFER: '전달된 누적 데미지',
  REDIRECT: '대신 받은 누적 데미지',
  TIME_BOMB: '폭탄 피해',
  SHARE: '나눠 받은 누적 데미지',
  BASEBALL_BAT: '야구빠따 피해',
}

export function fromSnapshot(s: GameSnapshot, now = Date.now()): GameView {
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
    fieldLocks: s.fieldLocks ?? [],
    timeBomb: s.timeBomb ?? null,
    drawCountdown: s.drawCountdown ?? null,
    extraPlay: s.extraPlay ?? null,
    players: s.players,
    viewerId: s.viewerId,
    myHand: s.myHand,
    playability: Object.fromEntries(s.playability.map((p) => [p.instanceId, p])),
    turnDeadlineEpochMs: s.turnDeadlineEpochMs,
    turnActiveAt: now + (s.transitionRemainingMs ?? 0),
    winnerIds: s.winnerIds,
    ranking: null,
    draw: false,
    cards: Object.fromEntries(s.cards.map((c) => [c.id, c])),
  }
}

const str = (v: unknown) => (typeof v === 'string' ? v : '')
const num = (v: unknown) => (typeof v === 'number' ? v : 0)

/**
 * 스냅샷에 담긴 최근 이벤트로 게임 로그 문구를 다시 만든다 (새로고침 복원, FR-UI-04).
 * 문구는 이벤트 내용과 닉네임·카드 이름만으로 정해지므로 스냅샷 화면 상태를 기준으로 만들고, 상태 변화는 버린다.
 */
export function logFromHistory(view: GameView, events: ServerMessage[]): string[] {
  const base = { ...view, lastSeq: -1 }
  return events.flatMap((e) => reduce(base, e).fx.flatMap((f) => (f.kind === 'log' ? [f.text] : [])))
}

/** @param now 이벤트를 받은 시각. 전환 시간은 이때부터 잰다 */
export function reduce(view: GameView, msg: ServerMessage, now = Date.now()): { view: GameView; fx: Fx[] } {
  if (msg.seq !== undefined && msg.seq <= view.lastSeq) {
    return { view, fx: [] } // 스냅샷에 이미 들어 있는 이벤트
  }
  const p = msg.payload ?? {}
  const fx: Fx[] = []
  const nick = (id: unknown) => view.players.find((pl) => pl.playerId === id)?.nickname ?? '?'
  const cardName = (id: unknown) => view.cards[str(id)]?.name ?? '?'
  const updatePlayer = (id: unknown, patch: (pl: PlayerView) => PlayerView) =>
    next.players.map((pl) => (pl.playerId === id ? patch(pl) : pl))
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
        turnActiveAt: now + num(p.transitionMs),
        playability: playerId === view.viewerId ? next.playability : {},
      }
      fx.push({ kind: 'turnBanner', playerId, until: now + num(p.transitionMs) })
      if (playerId === view.viewerId) {
        fx.push({ kind: 'sound', name: 'turn' })
      }
      break
    }
    case 'CARD_PLAYED': {
      const instanceId = str(p.instanceId)
      // 필드를 바로 바꿔서, 내 손패의 같은 카드가 필드로 "날아가는" 애니메이션이 한 번에 일어나게 한다.
      // 추가 제출·전군 돌격으로 이어서 낸 카드는 필드에 쌓는다 (최종 필드는 FIELD_CHANGED가 확정한다)
      const played = { instanceId, cardId: str(p.cardId), ownerId: str(p.playerId), attack: num(p.attack), playedTurn: next.turnNumber }
      const stack = next.extraPlay !== null || p.via === 'PLAY_ALL'
      next = {
        ...next,
        myHand: next.myHand.filter((c) => c.instanceId !== instanceId),
        field: stack ? [...next.field, played] : [played],
        playability: {},
      }
      const attack = typeof p.attack === 'number' ? ` (공격력 ${p.attack})` : ''
      const target = p.targetId ? ` → ${nick(p.targetId)}` : ''
      fx.push({ kind: 'cardSound', cardId: str(p.cardId) }, { kind: 'log', text: `${nick(p.playerId)}: [${cardName(p.cardId)}]${attack}${target}` })
      break
    }
    case 'CARD_DISCARDED':
    case 'TURN_TIMED_OUT': {
      next = { ...next, myHand: next.myHand.filter((c) => c.instanceId !== str(p.instanceId)), playability: {} }
      const streak = num(p.consecutiveTimeouts) >= 2 ? ` (${num(p.consecutiveTimeouts)}번 연속)` : ''
      fx.push({ kind: 'log', text: `${nick(p.playerId)}: ${msg.type === 'TURN_TIMED_OUT' ? `시간 초과${streak}` : '카드 버림'}` })
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
        const reason = HP_CAUSE[str(p.cause)]
        if (reason) {
          fx.push({ kind: 'log', text: `${nick(playerId)} ${reason} ${-delta} 받음` })
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
      next = {
        ...next,
        players: next.players.map((pl) => (pl.playerId === playerId ? { ...pl, eliminated: true, away: false, handCount: 0 } : pl)),
      }
      const text = p.reason === 'KICKED' ? `🚪 ${nick(playerId)} 강퇴 (탈락)` : `💀 ${nick(playerId)} 탈락`
      fx.push({ kind: 'sound', name: 'eliminate' }, { kind: 'log', text })
      break
    }
    case 'PLAYER_CONNECTION': {
      // 서버가 연결 상태만 알리는 메시지 (seq·version 없음, 게임 기록에도 남지 않는다)
      const connected = p.connected !== false
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, connected })) }
      fx.push({ kind: 'log', text: connected ? `🔌 ${nick(p.playerId)} 다시 연결됨` : `📡 ${nick(p.playerId)} 연결 끊김` })
      break
    }
    case 'PLAYER_AWAY_CHANGED': {
      const away = p.away === true
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, away })) }
      fx.push({ kind: 'log', text: away ? `💤 ${nick(p.playerId)} 자리 비움 (연속 시간 초과)` : `👋 ${nick(p.playerId)} 돌아옴` })
      break
    }
    case 'GAME_ENDED': {
      const winnerIds = (p.winnerIds as string[]) ?? []
      next = { ...next, status: 'FINISHED', winnerIds, ranking: (p.ranking as RankingEntry[]) ?? [], draw: p.draw === true, playability: {} }
      const drawText = p.reason === 'DRAW_COUNTDOWN' ? '☕ 카운트다운 종료 — 무승부!' : '무승부!'
      fx.push({ kind: 'sound', name: 'win' }, { kind: 'log', text: p.draw ? drawText : `🏆 ${winnerIds.map(nick).join(', ')} 승리!` })
      break
    }
    case 'HAND_LIMIT_CHANGED':
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, handLimit: num(p.handLimit) })) }
      fx.push({ kind: 'log', text: `${nick(p.playerId)} 손패 한도 ${num(p.handLimit)}장` })
      break
    case 'CURSE_APPLIED':
      next = {
        ...next,
        players: updatePlayer(p.playerId, (pl) => ({ ...pl, curse: { cardId: str(p.cardId) || null, casterId: str(p.casterId) } })),
      }
      fx.push({ kind: 'log', text: `😈 ${nick(p.playerId)}에게 [${cardName(p.cardId)}] 저주` })
      break
    case 'CURSE_REMOVED':
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, curse: null })) }
      fx.push({ kind: 'log', text: `✨ ${nick(p.playerId)}의 [${cardName(p.cardId)}] 저주 해제` })
      break
    case 'STATUS_APPLIED': {
      const status: StatusView = { status: str(p.status), turnsLeft: num(p.turns) }
      next = {
        ...next,
        players: updatePlayer(p.playerId, (pl) => ({ ...pl, statuses: [...pl.statuses.filter((st) => st.status !== status.status), status] })),
      }
      const label = STATUS_TEXT[status.status]
      fx.push({ kind: 'log', text: `${label?.icon ?? '•'} ${nick(p.playerId)} ${label?.label ?? status.status} (${status.turnsLeft}턴)` })
      break
    }
    case 'STATUS_EXPIRED':
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, statuses: pl.statuses.filter((st) => st.status !== p.status) })) }
      break
    case 'HAND_REVEALED':
      next = { ...next, players: updatePlayer(p.playerId, (pl) => ({ ...pl, revealedHand: (p.hand as CardInstance[]) ?? [] })) }
      break
    case 'PLAY_FUMBLED':
      fx.push({ kind: 'log', text: `🌀 ${nick(p.playerId)}: [${cardName(p.cardId)}] 내기가 버리기로 바뀜` })
      if (p.playerId === view.viewerId) {
        fx.push({ kind: 'toast', text: '저주 때문에 카드 내기가 버리기로 바뀌었습니다' })
      }
      break
    case 'FIELD_LOCKS_CHANGED':
      next = { ...next, fieldLocks: (p.locks as FieldLock[]) ?? [] }
      break
    case 'TURN_SKIPPED':
      fx.push({ kind: 'log', text: `⏭ ${nick(p.playerId)} 차례 건너뜀` })
      break
    case 'TURN_LOCKED':
      fx.push({ kind: 'log', text: `⏸ ${nick(p.playerId)} 아무것도 할 수 없음` })
      break
    case 'DIRECTION_CHANGED':
      next = { ...next, direction: num(p.direction) }
      fx.push({ kind: 'log', text: '🔄 순서가 반대로 바뀜' })
      break
    case 'TIME_BOMB_PLANTED':
      next = { ...next, timeBomb: { ownerId: str(p.ownerId), p: num(p.p), damage: num(p.damage) } }
      fx.push({ kind: 'log', text: `💣 ${nick(p.ownerId)}: 랜덤시한폭탄 설치` })
      break
    case 'TIME_BOMB_EXPLODED':
      next = { ...next, timeBomb: null }
      fx.push({ kind: 'log', text: `💥 폭탄이 ${nick(p.playerId)}에게 터짐!` })
      break
    case 'TIME_BOMB_REMOVED':
      next = { ...next, timeBomb: null }
      fx.push({ kind: 'log', text: '💣 시한폭탄 해제' })
      break
    case 'DRAW_COUNTDOWN_CHANGED':
      next = { ...next, drawCountdown: typeof p.turnsLeft === 'number' ? p.turnsLeft : null }
      break
    case 'EXTRA_PLAY_STARTED': {
      const extra: ExtraPlayState = {
        mode: (str(p.mode) || 'ANY') as ExtraPlayState['mode'],
        filter: (p.filter as ExtraPlayState['filter']) ?? null,
        discardOnly: p.discardOnly === true,
        attack: 0,
      }
      next = { ...next, extraPlay: extra }
      fx.push({ kind: 'log', text: `➕ ${nick(p.playerId)}: ${extra.discardOnly ? '낼 카드가 없어 한 장 버려야 함' : EXTRA_MODE_TEXT[extra.mode]}` })
      break
    }
    case 'TURN_ENDED':
      next = { ...next, extraPlay: null }
      break
    case 'CARDS_BANNED':
      fx.push({ kind: 'log', text: `🕳 게임에서 제외: ${((p.cardIds as string[]) ?? []).map(cardName).join(', ')}` })
      break
    case 'ACTION_REJECTED':
      if (p.code === 'TURN_TRANSITION') {
        // 화면도 전환이 끝날 때까지 기다렸다 내므로 드물다. 오류음 없이 안내만 한다
        fx.push({ kind: 'toast', text: str(p.message) })
        break
      }
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
