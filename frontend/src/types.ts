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

/** engine/view/PlayerView */
export interface PlayerView {
  playerId: string
  nickname: string
  seat: number
  hp: number
  hpCap: number
  handCount: number
  eliminated: boolean
}

/** engine/view/CardPlayabilityView */
export interface CardPlayability {
  instanceId: string
  playable: boolean
  code: string | null
  reason: 'FIELD_LOCK' | 'CURSE_LOCK' | 'CONDITION_UNMET' | null
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
  players: PlayerView[]
  viewerId: string
  myHand: CardInstance[]
  playability: CardPlayability[]
  turnDeadlineEpochMs: number
  winnerIds: string[]
  packCode: string
  packVersion: number
  cards: CardInfo[]
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
