import { useEffect, useRef } from 'react'
import { useApp } from '../store/app'

/** 게임 로그 (PRD FR-UI-04). 처음 하는 친구도 방금 무슨 일이 있었는지 볼 수 있게 */
export function GameLog() {
  const log = useApp((s) => s.log)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [log.length])

  return (
    <div className="flex h-full flex-col rounded-xl border border-slate-800 bg-slate-900/60">
      <div className="border-b border-slate-800 px-3 py-2 text-xs font-semibold tracking-wide text-slate-400">게임 로그</div>
      <div className="flex-1 space-y-1 overflow-y-auto px-3 py-2 text-sm">
        {log.map((entry) => (
          <div key={entry.id} className="text-slate-300">
            {entry.text}
          </div>
        ))}
        <div ref={bottom} />
      </div>
    </div>
  )
}
