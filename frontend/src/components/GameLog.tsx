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
    <div className="panel flex h-full flex-col p-4">
      <div className="label mb-2">게임 로그</div>
      <div className="flex-1 overflow-y-auto text-[13px] leading-snug">
        {log.map((entry, i) => {
          // 문구로 종류를 가려 점 색만 바꾼다 (피해·탈락은 빨강, 회복은 초록)
          const tone = /받음|탈락|피해/.test(entry.text) ? 'bg-danger' : /회복|승리/.test(entry.text) ? 'bg-ok' : 'bg-line'
          return (
            <div key={entry.id} className={`flex gap-2 border-b border-raised py-1.5 ${i === log.length - 1 ? 'text-ink' : 'text-muted'}`}>
              <i className={`mt-1.5 size-1.5 shrink-0 rounded-full ${tone}`} />
              <span>{entry.text}</span>
            </div>
          )
        })}
        <div ref={bottom} />
      </div>
    </div>
  )
}
