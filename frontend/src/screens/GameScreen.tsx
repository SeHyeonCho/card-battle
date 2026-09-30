import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { sfx } from '../audio/sfx'
import { CardFace } from '../components/CardFace'
import { BgmToggle } from '../components/BgmToggle'
import { CenterBoard } from '../components/CenterBoard'
import { GameLog } from '../components/GameLog'
import { ResultOverlay } from '../components/ResultOverlay'
import { Seat } from '../components/Seat'
import { TurnBanner } from '../components/TurnBanner'
import { TurnTimer } from '../components/TurnTimer'
import { discardCard, kickPlayer, playCard } from '../game/actions'
import { describeFilter, EXTRA_MODE_TEXT } from '../game/describe'
import { useTurnTransition } from '../game/useTurnTransition'
import { useApp } from '../store/app'
import type { CardInfo, CardInstance } from '../types'

const REASON_TEXT: Record<string, string> = {
  CONDITION_UNMET: '조건 미충족',
  FIELD_LOCK: '필드 효과로 봉인',
  CURSE_LOCK: '저주로 봉인',
  EXTRA_PLAY: '추가로 낼 수 없음',
}

/** 차례 전환 중에 골라 둔 행동. 전환이 끝나면 곧바로 보낸다 (FR-GAME-10) */
type Pending = { turn: number; instanceId: string } & ({ kind: 'play'; targetId?: string } | { kind: 'discard' })

function submit(action: Pending) {
  if (action.kind === 'discard') {
    discardCard(action.instanceId)
  } else {
    playCard(action.instanceId, action.targetId)
  }
}

