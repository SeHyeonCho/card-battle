// 서버와 주고받는 데이터 모양. 백엔드의 Java record와 1:1로 맞춘다.

export type CardCategory = 'ATTACK' | 'SUPPORT' | 'BENEFIT' | 'CURSE' | 'MISC'
export type Targeting = 'NONE' | 'CHOSEN_OTHER' | 'CHOSEN_ANY'

/** engine/view/CardView */
export interface CardInfo {
  id: string
  name: string
  category: CardCategory
  subcategory: string | null
  attack: number | null
  attackMin: number | null
  attackMax: number | null
  tags: string[]
  targeting: Targeting
  description: string
  flavor: string | null
}

/** engine/card/CardInstance — 손패의 카드 한 장 */
export interface CardInstance {
  instanceId: string
  cardId: string
}

/** engine/state/FieldCard */
export interface FieldCard {
  instanceId: string
  cardId: string
  ownerId: string
  attack: number
  playedTurn: number
}

/** engine/view/PlayerView.Curse — 걸린 저주 */
export interface CurseView {
  cardId: string | null
  casterId: string
}

/** engine/view/PlayerView.Status — 지속 상태 (UNTARGETABLE, REGEN, NEXT_ATTACK_BONUS) */
export interface StatusView {
  status: string
  turnsLeft: number
}

/** engine/view/PlayerView */
export interface PlayerView {
  playerId: string
  nickname: string
  seat: number
  hp: number
  /** 실제 체력 상한 (저주로 줄어든 값 포함) */
  hpCap: number
  handCount: number
  /** 실제 손패 한도 (저주로 고정된 값 포함) */
  handLimit: number
  eliminated: boolean
  curse: CurseView | null
  statuses: StatusView[]
  /** "지켜보고 있다" 저주로 공개된 손패. 공개되지 않았으면 null */
  revealedHand: CardInstance[] | null
}

/** engine/state/FieldLock — 필드 카드가 거는 제출 제한 */
export interface FieldLock {
  sourceInstanceId: string
  cardId: string
  ownerId: string
  filter: CardFilter
  expiresAfterTurn: number
}

/** engine/rules/CardFilter */
export interface CardFilter {
  category?: string | string[]
  subcategory?: string | string[]
  tags?: string[]
  attackGte?: number
  attackLte?: number
  hasEffects?: boolean | string[]
  cardIds?: string[]
  excludeCardIds?: string[]
}

/** engine/state/TimeBomb */
export interface TimeBomb {
  ownerId: string
  p: number
  damage: number
}

/** engine/state/ExtraPlayState — 추가 제출 진행 중 */
export interface ExtraPlayState {
  mode: 'ANY' | 'SUM' | 'DOUBLE' | 'HEAL_SELF'
  filter: CardFilter | null
  discardOnly: boolean
  attack: number
}

/** engine/view/CardPlayabilityView */
export interface CardPlayability {
  instanceId: string
  playable: boolean
  code: string | null
  reason: 'FIELD_LOCK' | 'CURSE_LOCK' | 'CONDITION_UNMET' | 'EXTRA_PLAY' | null
  message: string | null
}

export interface RankingEntry {
  playerId: string
  nickname: string
  rank: number
}

/** engine/GameSettings */
export interface GameSettings {
  startingHp: number
  hpCap: number
  handSize: number
  turnTimeSeconds: number
  ruleMode: string
}

/** engine/view/GameSnapshot */
export interface GameSnapshot {
  gameId: string
  version: number
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
  extraPlay: ExtraPlayState | null
  players: PlayerView[]
  viewerId: string
  myHand: CardInstance[]
  playability: CardPlayability[]
  turnDeadlineEpochMs: number
  winnerIds: string[]
  packCode: string
  packVersion: number
  cards: CardInfo[]
  /** 최근 공개 이벤트 (오래된 것부터). 새로고침 후 게임 로그를 다시 채운다 */
  recentEvents?: ServerMessage[]
}

/** 서버가 보내는 모든 게임 메시지: {type, payload, seq?, version?} */
export interface ServerMessage {
  type: string
  payload: Record<string, unknown>
  seq?: number
  version?: number
}

export interface Session {
  token: string
  playerId: string
  nickname: string
}

export interface RoomSettings {
  maxPlayers: number
  packCode: string
  startingHp: number
  hpCap: number
  handSize: number
  turnTimeSeconds: number
}

export interface RoomMember {
  playerId: string
  nickname: string
  ready: boolean
  joinedAt: number
}

export interface Room {
  roomId: string
  inviteCode: string
  hostId: string
  settings: RoomSettings
  status: 'LOBBY' | 'IN_GAME'
  members: RoomMember[]
  gameId: string | null
  createdAt: number
}

export interface PackSummary {
  code: string
  name: string
  version: number
  visibility: 'PUBLIC' | 'PRIVATE'
}
