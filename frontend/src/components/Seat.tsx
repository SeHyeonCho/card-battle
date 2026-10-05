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

/**
 * 플레이어 좌석: 이름 첫 글자 원, 닉네임, 가는 체력 바, 체력·손패 수, 상태 표시.
 * 맞으면 흔들리고 숫자가 떠오른다. 지금 차례면 연두 테두리, 대상으로 고를 수 있으면 빨간 테두리
 */
export function Seat({ player, baseHp, isCurrent, isMe, floaters, shake, targetable, onClick, defaultHandLimit, cardName, nickOf }: Props) {
  const hp = Math.max(0, player.hp)
  const ratio = Math.min(1, hp / Math.max(1, baseHp))
  const barColor = hp > baseHp ? 'bg-cat-support' : ratio > 0.5 ? 'bg-ok' : ratio > 0.25 ? 'bg-warn' : 'bg-danger'
  const ring = targetable
    ? 'bg-raised shadow-[inset_0_0_0_1.5px_var(--color-danger)]'
    : isCurrent
      ? 'bg-raised shadow-[inset_0_0_0_1.5px_var(--color-accent)]'
      : ''
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
      className={`relative flex w-full min-w-44 items-start gap-3 rounded-2xl p-2.5 text-left transition ${ring} ${
        player.eliminated ? 'opacity-35 grayscale' : offline ? 'opacity-60' : ''
      } ${targetable ? 'cursor-pointer hover:brightness-125' : 'cursor-default'}`}
    >
      <span className={`grid size-10 shrink-0 place-items-center rounded-full text-sm font-bold ${isMe ? 'bg-accent text-on-accent' : 'bg-line text-ink'}`}>
        {player.eliminated ? '💀' : player.nickname.slice(0, 1)}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5 text-sm font-semibold text-ink">
          <span className="truncate">{player.nickname}</span>
          {isMe && <span className="text-[11px] font-semibold text-accent">나</span>}
          {isCurrent && !player.eliminated && <span className="text-[11px] font-semibold text-accent">차례</span>}
        </span>
        <span className="mt-1.5 block h-1 overflow-hidden rounded-full bg-line">
          <motion.span className={`block h-full rounded-full ${barColor}`} animate={{ width: `${ratio * 100}%` }} transition={{ duration: 0.4 }} />
        </span>
        <span className="mt-1.5 block text-xs text-muted tabular-nums">
          <b className="font-semibold text-ink">{hp}</b> HP
          {player.hpCap < baseHp && <span className="text-danger"> (최대 {player.hpCap})</span>}
          <span title="손패 수 / 손패 한도">
            {' '}
            · 손패 {player.handCount}
            {player.handLimit !== defaultHandLimit && <span className="text-warn">/{player.handLimit}</span>}
          </span>
        </span>

        {(offline || (player.away && !player.eliminated) || player.curse || player.statuses.length > 0) && (
          <span className="mt-1.5 flex flex-wrap gap-1">
            {offline && (
              <span className="chip chip-danger" title="좌석은 그대로이고 차례가 오면 턴 시간대로 진행됩니다">
                연결 끊김
              </span>
            )}
            {player.away && !player.eliminated && (
              <span className="chip" title="자리 비움 (연속 시간 초과)">
                💤 자리 비움
              </span>
            )}
            {player.curse && (
              <span className="chip chip-curse" title={`저주를 건 사람: ${nickOf(player.curse.casterId)}`}>
                {cardName(player.curse.cardId)}
              </span>
            )}
            {player.statuses.map((st) => (
              <span key={st.status} className="chip" title={STATUS_TEXT[st.status]?.label ?? st.status}>
                {STATUS_TEXT[st.status]?.icon ?? '•'} {st.turnsLeft}
              </span>
            ))}
          </span>
        )}

        {!isMe && player.revealedHand && (
          <span className="mt-1.5 block rounded-lg bg-line px-2 py-1 text-[11px] leading-snug text-warn" title="지켜보고 있다: 손패 공개">
            👁 {player.revealedHand.map((c) => cardName(c.cardId)).join(', ')}
          </span>
        )}
      </span>

      <span className="pointer-events-none absolute inset-x-0 -top-2 flex justify-center">
        <AnimatePresence>
          {floaters.map((f) => (
            <motion.span
              key={f.id}
              initial={{ y: 0, opacity: 1, scale: 1.2 }}
              animate={{ y: -44, opacity: 0, scale: 1 }}
              transition={{ duration: 1.1, ease: 'easeOut' }}
              className={`num absolute text-3xl ${f.delta < 0 ? 'text-danger' : 'text-ok'}`}
            >
              {f.delta > 0 ? `+${f.delta}` : f.delta}
            </motion.span>
          ))}
        </AnimatePresence>
      </span>
    </button>
  )
}
