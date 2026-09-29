import { useEffect, useState } from 'react'

/** 남은 턴 시간 막대 (PRD FR-UI-03). 서버가 준 마감 시각 기준이라 새로고침해도 맞다 */
export function TurnTimer({ deadline, totalSeconds, active }: { deadline: number; totalSeconds: number; active: boolean }) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!active) return
    const id = setInterval(() => setNow(Date.now()), 200)
    return () => clearInterval(id)
  }, [active])

  const remainingMs = Math.max(0, deadline - now)
  const ratio = active ? Math.min(1, remainingMs / (totalSeconds * 1000)) : 0
  const urgent = remainingMs < 5000

  return (
    <div className="flex items-center gap-3">
      <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-800">
        <div
          className={`h-full transition-[width] duration-200 ease-linear ${urgent ? 'bg-rose-500' : 'bg-amber-400'}`}
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
      <span className={`w-10 text-right text-sm tabular-nums ${urgent ? 'text-rose-400' : 'text-slate-300'}`}>
        {active ? Math.ceil(remainingMs / 1000) : '-'}s
      </span>
    </div>
  )
}
