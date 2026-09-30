import { useState } from 'react'
import { bgm } from '../audio/bgm'

/**
 * 배경음악 켜기/끄기. 설정은 브라우저에 기억한다.
 * floating: 첫 화면·대기실처럼 메뉴줄이 없는 화면에서 오른쪽 위에 띄운다 (게임 화면은 메뉴줄 안에 둔다)
 */
export function BgmToggle({ floating = false }: { floating?: boolean }) {
  const [on, setOn] = useState(bgm.enabled())
  return (
    <button
      type="button"
      className={`btn px-2 py-0.5 text-xs ${floating ? 'fixed top-3 right-3 z-30 py-1' : ''}`}
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
