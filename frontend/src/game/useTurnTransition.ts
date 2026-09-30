import { useEffect, useState } from 'react'

/**
 * 차례 전환 중인지 (PRD FR-GAME-10). activeAt(이 브라우저의 Date.now() 기준, GameView.turnActiveAt)이
 * 바뀔 때마다 끝나는 시각에 타이머를 걸어, 전환이 끝나면 다시 그리게 한다
 */
export function useTurnTransition(activeAt: number): boolean {
  const [finishedFor, setFinishedFor] = useState(0)
  useEffect(() => {
    const id = setTimeout(() => setFinishedFor(activeAt), Math.max(0, activeAt - Date.now()))
    return () => clearTimeout(id)
  }, [activeAt])
  return finishedFor !== activeAt
}
