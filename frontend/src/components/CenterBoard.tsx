import { AnimatePresence, motion } from 'motion/react'
import type { GameView } from '../game/reduce'
import { CardFace } from './CardFace'

/** 화면 중앙: 현재 공격력(A), 필드 카드, 누적 데미지(D) — PRD FR-UI-01 */
export function CenterBoard({ game }: { game: GameView }) {
  return (
    <div className="flex items-center justify-center gap-3 md:gap-12">
      <Stat label="현재 공격력" value={game.currentAttack} tone="text-sky-200" />

      <div className="flex h-60 w-44 items-center justify-center rounded-2xl border border-dashed border-slate-700 bg-slate-900/40">
        <AnimatePresence mode="popLayout">
          {game.field.map((f) => {
            const card = game.cards[f.cardId]
            return card ? (
              <motion.div
                key={f.instanceId}
                layoutId={f.instanceId}
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
