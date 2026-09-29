import { AnimatePresence, motion } from 'motion/react'
import { useState } from 'react'
import { sfx } from '../audio/sfx'
import { CardFace } from '../components/CardFace'
import { CenterBoard } from '../components/CenterBoard'
import { GameLog } from '../components/GameLog'
import { ResultOverlay } from '../components/ResultOverlay'
import { Seat } from '../components/Seat'
import { TurnTimer } from '../components/TurnTimer'
import { discardCard, playCard } from '../game/actions'
import { describeFilter, EXTRA_MODE_TEXT } from '../game/describe'
import { useApp } from '../store/app'
import type { CardInfo, CardInstance } from '../types'

const REASON_TEXT: Record<string, string> = {
  CONDITION_UNMET: '조건 미충족',
  FIELD_LOCK: '필드 효과로 봉인',
  CURSE_LOCK: '저주로 봉인',
  EXTRA_PLAY: '추가로 낼 수 없음',
}

/** 게임 화면. 모든 숫자는 서버가 보낸 값을 그대로 보여준다 */
export function GameScreen() {
  const game = useApp((s) => s.game)
  const floaters = useApp((s) => s.floaters)
  const shakes = useApp((s) => s.shakes)
  const connected = useApp((s) => s.connected)
  const demo = useApp((s) => s.demo)
  const backToRoom = useApp((s) => s.backToRoom)
  const [discardMode, setDiscardMode] = useState(false)
  const [targeting, setTargeting] = useState<{ instance: CardInstance; card: CardInfo } | null>(null)
  const [muted, setMuted] = useState(sfx.muted())

  if (!game) {
    return <div className="flex h-full items-center justify-center text-slate-400">게임 불러오는 중...</div>
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

  function onCardClick(instance: CardInstance) {
    if (!myTurn) return
    const card = game!.cards[instance.cardId]
    if (discarding) {
      discardCard(instance.instanceId)
      setDiscardMode(false)
      return
    }
    if (!game!.playability[instance.instanceId]?.playable) return
    if (card.targeting !== 'NONE') {
      setTargeting({ instance, card })
      return
    }
    playCard(instance.instanceId)
  }

  function onSeatClick(playerId: string) {
    if (!targeting) return
    playCard(targeting.instance.instanceId, playerId)
    setTargeting(null)
  }

  // 천상의 보호막이 있는 다른 사람은 고를 수 없다 (서버도 거절한다)
  const shielded = (playerId: string) =>
    playerId !== game.viewerId && (game.players.find((p) => p.playerId === playerId)?.statuses ?? []).some((st) => st.status === 'UNTARGETABLE')
  const canTarget = (playerId: string, eliminated: boolean) =>
    targeting !== null &&
    !eliminated &&
    !shielded(playerId) &&
    (targeting.card.targeting === 'CHOSEN_ANY' || playerId !== game.viewerId)

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-slate-800 px-4 py-2 text-sm">
        <div className="flex items-center gap-3">
          <span className="font-bold">턴 {game.turnNumber}</span>
          <span className="text-slate-400">방향 {game.direction > 0 ? '→' : '←'}</span>
          <span className={myTurn ? 'font-bold text-amber-300' : 'text-slate-300'}>
            {game.status === 'FINISHED' ? '게임 종료' : myTurn ? '내 차례!' : `${current?.nickname ?? '?'}의 차례`}
          </span>
        </div>
        <div className="flex items-center gap-3">
          {demo && <span className="rounded bg-fuchsia-700/60 px-2 py-0.5 text-xs">미리보기 모드</span>}
          {!demo && <span className={connected ? 'text-emerald-400' : 'text-rose-400'}>{connected ? '● 연결됨' : '● 재연결 중'}</span>}
          <button
            type="button"
            onClick={() => {
              sfx.setMuted(!muted)
              setMuted(!muted)
            }}
            className="rounded px-2 py-1 hover:bg-slate-800"
            title="효과음 켜기/끄기"
          >
            {muted ? '🔇' : '🔊'}
          </button>
        </div>
      </header>

      <div className="px-4 pt-2">
        <TurnTimer deadline={game.turnDeadlineEpochMs} totalSeconds={game.settings.turnTimeSeconds} active={game.status === 'IN_PROGRESS'} />
      </div>

      <main className="grid flex-1 gap-4 overflow-hidden p-4 lg:grid-cols-[1fr_260px]">
        <section className="flex flex-col items-center gap-6 overflow-y-auto">
          <div className="flex flex-wrap justify-center gap-3">
            {others.map((p) => (
              <Seat
                key={p.playerId}
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
                className={`rounded-lg px-4 py-2 text-sm font-semibold ${
                  discardMode ? 'bg-rose-600 text-white' : 'bg-slate-800 text-slate-200 hover:bg-slate-700'
                }`}
              >
                {discardMode ? '버리기 취소' : extra ? '한 장 버리고 끝내기' : '카드 버리기'}
              </button>
            )}
            {targeting && (
              <div className="flex items-center gap-2 rounded-lg bg-rose-950/70 px-3 py-2 text-sm">
                <span>[{targeting.card.name}] 대상을 고르세요</span>
                <button type="button" onClick={() => setTargeting(null)} className="rounded bg-slate-800 px-2 py-1 hover:bg-slate-700">
                  취소
                </button>
              </div>
            )}
          </div>

          {extra && (
            <div className="rounded-lg bg-amber-900/60 px-4 py-2 text-sm text-amber-100">
              ➕{' '}
              {extra.discardOnly
                ? '낼 수 있는 카드가 없어 한 장을 버려야 합니다'
                : `${EXTRA_MODE_TEXT[extra.mode]}${extra.filter ? ` · ${describeFilter(extra.filter)}만` : ''}`}
            </div>
          )}
          {discarding && (
            <div className="text-sm text-rose-300">
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
                return (
                  <motion.button
                    type="button"
                    key={instance.instanceId}
                    layoutId={instance.instanceId}
                    initial={{ opacity: 0, y: 40 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: 60, transition: { duration: 0.2 } }}
                    whileHover={playable ? { y: -14 } : undefined}
                    whileTap={playable ? { scale: 0.95 } : undefined}
                    onClick={() => onCardClick(instance)}
                    disabled={!playable}
                    className={`relative ${playable ? 'cursor-pointer' : 'cursor-not-allowed'} ${myTurn ? '' : 'opacity-70'} ${
                      blocked ? 'opacity-45 grayscale' : ''
                    } ${discarding ? 'ring-2 ring-rose-500 rounded-xl' : ''}`}
                  >
                    <CardFace card={card} />
                    {blocked && playability?.reason && (
                      <span className="absolute inset-x-1 bottom-1 rounded bg-black/80 px-1 py-0.5 text-center text-[10px] text-rose-300">
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

      {game.status === 'FINISHED' && game.ranking && <ResultOverlay game={game} onClose={demo ? () => location.reload() : backToRoom} />}
    </div>
  )
}
