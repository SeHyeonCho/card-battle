import { useEffect, useState } from 'react'

/**
 * 남은 턴 시간 막대 (PRD FR-UI-03). 서버가 준 마감 시각 기준이라 새로고침해도 맞다.
 * 차례 전환 중(waiting)에는 시간이 줄지 않으므로 가득 찬 채로 "대기"를 보여 준다 (FR-GAME-10)
 */
export function TurnTimer({ deadline, totalSeconds, active, waiting }: { deadline: number; totalSeconds: number; active: boolean; waiting: boolean }) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!active) return
    const id = setInterval(() => setNow(Date.now()), 200)
    return () => clearInterval(id)
  }, [active])

  const remainingMs = Math.min(totalSeconds * 1000, Math.max(0, deadline - now))
  const ratio = active ? Math.min(1, remainingMs / (totalSeconds * 1000)) : 0
  const urgent = remainingMs < 5000

  return (
    <div className="flex items-center gap-3">
      <div className="h-[3px] flex-1 overflow-hidden rounded-full bg-raised">
        <div
          className={`h-full rounded-full transition-[width] duration-200 ease-linear ${urgent ? 'bg-danger' : 'bg-accent'}`}
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
      <span className={`w-9 text-right text-xs font-semibold tabular-nums ${waiting ? 'text-muted' : urgent ? 'text-danger' : 'text-ink'}`}>
        {!active ? '-' : waiting ? '대기' : `${Math.ceil(remainingMs / 1000)}초`}
      </span>
    </div>
  )
}
