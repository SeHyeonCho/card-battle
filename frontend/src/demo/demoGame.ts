import type { CardInfo, CardInstance, GameSnapshot, PlayerView, ServerMessage } from '../types'

/**
 * 화면 미리보기용 가짜 서버 (주소 끝에 ?demo).
 * 백엔드 없이 카드 애니메이션·효과음·HUD를 확인하려고 만든 것으로, 규칙은 아주 단순하게 흉내만 낸다.
 * 실제 규칙은 전부 서버의 Java 엔진이 판정한다.
 */

const CARDS: CardInfo[] = [
  card('demo.poke', '쿡 찌르기', 'ATTACK', 5, '공격력 5', '카톡 ‘자니?’'),
  card('demo.jab', '가벼운 잽', 'ATTACK', 10, '공격력 10'),
  card('demo.alarm', '월요일 알람', 'ATTACK', 15, '공격력 15', '5분만 더...'),
  card('demo.deadline', '마감 임박', 'ATTACK', 20, '공격력 20'),
  card('demo.overtime', '야근 확정', 'ATTACK', 30, '공격력 30'),
  card('demo.bug', '운영 서버 장애', 'ATTACK', 60, '공격력 60', '금요일 오후 6시였다'),
  card('demo.coffee', '커피 수혈', 'SUPPORT', null, '누적 데미지를 30 줄이고, 남은 만큼 받는다'),
  card('demo.not_my_job', '제 담당 아닌데요', 'SUPPORT', null, '누적 데미지를 받지 않고 다음 차례로 넘긴다'),
  { ...card('demo.snipe', '내 알 바 아님', 'SUPPORT', null, '누적 데미지를 고른 사람에게 바로 입힌다'), targeting: 'CHOSEN_OTHER' },
  card('demo.lunch', '점심 맛집', 'BENEFIT', null, '내 체력 +25'),
]

function card(id: string, name: string, category: CardInfo['category'], attack: number | null, description: string, flavor: string | null = null): CardInfo {
  return { id, name, category, subcategory: null, attack, attackMin: null, attackMax: null, tags: [], targeting: 'NONE', description, flavor }
}

const byId = Object.fromEntries(CARDS.map((c) => [c.id, c]))

interface DemoPlayer extends PlayerView {
  hand: CardInstance[]
}

