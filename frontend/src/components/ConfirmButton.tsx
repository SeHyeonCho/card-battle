import { useEffect, useState } from 'react'

/** 다시 누르지 않으면 이 시간 뒤 원래 버튼으로 돌아간다 */
const ARM_MS = 3000

interface Props {
  label: string
  /** 한 번 누른 뒤 보이는 문구 (예: "정말 내보내기?") */
  confirmLabel: string
  onConfirm: () => void
  className?: string
  title?: string
}

/**
 * 두 번 눌러야 실행되는 버튼 (강퇴처럼 되돌릴 수 없는 행동).
 * 브라우저 confirm()은 일부 브라우저·앱 내장 브라우저에서 막혀 항상 "취소"가 되므로 쓰지 않는다.
 */
export function ConfirmButton({ label, confirmLabel, onConfirm, className = '', title }: Props) {
  const [armed, setArmed] = useState(false)

  useEffect(() => {
    if (!armed) return
    const id = setTimeout(() => setArmed(false), ARM_MS)
    return () => clearTimeout(id)
  }, [armed])

  return (
    <button
      type="button"
      title={title}
      className={`${className} ${armed ? 'animate-pulse' : ''}`}
      onClick={() => {
        if (armed) {
          setArmed(false)
          onConfirm()
        } else {
          setArmed(true)
        }
      }}
    >
      {armed ? confirmLabel : label}
    </button>
  )
}
