import { AnimatePresence, motion } from 'motion/react'
import { useEffect, useState } from 'react'
import { sfx } from '../audio/sfx'
import { CardFace } from '../components/CardFace'
import { BgmToggle } from '../components/BgmToggle'
import { CenterBoard } from '../components/CenterBoard'
import { ConfirmButton } from '../components/ConfirmButton'
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
    return <div className="flex h-full items-center justify-center text-muted">게임 불러오는 중...</div>
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
  const canTarget = (playerId: string, eliminated: boolean) =>
    targeting !== null &&
    !eliminated &&
    !shielded(playerId) &&
    (targeting.card.targeting === 'CHOSEN_ANY' || playerId !== game.viewerId)

  const seatProps = { baseHp: game.settings.startingHp, defaultHandLimit: game.settings.handSize, cardName, nickOf }
  const turnText = game.status === 'FINISHED' ? '게임 종료' : myTurn ? '내 차례' : `${current?.nickname ?? '?'}의 차례`

  return (
    <div className="flex min-h-full flex-col lg:h-full">
      <header className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 pt-3 text-sm lg:px-5">
        <b className="text-base font-extrabold tracking-tight">카드 배틀</b>
        <span className="text-muted">
          턴 {game.turnNumber} · 방향 {game.direction > 0 ? '→' : '←'}
        </span>
        <span className="flex-1" />
        <span className={`chip ${myTurn ? 'chip-accent font-bold' : ''}`}>{turnText}</span>
        {demo && <span className="chip chip-curse">미리보기</span>}
        {!demo && !connected && <span className="chip chip-danger">재연결 중</span>}
        <button
          type="button"
          onClick={() => {
            sfx.setMuted(!muted)
            setMuted(!muted)
          }}
          className={`btn btn-sm ${muted ? 'text-muted' : ''}`}
          title="효과음 켜기/끄기"
        >
          {muted ? '효과음 꺼짐' : '효과음 켜짐'}
        </button>
        <BgmToggle />
      </header>

      <div className="px-4 pt-2 lg:px-5">
        <TurnTimer
          deadline={game.turnDeadlineEpochMs}
          totalSeconds={game.settings.turnTimeSeconds}
          active={game.status === 'IN_PROGRESS'}
          waiting={game.status === 'IN_PROGRESS' && waiting}
        />
      </div>

      <main className="grid flex-1 gap-3 p-3 lg:min-h-0 lg:grid-cols-[250px_1fr_270px] lg:gap-4 lg:p-5 lg:pb-3">
        {/* 다른 플레이어: 넓은 화면은 왼쪽 세로 목록, 좁은 화면은 가로로 넘기는 줄 */}
        <aside className="panel flex gap-1 overflow-x-auto p-2 lg:flex-col lg:overflow-x-visible lg:overflow-y-auto lg:p-3">
          <div className="label hidden px-2 pt-1 pb-2 lg:block">플레이어</div>
          {others.map((p) => (
            <div key={p.playerId} className="flex shrink-0 flex-col items-stretch gap-1">
              <Seat
                player={p}
                isCurrent={p.playerId === game.currentPlayerId}
                isMe={false}
                floaters={floaters.filter((f) => f.playerId === p.playerId)}
                shake={shakes[p.playerId] ?? 0}
                targetable={canTarget(p.playerId, p.eliminated)}
                onClick={() => onSeatClick(p.playerId)}
                {...seatProps}
              />
              {isHost && p.away && !p.eliminated && game.status === 'IN_PROGRESS' && (
                <ConfirmButton
                  label="강퇴"
                  confirmLabel="정말 강퇴?"
                  onConfirm={() => kickPlayer(p.playerId)}
                  className="btn btn-danger btn-sm mx-2 mb-1"
                  title="강퇴하면 탈락 처리되고 방에서도 나가게 됩니다"
                />
              )}
            </div>
          ))}
        </aside>

        <section className="flex flex-col items-center justify-center gap-3 py-2">
          <CenterBoard game={game} />
        </section>

        <aside className="hidden lg:block lg:min-h-0">
          <GameLog />
        </aside>
      </main>

      {/* 내 자리와 손패 */}
      <footer className="flex flex-col gap-2 px-3 pb-4 lg:px-5 lg:pb-5">
        <div className="flex min-h-7 flex-wrap items-center justify-center gap-2 text-center text-sm">
          {targeting && (
            <>
              <span className="chip chip-danger">[{targeting.card.name}] 대상을 고르세요</span>
              <button type="button" onClick={() => setTargeting(null)} className="btn btn-sm">
                취소
              </button>
            </>
          )}
          {extra && (
            <span className="chip chip-warn">
              {extra.discardOnly
                ? '낼 수 있는 카드가 없어 한 장을 버려야 합니다'
                : `${EXTRA_MODE_TEXT[extra.mode]}${extra.filter ? ` · ${describeFilter(extra.filter)}만` : ''}`}
            </span>
          )}
          {myTurn && waiting && (
            <span className="text-muted">
              {pending ? '골라 둔 카드를 차례가 넘어오는 대로 냅니다 (다시 누르면 취소)' : '차례가 넘어오는 중 — 카드를 골라 두면 곧바로 냅니다'}
            </span>
          )}
          {discarding && (
            <span className="text-danger">
              {extra ? '버릴 카드를 누르세요. 지금까지 낸 카드로 판정합니다.' : '버릴 카드를 누르세요. 누적 데미지가 있으면 받습니다.'}
            </span>
          )}
        </div>

        <div className="flex flex-col items-center gap-3 lg:flex-row lg:items-end">
          <div className="flex w-full items-center gap-2 lg:w-[250px] lg:shrink-0 lg:flex-col lg:items-stretch">
            {me && (
              <div className="panel flex-1 p-2">
                <Seat
                  player={me}
                  isCurrent={myTurn}
                  isMe
                  floaters={floaters.filter((f) => f.playerId === me.playerId)}
                  shake={shakes[me.playerId] ?? 0}
                  targetable={canTarget(me.playerId, me.eliminated)}
                  onClick={() => onSeatClick(me.playerId)}
                  {...seatProps}
                />
              </div>
            )}
            {myTurn && !targeting && !extra?.discardOnly && (
              <button type="button" onClick={() => setDiscardMode(!discardMode)} className={`btn shrink-0 ${discardMode ? 'btn-danger' : ''}`}>
                {discardMode ? '버리기 취소' : extra ? '한 장 버리고 끝내기' : '카드 버리기'}
              </button>
            )}
          </div>

          <div className="flex min-h-40 flex-1 flex-wrap items-end justify-center gap-2 sm:gap-3">
            <AnimatePresence>
              {game.myHand.map((instance) => {
                const card = game.cards[instance.cardId]
                if (!card) return null
                const playability = game.playability[instance.instanceId]
                const playable = myTurn && (discarding || playability?.playable === true)
                const blocked = myTurn && !discarding && playability && !playability.playable
                const isPending = pending?.instanceId === instance.instanceId
                const ring = isPending
                  ? 'shadow-[0_0_0_2px_var(--color-accent),0_18px_40px_-12px_color-mix(in_srgb,var(--color-accent)_45%,transparent)]'
                  : discarding
                    ? 'shadow-[0_0_0_2px_var(--color-danger)]'
                    : ''
                return (
                  <motion.button
                    type="button"
                    key={instance.instanceId}
                    layoutId={instance.instanceId}
                    initial={{ opacity: 0, y: 40 }}
                    animate={{ opacity: 1, y: isPending ? -14 : 0 }}
                    exit={{ opacity: 0, y: 60, transition: { duration: 0.2 } }}
                    whileHover={playable ? { y: -14 } : undefined}
                    whileTap={playable ? { scale: 0.96 } : undefined}
                    onClick={() => onCardClick(instance)}
                    disabled={!playable}
                    className={`relative rounded-2xl ${playable ? 'cursor-pointer hover:shadow-[0_0_0_2px_var(--color-accent)]' : 'cursor-not-allowed'} ${
                      myTurn ? '' : 'opacity-60'
                    } ${blocked ? 'opacity-40' : ''} ${ring}`}
                  >
                    <CardFace card={card} />
                    {isPending && (
                      <span className="chip chip-accent absolute inset-x-1.5 bottom-1.5 justify-center font-bold">
                        {pending.kind === 'discard' ? '곧 버림' : '곧 냄'}
                      </span>
                    )}
                    {myTurn && !discarding && !isPending && playability?.playable && playability.forcedTargetId && (
                      <span className="chip chip-curse absolute inset-x-1.5 bottom-1.5 justify-center">
                        대상: {playability.forcedTargetId === game.viewerId ? '나' : nickOf(playability.forcedTargetId)}
                      </span>
                    )}
                    {blocked && playability?.reason && (
                      <span className="chip chip-danger absolute inset-x-1.5 bottom-1.5 justify-center">
                        {REASON_TEXT[playability.reason] ?? playability.message}
                      </span>
                    )}
                  </motion.button>
                )
              })}
            </AnimatePresence>
          </div>
        </div>
      </footer>

      <TurnBanner banner={banner} viewerId={game.viewerId} nickOf={nickOf} />

      {game.status === 'FINISHED' && game.ranking && <ResultOverlay game={game} onClose={demo ? () => location.reload() : backToRoom} />}
    </div>
  )
}
