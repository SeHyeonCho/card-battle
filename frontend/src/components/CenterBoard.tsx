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
            <span className="chip chip-danger" title={`설치: ${nick(game.timeBomb.ownerId)}`}>
              💣 시한폭탄 · 매 턴 {Math.round(game.timeBomb.p * 100)}% · {game.timeBomb.damage} 피해
            </span>
          )}
          {game.drawCountdown !== null && (
            <span className="chip chip-warn">☕ 무승부까지 {game.drawCountdown}턴</span>
          )}
        </div>
      )}

      <div className="flex items-center justify-center gap-3 md:gap-12">
        <Stat label="현재 공격력" value={game.currentAttack} tone="text-ink" />

        <div className="flex min-h-72 min-w-44 items-center justify-center sm:min-w-50">
          <AnimatePresence mode="popLayout">
            {game.field.map((f, i) => {
              const card = game.cards[f.cardId]
              return card ? (
                <motion.div
                  key={f.instanceId}
                  layoutId={f.instanceId}
                  style={{ marginLeft: i === 0 ? 0 : -120, zIndex: i }}
                  className="rounded-[20px] shadow-[0_18px_40px_-16px_#000]"
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
          {game.field.length === 0 && (
            <span className="grid h-64 w-44 place-items-center rounded-[20px] bg-surface text-sm text-muted sm:w-50">필드가 비어 있음</span>
          )}
        </div>

        <Stat label="누적 데미지" value={game.accumulatedDamage} tone="text-danger" />
      </div>

      {game.fieldLocks.length > 0 && (
        <div className="flex flex-wrap justify-center gap-2 text-xs">
          {game.fieldLocks.map((lock, i) => (
            <span key={`${lock.sourceInstanceId}-${i}`} className="chip chip-curse">
              🔒 [{game.cards[lock.cardId]?.name ?? '?'}] {describeFilter(lock.filter)} 봉인 ·{' '}
              {Math.max(0, lock.expiresAfterTurn - game.turnNumber + 1)}턴
            </span>
          ))}
        </div>
      )}
    </div>
  )
}

function Stat({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className="flex w-20 flex-col items-center gap-1 md:w-40">
      <span className="text-xs font-medium text-muted md:text-sm">{label}</span>
      <motion.span
        key={value}
        initial={{ scale: 1.8, opacity: 0.4 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 400, damping: 18 }}
        className={`num text-5xl md:text-[88px] ${tone}`}
      >
        {value}
      </motion.span>
    </div>
  )
}
