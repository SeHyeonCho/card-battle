import { motion } from 'motion/react'
import type { GameView } from '../game/reduce'

/** 게임 결과 (PRD FR-UI-07) */
export function ResultOverlay({ game, onClose }: { game: GameView; onClose: () => void }) {
  const ranking = [...(game.ranking ?? [])].sort((a, b) => a.rank - b.rank)
  const iWon = game.winnerIds.includes(game.viewerId)

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="fixed inset-0 z-40 flex items-center justify-center bg-black/70 p-4">
      <motion.div
        initial={{ scale: 0.7, y: 30 }}
        animate={{ scale: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 220, damping: 18 }}
        className="w-full max-w-sm rounded-2xl border border-amber-400/50 bg-slate-900 p-6 text-center shadow-2xl"
      >
        <div className="text-5xl">{game.draw ? '🤝' : iWon ? '🏆' : '💀'}</div>
        <h2 className="mt-2 text-2xl font-black">{game.draw ? '무승부' : iWon ? '승리!' : '게임 종료'}</h2>
        <ol className="mt-4 space-y-1 text-left">
          {ranking.map((r) => (
            <li key={r.playerId} className="flex justify-between rounded-lg bg-slate-800/70 px-3 py-2">
              <span>
                {r.rank}위 · {r.nickname}
                {r.playerId === game.viewerId && <span className="ml-1 text-xs text-sky-300">(나)</span>}
              </span>
              {r.rank === 1 && !game.draw && <span>👑</span>}
            </li>
          ))}
        </ol>
        <button type="button" onClick={onClose} className="mt-5 w-full rounded-lg bg-amber-500 py-2 font-bold text-slate-950 hover:bg-amber-400">
          대기실로
        </button>
      </motion.div>
    </motion.div>
  )
}
