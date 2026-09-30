import { useApp } from '../store/app'
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

/** 카드 앞면. 카드팩에 그림이 있으면 그림으로, 없으면 글자로 그린다 */
export function CardFace({ card, attack, size = 'hand' }: Props) {
  const image = useApp((s) => s.cardImages[card.id])
  return image ? <ImageFace card={card} attack={attack} size={size} image={image} /> : <TextFace card={card} attack={attack} size={size} />
}

/**
 * 그림 카드 (96×128 비율). 그림에는 이름과 짧은 설명만 있으므로
 * 마우스를 올리면 그림 칸 위에 전체 효과 설명을 띄운다.
 */
function ImageFace({ card, attack, size, image }: Props & { image: string }) {
  const big = size === 'field'
  // 주사위 폭탄처럼 공격력이 무작위거나 슈퍼파워로 바뀐 경우 실제 공격력을 따로 표시
  const shownAttack = attack !== undefined && attack !== card.attack ? attack : null
  return (
    <div className={`group relative overflow-hidden rounded-lg shadow-lg shadow-black/40 ${big ? 'h-56 w-42' : 'h-44 w-33'}`}>
      <img src={image} alt={card.name} draggable={false} className="h-full w-full select-none" />
      {shownAttack !== null && (
        <span
          className={`absolute right-1 top-1 rounded-md bg-black/80 px-1.5 font-black leading-tight text-amber-200 ${big ? 'text-2xl' : 'text-lg'}`}
        >
          {shownAttack}
        </span>
      )}
      <div
        className={`pointer-events-none absolute inset-x-[8%] top-[6%] flex h-[62%] flex-col justify-center rounded bg-black/85 p-2 text-left leading-snug text-white opacity-0 transition-opacity group-hover:opacity-100 ${
          big ? 'text-xs' : 'text-[11px]'
        }`}
      >
        {card.description}
        {card.flavor && <span className="mt-1 text-[10px] italic text-white/60">{card.flavor}</span>}
      </div>
    </div>
  )
}

function TextFace({ card, attack, size }: Props) {
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