/** 게임 화면. 모든 숫자는 서버가 보낸 값을 그대로 보여준다 */
export function GameScreen() {
  const game = useApp((s) => s.game)
  const floaters = useApp((s) => s.floaters)
  const shakes = useApp((s) => s.shakes)
  const connected = useApp((s) => s.connected)
  const demo = useApp((s) => s.demo)
  const backToRoom = useApp((s) => s.backToRoom)
  const room = useApp((s) => s.room)
  const session = useApp((s) => s.session)
  const banner = useApp((s) => s.banner)
  const [discardMode, setDiscardMode] = useState(false)
  const [targeting, setTargeting] = useState<{ instance: CardInstance; card: CardInfo } | null>(null)
  const [muted, setMuted] = useState(sfx.muted())
  const [pending, setPending] = useState<Pending | null>(null)

  // 차례 전환(FR-GAME-10): 전환 중에는 카드를 골라 두기만 하고, 전환이 끝나는 순간 낸다
  const activeAt = game?.turnActiveAt ?? 0
  const waiting = useTurnTransition(activeAt)
  useEffect(() => {
    if (!pending) return
    const id = setTimeout(() => {
      setPending(null)
      const latest = useApp.getState().game
      const stillMine = latest?.status === 'IN_PROGRESS' && latest.currentPlayerId === latest.viewerId
      if (stillMine && latest.turnNumber === pending.turn) submit(pending)
    }, Math.max(0, activeAt - Date.now()))
    return () => clearTimeout(id)
  }, [pending, activeAt])

  if (!game) {
    return <div className="flex h-full items-center justify-center text-sc-yellow">게임 불러오는 중...</div>
  }

  const me = game.players.find((p) => p.playerId === game.viewerId)
  const others = game.players.filter((p) => p.playerId !== game.viewerId)
  const myTurn = game.status === 'IN_PROGRESS' && game.currentPlayerId === game.viewerId
  const current = game.players.find((p) => p.playerId === game.currentPlayerId)
  // 추가 제출 중: 한 장 더 내거나, 버려서 끝낸다. 낼 카드가 없으면 버리기만 할 수 있다
  const extra = myTurn ? game.extraPlay : null
  const discarding = discardMode || extra?.discardOnly === true
  const cardName = (cardId: string | null) => (cardId ? (game.cards[cardId]?.name ?? '?') : '?')
  const nickOf = (playerId: string) => game.players.find((p) => p.playerId === playerId)?.nickname ?? '?'

  /** 전환 중이면 골라 두기만 하고, 아니면 바로 보낸다 */
  function act(action: Pending) {
    if (waiting) {
      setPending(action)
    } else {
      submit(action)
    }
  }

  function onCardClick(instance: CardInstance) {
    if (!myTurn) return
    if (pending?.instanceId === instance.instanceId) {
      setPending(null) // 골라 둔 카드를 다시 누르면 취소
      return
    }
    const card = game!.cards[instance.cardId]
    if (discarding) {
      act({ kind: 'discard', turn: game!.turnNumber, instanceId: instance.instanceId })
      setDiscardMode(false)
      return
    }
    const playability = game!.playability[instance.instanceId]
    if (!playability?.playable) return
    // 멈춰! 저주처럼 대상이 이미 정해져 있으면 고르지 않고 바로 낸다
    if (card.targeting !== 'NONE' && !playability.forcedTargetId) {
      setTargeting({ instance, card })
      return
    }
    act({ kind: 'play', turn: game!.turnNumber, instanceId: instance.instanceId })
  }

  function onSeatClick(playerId: string) {
    if (!targeting) return
    act({ kind: 'play', turn: game!.turnNumber, instanceId: targeting.instance.instanceId, targetId: playerId })
    setTargeting(null)
  }

  // 천상의 보호막이 있는 다른 사람은 고를 수 없다 (서버도 거절한다)
  const shielded = (playerId: string) =>
    playerId !== game.viewerId && (game.players.find((p) => p.playerId === playerId)?.statuses ?? []).some((st) => st.status === 'UNTARGETABLE')
  // 방장은 자리 비움(연속 시간 초과)인 참가자를 강퇴할 수 있다 (PRD 4.3)
  const isHost = !demo && room !== null && session !== null && room.hostId === session.playerId
  const kick = (playerId: string) => {
    if (window.confirm(`${nickOf(playerId)}님을 강퇴할까요? 강퇴하면 탈락 처리되고 방에서도 나가게 됩니다.`)) {
      kickPlayer(playerId)
    }
  }
  const canTarget = (playerId: string, eliminated: boolean) =>
    targeting !== null &&
    !eliminated &&
    !shielded(playerId) &&
    (targeting.card.targeting === 'CHOSEN_ANY' || playerId !== game.viewerId)

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b-2 border-rim-steel bg-gradient-to-b from-[#2a2f3a] to-[#14171d] px-4 py-2 text-sm shadow-[inset_0_-1px_#000]">
        <div className="flex items-center gap-3">
          <span className="font-bold text-sc-yellow text-outline">턴 {game.turnNumber}</span>
          <span className="text-slate-300">방향 {game.direction > 0 ? '→' : '←'}</span>
          <span className={myTurn ? 'font-bold text-sc-yellow text-outline' : 'text-slate-200'}>
            {game.status === 'FINISHED' ? '게임 종료' : myTurn ? '▶ 내 차례!' : `${current?.nickname ?? '?'}의 차례`}
          </span>
        </div>
        <div className="flex items-center gap-3">
          {demo && <span className="border border-rim-purple bg-purple-950 px-2 py-0.5 text-xs">미리보기 모드</span>}
          {!demo && <span className={connected ? 'text-sc-green' : 'text-rose-400'}>{connected ? '● 연결됨' : '● 재연결 중'}</span>}
          <button
            type="button"
            onClick={() => {
              sfx.setMuted(!muted)
              setMuted(!muted)
            }}
            className="btn px-2 py-0.5"
            title="효과음 켜기/끄기"
          >
            {muted ? '🔇' : '🔊'}
          </button>
          <BgmToggle />
        </div>
      </header>

      <div className="px-4 pt-2">
        <TurnTimer
          deadline={game.turnDeadlineEpochMs}
          totalSeconds={game.settings.turnTimeSeconds}
          active={game.status === 'IN_PROGRESS'}
          waiting={game.status === 'IN_PROGRESS' && waiting}
        />
      </div>

      <main className="grid flex-1 gap-4 overflow-hidden p-4 lg:grid-cols-[1fr_260px]">
        <section className="flex flex-col items-center gap-6 overflow-y-auto">
          <div className="flex flex-wrap justify-center gap-3">
            {others.map((p) => (
              <div key={p.playerId} className="flex flex-col items-center gap-1">
                <Seat
                  player={p}
                  baseHp={game.settings.startingHp}
                  isCurrent={p.playerId === game.currentPlayerId}
                  isMe={false}
                  floaters={floaters.filter((f) => f.playerId === p.playerId)}
                  shake={shakes[p.playerId] ?? 0}
                  targetable={canTarget(p.playerId, p.eliminated)}
                  onClick={() => onSeatClick(p.playerId)}
                  defaultHandLimit={game.settings.handSize}
                  cardName={cardName}
                  nickOf={nickOf}
                />
                {isHost && p.away && !p.eliminated && game.status === 'IN_PROGRESS' && (
                  <button type="button" onClick={() => kick(p.playerId)} className="btn btn-red px-2 py-0.5 text-xs">
                    강퇴
                  </button>
                )}
              </div>
            ))}
          </div>

          <CenterBoard game={game} />

          <div className="flex flex-wrap items-center justify-center gap-3">
            {me && (
              <Seat
                player={me}
                baseHp={game.settings.startingHp}
                isCurrent={myTurn}
                isMe
                floaters={floaters.filter((f) => f.playerId === me.playerId)}
                shake={shakes[me.playerId] ?? 0}
                targetable={canTarget(me.playerId, me.eliminated)}
                onClick={() => onSeatClick(me.playerId)}
                defaultHandLimit={game.settings.handSize}
                cardName={cardName}
                nickOf={nickOf}
              />
            )}
            {myTurn && !targeting && !extra?.discardOnly && (
              <button
                type="button"
                onClick={() => setDiscardMode(!discardMode)}
                className={`btn px-4 py-2 text-sm ${discardMode ? 'btn-red' : 'btn-orange'}`}
              >
                {discardMode ? '버리기 취소' : extra ? '한 장 버리고 끝내기' : '카드 버리기'}
              </button>
            )}
            {targeting && (
              <div className="frame frame-red flex items-center gap-2 px-3 py-2 text-sm">
                <span className="text-rose-100">[{targeting.card.name}] 대상을 고르세요</span>
                <button type="button" onClick={() => setTargeting(null)} className="btn px-2 py-0.5">
                  취소
                </button>
              </div>
            )}
          </div>

          {extra && (
            <div className="frame frame-gold px-4 py-2 text-sm text-amber-100">
              ➕{' '}
              {extra.discardOnly
                ? '낼 수 있는 카드가 없어 한 장을 버려야 합니다'
                : `${EXTRA_MODE_TEXT[extra.mode]}${extra.filter ? ` · ${describeFilter(extra.filter)}만` : ''}`}
            </div>
          )}
          {myTurn && waiting && (
            <div className="text-sm text-sc-yellow text-outline">
              {pending ? '골라 둔 카드를 차례가 넘어오는 대로 냅니다 (다시 누르면 취소)' : '차례가 넘어오는 중 — 카드를 골라 두면 곧바로 냅니다'}
            </div>
          )}
          {discarding && (
            <div className="text-sm text-rose-300 text-outline">
              {extra ? '버릴 카드를 누르세요. 지금까지 낸 카드로 판정합니다.' : '버릴 카드를 누르세요. 누적 데미지가 있으면 받습니다.'}
            </div>
          )}

          <div className="flex min-h-48 flex-wrap justify-center gap-2 pb-4">
            <AnimatePresence>
              {game.myHand.map((instance) => {
                const card = game.cards[instance.cardId]
                if (!card) return null
                const playability = game.playability[instance.instanceId]
                const playable = myTurn && (discarding || playability?.playable === true)
                const blocked = myTurn && !discarding && playability && !playability.playable
                const isPending = pending?.instanceId === instance.instanceId
                return (
                  <motion.button
                    type="button"
                    key={instance.instanceId}
                    layoutId={instance.instanceId}
                    initial={{ opacity: 0, y: 40 }}
                    animate={{ opacity: 1, y: isPending ? -12 : 0 }}
                    exit={{ opacity: 0, y: 60, transition: { duration: 0.2 } }}
                    whileHover={playable ? { y: -14 } : undefined}
                    whileTap={playable ? { scale: 0.95 } : undefined}
                    onClick={() => onCardClick(instance)}
                    disabled={!playable}
                    className={`relative ${playable ? 'cursor-pointer' : 'cursor-not-allowed'} ${myTurn ? '' : 'opacity-70'} ${
                      blocked ? 'opacity-45 grayscale' : ''
                    } ${discarding ? 'shadow-[0_0_0_3px_#ff5a5a,0_0_16px_#ff2a2a] rounded-lg' : ''} ${
                      isPending ? 'rounded-lg shadow-[0_0_0_3px_#ffd84a,0_0_18px_#ffb400]' : ''
                    }`}
                  >
                    <CardFace card={card} />
                    {isPending && (
                      <span className="absolute inset-x-1 bottom-1 border border-rim-gold bg-black/90 px-1 py-0.5 text-center text-[11px] text-sc-yellow">
                        {pending.kind === 'discard' ? '곧 버림' : '곧 냄'}
                      </span>
                    )}
                    {myTurn && !discarding && !isPending && playability?.playable && playability.forcedTargetId && (
                      <span className="absolute inset-x-1 bottom-1 border border-rim-purple bg-black/90 px-1 py-0.5 text-center text-[11px] text-purple-200">
                        대상: {playability.forcedTargetId === game.viewerId ? '나' : nickOf(playability.forcedTargetId)}
                      </span>
                    )}
                    {blocked && playability?.reason && (
                      <span className="absolute inset-x-1 bottom-1 border border-rose-500 bg-black/90 px-1 py-0.5 text-center text-[11px] text-rose-300">
                        {REASON_TEXT[playability.reason] ?? playability.message}
                      </span>
                    )}
                  </motion.button>
                )
              })}
            </AnimatePresence>
          </div>
        </section>

        <aside className="hidden lg:block">
          <GameLog />
        </aside>
      </main>

      <TurnBanner banner={banner} viewerId={game.viewerId} nickOf={nickOf} />

      {game.status === 'FINISHED' && game.ranking && <ResultOverlay game={game} onClose={demo ? () => location.reload() : backToRoom} />}
    </div>
  )
}
