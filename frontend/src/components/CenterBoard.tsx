import { AnimatePresence, motion } from 'motion/react'
import { describeFilter } from '../game/describe'
import type { GameView } from '../game/reduce'
import { CardFace } from './CardFace'

/**
 * 화면 중앙: 현재 공격력(A), 필드 카드, 누적 데미지(D) — PRD FR-UI-01.
 * 위에는 게임 전체에 걸린 것(시한폭탄, 무승부 카운트다운), 아래에는 필드 락을 보여준다.
 */
export function CenterBoard({ game }: { game: GameView }) {
  const nick = (id: string) => game.players.find((p) => p.playerId === id)?.nickname ?? '?'
  return (
    <div className="flex flex-col items-center gap-2">
      {(game.timeBomb || game.drawCountdown !== null) && (
        <div className="flex flex-wrap justify-center gap-2 text-xs">
          {game.timeBomb && (
            <span className="rounded-full bg-orange-900/70 px-3 py-1 text-orange-100" title={`설치: ${nick(game.timeBomb.ownerId)}`}>
              💣 시한폭탄 · 매 턴 {Math.round(game.timeBomb.p * 100)}% · {game.timeBomb.damage} 피해
            </span>
          )}
          {game.drawCountdown !== null && (
            <span className="rounded-full bg-amber-900/70 px-3 py-1 text-amber-100">☕ 무승부까지 {game.drawCountdown}턴</span>
          )}
        </div>
      )}

      <div className="flex items-center justify-center gap-3 md:gap-12">
        <Stat label="현재 공격력" value={game.currentAttack} tone="text-sky-200" />

        <div className="flex h-60 min-w-44 items-center justify-center rounded-2xl border border-dashed border-slate-700 bg-slate-900/40 px-2">
          <AnimatePresence mode="popLayout">
            {game.field.map((f, i) => {
              const card = game.cards[f.cardId]
              return card ? (
                <motion.div
                  key={f.instanceId}
                  layoutId={f.instanceId}
                  style={{ marginLeft: i === 0 ? 0 : -96, zIndex: i }}
                  initial={{ opacity: 0, y: -60, scale: 0.7, rotate: -8 }}
                  animate={{ opacity: 1, y: 0, scale: 1, rotate: 0 }}
                  exit={{ opacity: 0, scale: 0.8, transition: { duration: 0.2 } }}
                  transition={{ type: 'spring', stiffness: 260, damping: 22 }}
                >
                  <CardFace card={card} attack={card.category === 'ATTACK' ? f.attack : undefined} size="field" />
                </motion.div>
              ) : null
            })}
          </AnimatePresence>
          {game.field.length === 0 && <span className="text-sm text-slate-500">필드가 비어 있음</span>}
        </div>

        <Stat label="누적 데미지" value={game.accumulatedDamage} tone="text-rose-300" big />
      </div>

      {game.fieldLocks.length > 0 && (
        <div className="flex flex-wrap justify-center gap-2 text-xs">
          {game.fieldLocks.map((lock, i) => (
            <span key={`${lock.sourceInstanceId}-${i}`} className="rounded-full bg-slate-800 px-3 py-1 text-slate-200">
              🔒 [{game.cards[lock.cardId]?.name ?? '?'}] {describeFilter(lock.filter)} 봉인 ·{' '}
              {Math.max(0, lock.expiresAfterTurn - game.turnNumber + 1)}턴
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

function Stat({ label, value, tone, big }: { label: string; value: number; tone: string; big?: boolean }) {
  return (
    <div className="flex w-20 flex-col items-center md:w-28">
      <span className="text-[11px] tracking-wide text-slate-400 md:text-xs">{label}</span>
      <motion.span
        key={value}
        initial={{ scale: 1.8, opacity: 0.4 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 400, damping: 18 }}
        className={`font-black tabular-nums ${tone} ${big ? 'text-5xl md:text-6xl' : 'text-4xl md:text-5xl'}`}
      >
        {value}
      </motion.span>
    </div>
  )
}
