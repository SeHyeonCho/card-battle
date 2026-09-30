import { useState } from 'react'
import { bgm } from '../audio/bgm'

/** 배경음악 켜기/끄기 (화면 오른쪽 위). 설정은 브라우저에 기억한다 */
export function BgmToggle() {
  const [on, setOn] = useState(bgm.enabled())
  return (
    <button
      type="button"
      className="btn fixed top-3 right-3 z-30 px-2 py-1 text-xs"
      title="배경음악 켜기/끄기"
      onClick={() => {
        bgm.setEnabled(!on)
        setOn(!on)
      }}
    >
      {on ? '♪ BGM 켜짐' : '♪ BGM 꺼짐'}
    </button>
  )
}