export function createDemo(emit: (message: ServerMessage) => void) {
  let counter = 0
  let seq = 0
  let version = 1
  let turn = 1
  let current = 0
  let attack = 0
  let accumulated = 0
  const me = 'me'
  const players: DemoPlayer[] = ['나', '민수', '지훈', '서연'].map((nickname, seat) => ({
    playerId: seat === 0 ? me : `bot${seat}`,
    nickname,
    seat,
    hp: 200,
    hpCap: 500,
    handCount: 5,
    eliminated: false,
    hand: [],
  }))
  const draw = (): CardInstance => ({ instanceId: `d${++counter}`, cardId: CARDS[Math.floor(Math.random() * CARDS.length)].id })
  players.forEach((p) => (p.hand = Array.from({ length: 5 }, draw)))

  const view = (p: DemoPlayer): PlayerView => ({ ...p, handCount: p.hand.length })
  const e = (type: string, payload: Record<string, unknown>) => emit({ type, payload, seq: ++seq, version })
  const alive = () => players.filter((p) => !p.eliminated)
  const nextAlive = (from: number) => {
    for (let i = 1; i <= players.length; i++) {
      const p = players[(from + i) % players.length]
      if (!p.eliminated) return p
    }
    return players[from]
  }
  const playability = (p: DemoPlayer) =>
    p.hand.map((c) => ({ instanceId: c.instanceId, playable: true, code: null, reason: null, message: null }))
  const damage = (p: DemoPlayer, amount: number, cause: string) => {
    if (amount <= 0) return
    p.hp -= amount
    e('HP_CHANGED', { playerId: p.playerId, hp: p.hp, delta: -amount, cause })
  }

  function act(actor: DemoPlayer, instanceId: string, targetId?: string, discard = false) {
    const inst = actor.hand.find((c) => c.instanceId === instanceId)
    if (!inst || players[current] !== actor) return
    version++
    actor.hand = actor.hand.filter((c) => c !== inst)
    const def = byId[inst.cardId]
    let receive = true
    let restart = 0
    if (discard) {
      e('CARD_DISCARDED', { playerId: actor.playerId, cardId: def.id, instanceId })
    } else {
      e('CARD_PLAYED', { playerId: actor.playerId, cardId: def.id, instanceId, attack: def.attack, targetId: targetId ?? null })
      if (def.category === 'ATTACK' && def.attack !== null) {
        if (attack === 0 || def.attack >= attack) {
          attack = def.attack
          accumulated += def.attack
          receive = false
        } else {
          restart = def.attack
        }
      } else if (def.id === 'demo.coffee') {
        accumulated = Math.max(0, accumulated - 30)
      } else if (def.id === 'demo.not_my_job') {
        receive = false
      } else if (def.id === 'demo.snipe') {
        const target = players.find((p) => p.playerId === targetId) ?? nextAlive(actor.seat)
        damage(target, accumulated, 'TRANSFER')
        attack = 0
        accumulated = 0
        receive = false
      } else if (def.id === 'demo.lunch') {
        actor.hp = Math.min(actor.hpCap, actor.hp + 25)
        e('HP_CHANGED', { playerId: actor.playerId, hp: actor.hp, delta: 25, cause: 'EFFECT' })
      }
      e('FIELD_CHANGED', { field: [{ instanceId, cardId: def.id, ownerId: actor.playerId, attack: def.attack ?? 0, playedTurn: turn }] })
    }
    if (receive) {
      damage(actor, accumulated, 'ACCUMULATED')
      attack = restart
      accumulated = restart
    }
    e('ACCUMULATION_CHANGED', { currentAttack: attack, accumulatedDamage: accumulated })
    for (const p of alive()) {
      if (p.hp <= 0) {
        p.eliminated = true
        p.hand = []
        e('PLAYER_ELIMINATED', { playerId: p.playerId, turnNumber: turn })
      }
    }
    if (!actor.eliminated) {
      while (actor.hand.length < 5) actor.hand.push(draw())
    }
    if (actor.playerId === me) e('HAND_UPDATED', { hand: actor.hand })
    e('HAND_COUNT_CHANGED', { playerId: actor.playerId, count: actor.hand.length })
    if (alive().length <= 1) {
      const winners = alive().map((p) => p.playerId)
      const ranking = [...players]
        .sort((a, b) => Number(a.eliminated) - Number(b.eliminated))
        .map((p, i) => ({ playerId: p.playerId, nickname: p.nickname, rank: i + 1 }))
      e('GAME_ENDED', { winnerIds: winners, draw: winners.length === 0, ranking })
      return
    }
    current = nextAlive(actor.seat).seat
    turn++
    const next = players[current]
    e('TURN_STARTED', { playerId: next.playerId, turnNumber: turn, deadlineEpochMs: Date.now() + 25_000 })
    if (next.playerId === me) {
      e('PLAYABILITY_UPDATED', { cards: playability(next) })
    } else {
      setTimeout(() => botMove(next), 1100)
    }
  }

  function botMove(bot: DemoPlayer) {
    const attacks = bot.hand.filter((c) => (byId[c.cardId].attack ?? -1) >= attack)
    const others = bot.hand.filter((c) => byId[c.cardId].category !== 'ATTACK')
    const pick = attacks[0] ?? others[0]
    if (!pick) {
      act(bot, bot.hand[0].instanceId, undefined, true)
      return
    }
    const target = byId[pick.cardId].targeting === 'CHOSEN_OTHER' ? alive().find((p) => p !== bot)?.playerId : undefined
    act(bot, pick.instanceId, target)
  }

  const snapshot: GameSnapshot = {
    gameId: 'demo',
    version,
    lastSeq: 0,
    settings: { startingHp: 200, hpCap: 500, handSize: 5, turnTimeSeconds: 25, ruleMode: 'SC1' },
    status: 'IN_PROGRESS',
    turnNumber: turn,
    currentPlayerId: me,
    direction: 1,
    currentAttack: 0,
    accumulatedDamage: 0,
    field: [],
    players: players.map(view),
    viewerId: me,
    myHand: players[0].hand,
    playability: playability(players[0]),
    turnDeadlineEpochMs: Date.now() + 25_000,
    winnerIds: [],
    packCode: 'demo',
    packVersion: 1,
    cards: CARDS,
  }

  return {
    snapshot,
    play: (instanceId: string, targetId?: string) => act(players[0], instanceId, targetId),
    discard: (instanceId: string) => act(players[0], instanceId, undefined, true),
  }
}
