import { AnimatePresence, motion, useAnimate } from 'motion/react'
import { useEffect } from 'react'
import type { Floater } from '../store/app'
import type { PlayerView } from '../types'

interface Props {
  player: PlayerView
  /** 체력 바 100% 기준 (시작 체력). 이보다 많으면 초과 회복으로 표시한다 */
  baseHp: number
  isCurrent: boolean
  isMe: boolean
  floaters: Floater[]
  shake: number
  targetable?: boolean
  onClick?: () => void
}

/** 플레이어 좌석: 닉네임, 체력 바, 손패 수. 맞으면 흔들리고 숫자가 떠오른다 */
export function Seat({ player, baseHp, isCurrent, isMe, floaters, shake, targetable, onClick }: Props) {
  const hp = Math.max(0, player.hp)
  const ratio = Math.min(1, hp / Math.max(1, baseHp))
  const barColor = hp > baseHp ? 'bg-sky-400' : ratio > 0.5 ? 'bg-emerald-400' : ratio > 0.25 ? 'bg-amber-400' : 'bg-rose-500'
  const [scope, animate] = useAnimate<HTMLButtonElement>()

  useEffect(() => {
    if (shake > 0 && scope.current) {
      void animate(scope.current, { x: [0, -8, 8, -5, 5, 0] }, { duration: 0.35 })
    }
  }, [shake, animate, scope])

  return (
    <button
      ref={scope}
      type="button"
      disabled={!targetable}
      onClick={onClick}
      className={`relative w-40 rounded-xl border bg-slate-900/80 p-3 text-left transition ${
        isCurrent ? 'border-amber-400 shadow-[0_0_24px_rgba(251,191,36,0.35)]' : 'border-slate-700'
      } ${player.eliminated ? 'opacity-40 grayscale' : ''} ${
        targetable ? 'cursor-pointer ring-2 ring-rose-400 hover:bg-rose-950/60' : 'cursor-default'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="truncate font-semibold">
          {player.eliminated && '💀 '}
          {player.nickname}
          {isMe && <span className="ml-1 text-xs text-sky-300">(나)</span>}
        </span>
        <span className="shrink-0 text-xs text-slate-400">🂠 {player.handCount}</span>
      </div>
      <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-700">
        <motion.div className={`h-full ${barColor}`} animate={{ width: `${ratio * 100}%` }} transition={{ duration: 0.4 }} />
      </div>
      <div className="mt-1 text-right tabular-nums">
        <span className="text-lg font-bold text-slate-100">{hp}</span>
        <span className="ml-1 text-xs text-slate-500">HP</span>
      </div>

      <div className="pointer-events-none absolute inset-x-0 -top-2 flex justify-center">
        <AnimatePresence>
          {floaters.map((f) => (
            <motion.span
              key={f.id}
              initial={{ y: 0, opacity: 1, scale: 1.2 }}
              animate={{ y: -44, opacity: 0, scale: 1 }}
              transition={{ duration: 1.1, ease: 'easeOut' }}
              className={`absolute text-xl font-black drop-shadow ${f.delta < 0 ? 'text-rose-400' : 'text-emerald-300'}`}
            >
              {f.delta > 0 ? `+${f.delta}` : f.delta}
            </motion.span>
          ))}
        </AnimatePresence>
      </div>
    </button>
  )
}
