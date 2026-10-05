import { motion } from 'motion/react'
import type { GameView } from '../game/reduce'

/** 게임 결과 (PRD FR-UI-07) */
export function ResultOverlay({ game, onClose }: { game: GameView; onClose: () => void }) {
  const ranking = [...(game.ranking ?? [])].sort((a, b) => a.rank - b.rank)
  const iWon = game.winnerIds.includes(game.viewerId)

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="fixed inset-0 z-40 flex items-center justify-center bg-bg/80 p-4 backdrop-blur-sm">
      <motion.div
        initial={{ scale: 0.7, y: 30 }}
        animate={{ scale: 1, y: 0 }}
        transition={{ type: 'spring', stiffness: 220, damping: 18 }}
        className="panel w-full max-w-sm p-7 text-center shadow-[0_30px_80px_-20px_#000]"
      >
        <div className="text-5xl">{game.draw ? '🤝' : iWon ? '🏆' : '💀'}</div>
        <h2 className={`mt-3 text-3xl font-extrabold tracking-tight ${iWon && !game.draw ? 'text-accent' : 'text-ink'}`}>{game.draw ? '무승부' : iWon ? '승리!' : '게임 종료'}</h2>
        <ol className="mt-5 space-y-1.5 text-left text-sm">
          {ranking.map((r) => (
            <li
              key={r.playerId}
              className={`flex items-center justify-between rounded-xl px-3.5 py-2.5 ${r.rank === 1 && !game.draw ? 'bg-accent font-bold text-on-accent' : 'bg-raised'}`}
            >
              <span>
                <b className="mr-2 tabular-nums">{r.rank}</b>
                {r.nickname}
                {r.playerId === game.viewerId && <span className="ml-1.5 text-xs opacity-70">나</span>}
              </span>
              {r.rank === 1 && !game.draw && <span>👑</span>}
            </li>
          ))}
        </ol>
        <button type="button" onClick={onClose} className="btn btn-primary mt-6 w-full py-3">
          대기실로
        </button>
      </motion.div>
    </motion.div>
  )
}
