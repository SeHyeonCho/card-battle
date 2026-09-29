import type { CardInfo } from '../types'

const STYLE: Record<CardInfo['category'], { label: string; className: string }> = {
  ATTACK: { label: '공격', className: 'from-rose-700 to-orange-600 border-rose-400/70' },
  SUPPORT: { label: '보조', className: 'from-sky-700 to-indigo-700 border-sky-400/70' },
  BENEFIT: { label: '이득', className: 'from-emerald-700 to-teal-700 border-emerald-400/70' },
  CURSE: { label: '저주', className: 'from-fuchsia-800 to-purple-800 border-fuchsia-400/70' },
  MISC: { label: '기타', className: 'from-slate-600 to-slate-700 border-slate-400/70' },
}

interface Props {
  card: CardInfo
  /** 실제로 적용된 공격력 (랜덤 공격력 카드가 필드에 놓였을 때) */
  attack?: number
  size?: 'hand' | 'field'
}

/** 카드 앞면. 이미지 없이 텍스트로만 그린다 (원작 이미지 미사용 원칙) */
export function CardFace({ card, attack, size = 'hand' }: Props) {
  const style = STYLE[card.category]
  const big = size === 'field'
  const power =
    attack ?? card.attack ?? (card.attackMin !== null && card.attackMax !== null ? `${card.attackMin}~${card.attackMax}` : null)

  return (
    <div
      className={`relative flex flex-col rounded-xl border-2 bg-gradient-to-br p-2 text-left shadow-lg shadow-black/40 ${style.className} ${
        big ? 'h-56 w-40' : 'h-44 w-32'
      }`}
    >
      <div className="flex items-start justify-between">
        <span className="rounded bg-black/30 px-1.5 py-0.5 text-[10px] font-semibold tracking-wide">{style.label}</span>
        {power !== null && (
          <span className={`font-black leading-none text-amber-200 drop-shadow ${big ? 'text-4xl' : 'text-2xl'}`}>{power}</span>
        )}
      </div>
      <div className={`mt-2 font-bold leading-tight ${big ? 'text-lg' : 'text-sm'}`}>{card.name}</div>
      <div className={`mt-auto leading-snug text-white/90 ${big ? 'text-xs' : 'text-[11px]'}`}>{card.description}</div>
      {card.flavor && <div className="mt-1 text-[10px] italic text-white/60">{card.flavor}</div>}
    </div>
  )
}
