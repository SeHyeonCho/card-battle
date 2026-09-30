import { useEffect, useState } from 'react'

/** 칸 수. 남은 시간만큼 칸이 켜진다 (스타 게이지 느낌) */
const SEGMENTS = 20

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
  const lit = Math.ceil(ratio * SEGMENTS)

  return (
    <div className="flex items-center gap-3">
      <div className="flex h-3.5 flex-1 gap-px border border-rim-steel bg-black p-px">
        {Array.from({ length: SEGMENTS }, (_, i) => (
          <i
            key={i}
            className={`flex-1 ${i < lit ? (urgent ? 'bg-rose-500 shadow-[0_0_6px_#ff2a2a]' : 'bg-sc-green') : urgent ? 'bg-rose-950' : 'bg-[#173a1a]'}`}
          />
        ))}
      </div>
      <span className={`w-10 text-right text-sm tabular-nums ${waiting ? 'text-sc-yellow' : urgent ? 'text-rose-400' : 'text-slate-200'}`}>
        {!active ? '-s' : waiting ? '대기' : `${Math.ceil(remainingMs / 1000)}s`}
      </span>
    </div>
  )
}
