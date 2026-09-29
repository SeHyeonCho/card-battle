import { AnimatePresence, motion, useAnimate } from 'motion/react'
import { useEffect } from 'react'
import { STATUS_TEXT } from '../game/describe'
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
  /** 손패 한도 기본값. 다르면 한도를 함께 보여준다 (카드부자·햄보칼수업서) */
  defaultHandLimit: number
  cardName: (cardId: string | null) => string
  nickOf: (playerId: string) => string
}

/** 플레이어 좌석: 닉네임, 체력 바, 손패 수. 맞으면 흔들리고 숫자가 떠오른다 */
export function Seat({ player, baseHp, isCurrent, isMe, floaters, shake, targetable, onClick, defaultHandLimit, cardName, nickOf }: Props) {
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
        <span className="shrink-0 text-xs text-slate-400" title="손패 수 / 손패 한도">
          🂠 {player.handCount}
          {player.handLimit !== defaultHandLimit && <span className="text-amber-300">/{player.handLimit}</span>}
        </span>
      </div>
      <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-700">
        <motion.div className={`h-full ${barColor}`} animate={{ width: `${ratio * 100}%` }} transition={{ duration: 0.4 }} />
      </div>
      <div className="mt-1 text-right tabular-nums">
        <span className="text-lg font-bold text-slate-100">{hp}</span>
        <span className="ml-1 text-xs text-slate-500">HP</span>
        {player.hpCap < baseHp && <span className="ml-1 text-[10px] text-rose-300">(최대 {player.hpCap})</span>}
      </div>

      {(player.curse || player.statuses.length > 0) && (
        <div className="mt-1 flex flex-wrap gap-1 text-[11px]">
          {player.curse && (
            <span className="rounded bg-fuchsia-900/80 px-1.5 py-0.5 text-fuchsia-100" title={`저주를 건 사람: ${nickOf(player.curse.casterId)}`}>
              😈 {cardName(player.curse.cardId)}
            </span>
          )}
          {player.statuses.map((st) => (
            <span key={st.status} className="rounded bg-sky-900/80 px-1.5 py-0.5 text-sky-100" title={STATUS_TEXT[st.status]?.label ?? st.status}>
              {STATUS_TEXT[st.status]?.icon ?? '•'} {st.turnsLeft}
            </span>
          ))}
        </div>
      )}

      {!isMe && player.revealedHand && (
        <div className="mt-1 rounded bg-black/40 px-1.5 py-1 text-[10px] leading-tight text-amber-100" title="지켜보고 있다: 손패 공개">
          👁 {player.revealedHand.map((c) => cardName(c.cardId)).join(', ')}
        </div>
      )}

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
