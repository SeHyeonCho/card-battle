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
  const barColor =
    hp > baseHp
      ? 'bg-gradient-to-b from-sky-300 to-sky-600'
      : ratio > 0.5
        ? 'bg-gradient-to-b from-[#7dff6a] to-[#1f9c24]'
        : ratio > 0.25
          ? 'bg-gradient-to-b from-amber-300 to-amber-600'
          : 'bg-gradient-to-b from-[#ff8a6a] to-[#c0241f]'
  // 틀 색: 대상으로 고를 수 있음(빨강) > 지금 차례(금색) > 나(주황) > 다른 사람(파랑)
  const frame = targetable ? 'frame-red' : isCurrent ? 'frame-gold' : isMe ? 'frame-orange' : 'frame-blue'
  const [scope, animate] = useAnimate<HTMLButtonElement>()

  useEffect(() => {
    if (shake > 0 && scope.current) {
      void animate(scope.current, { x: [0, -8, 8, -5, 5, 0] }, { duration: 0.35 })
    }
  }, [shake, animate, scope])

  // 연결 끊김 (FR-UI-02): 탈락한 사람은 표시하지 않는다
  const offline = player.connected === false && !player.eliminated

  return (
    <button
      ref={scope}
      type="button"
      disabled={!targetable}
      onClick={onClick}
      className={`frame ${frame} relative w-44 p-3 text-left text-sm transition ${player.eliminated ? 'opacity-40 grayscale' : offline ? 'opacity-70' : ''} ${
        targetable ? 'cursor-pointer hover:brightness-125' : 'cursor-default'
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="truncate font-bold text-white">
          {player.eliminated && '💀 '}
          {offline && <span title="연결 끊김">📡 </span>}
          {player.away && !player.eliminated && <span title="자리 비움 (연속 시간 초과)">💤 </span>}
          {player.nickname}
          {isMe && <span className="ml-1 text-xs text-orange-300">(나)</span>}
        </span>
        <span className="shrink-0 text-xs text-slate-300" title="손패 수 / 손패 한도">
          🂠 {player.handCount}
          {player.handLimit !== defaultHandLimit && <span className="text-amber-300">/{player.handLimit}</span>}
        </span>
      </div>
      <div className="mt-2 h-2.5 overflow-hidden border border-slate-600 bg-black">
        <motion.div className={`h-full ${barColor}`} animate={{ width: `${ratio * 100}%` }} transition={{ duration: 0.4 }} />
      </div>
      <div className="mt-1 text-right tabular-nums">
        <span className="text-lg font-bold text-white">{hp}</span>
        <span className="ml-1 text-xs text-slate-400">/ {baseHp}</span>
        {player.hpCap < baseHp && <span className="ml-1 text-[10px] text-rose-300">(최대 {player.hpCap})</span>}
      </div>

      {offline && (
        <div className="mt-1 border border-rose-500 bg-black/70 px-1.5 py-0.5 text-center text-[11px] text-rose-300" title="좌석은 그대로이고 차례가 오면 턴 시간대로 진행됩니다">
          📡 연결 끊김
        </div>
      )}

      {player.away && !player.eliminated && (
        <div className="mt-1 border border-slate-500 bg-black/60 px-1.5 py-0.5 text-center text-[11px] text-slate-200">💤 자리 비움</div>
      )}

      {(player.curse || player.statuses.length > 0) && (
        <div className="mt-1 flex flex-wrap gap-1 text-[11px]">
          {player.curse && (
            <span className="border border-rim-purple bg-purple-950/90 px-1.5 py-0.5 text-purple-100" title={`저주를 건 사람: ${nickOf(player.curse.casterId)}`}>
              😈 {cardName(player.curse.cardId)}
            </span>
          )}
          {player.statuses.map((st) => (
            <span key={st.status} className="border border-rim-blue bg-sky-950/90 px-1.5 py-0.5 text-sky-100" title={STATUS_TEXT[st.status]?.label ?? st.status}>
              {STATUS_TEXT[st.status]?.icon ?? '•'} {st.turnsLeft}
            </span>
          ))}
        </div>
      )}

      {!isMe && player.revealedHand && (
        <div className="mt-1 border border-rim-gold/60 bg-black/60 px-1.5 py-1 text-[11px] leading-tight text-amber-100" title="지켜보고 있다: 손패 공개">
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
              className={`absolute text-2xl font-bold ${f.delta < 0 ? 'neon-red' : 'text-sc-green text-outline'}`}
            >
              {f.delta > 0 ? `+${f.delta}` : f.delta}
            </motion.span>
          ))}
        </AnimatePresence>
      </div>
    </button>
  )
}
