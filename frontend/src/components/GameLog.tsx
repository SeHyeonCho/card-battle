import { useEffect, useRef } from 'react'
import { useApp } from '../store/app'

/** 게임 로그 (PRD FR-UI-04). 처음 하는 플레이어도 방금 무슨 일이 있었는지 볼 수 있게 */
export function GameLog() {
  const log = useApp((s) => s.log)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth' })
  }, [log.length])

  return (
    <div className="frame flex h-full flex-col bg-black/80">
      <div className="border-b border-rim-steel px-3 py-2 text-xs text-sc-yellow">[게임 로그]</div>
      <div className="flex-1 space-y-1 overflow-y-auto px-3 py-2 text-[13px] leading-relaxed">
        {log.map((entry, i) => (
          <div key={entry.id} className={i === log.length - 1 ? 'text-white' : 'text-slate-400'}>
            {entry.text}
          </div>
        ))}
        <div ref={bottom} />
      </div>
    </div>
  )
}
