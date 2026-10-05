import type { CSSProperties } from 'react'
import { useApp } from '../store/app'
import type { CardInfo } from '../types'

/** 카드 종류는 색 점 하나로 표시한다 (index.css 의 --color-cat-*) */
const CATEGORY: Record<CardInfo['category'], { label: string; color: string }> = {
  ATTACK: { label: '공격', color: 'var(--color-cat-attack)' },
  SUPPORT: { label: '보조', color: 'var(--color-cat-support)' },
  BENEFIT: { label: '이득', color: 'var(--color-cat-benefit)' },
  CURSE: { label: '저주', color: 'var(--color-cat-curse)' },
  MISC: { label: '기타', color: 'var(--color-cat-misc)' },
}

interface Props {
  card: CardInfo
  /** 실제로 적용된 공격력 (랜덤 공격력 카드가 필드에 놓였을 때) */
  attack?: number
  size?: 'hand' | 'field'
}

/**
 * 카드 앞면: 그림 → 종류 점 + 큰 숫자 → 이름 → 설명.
 * 카드팩 그림(96×128)은 이름·공격력이 그림 안에 찍혀 있어서 가운데 그림 칸(8,8~88,88)만 잘라 쓰고 글자는 직접 그린다.
 * 손패 카드는 설명을 숨기고 그림에 마우스를 올리면 보여 준다.
 */
export function CardFace({ card, attack, size = 'hand' }: Props) {
  const image = useApp((s) => s.cardImages[card.id])
  const category = CATEGORY[card.category]
  const big = size === 'field'
  const power =
    attack ?? card.attack ?? (card.attackMin !== null && card.attackMax !== null ? `${card.attackMin}~${card.attackMax}` : null)
  // 주사위 폭탄처럼 무작위거나 슈퍼파워로 바뀐 공격력은 포인트 색으로
  const changed = attack !== undefined && attack !== card.attack
  // 설명 앞의 "공격력 N." 은 큰 숫자와 겹치므로 뺀다
  const description = card.description.replace(/^공격력 [\d~]+(\s무작위)?\.?\s*/, '') || '효과 없음'

  return (
    <div
      className={`group relative flex flex-col overflow-hidden bg-raised text-left ${big ? 'w-44 rounded-[20px] sm:w-50' : 'w-28 rounded-2xl sm:w-37.5'}`}
      style={{ '--c': category.color } as CSSProperties}
    >
      <div
        className={`relative aspect-square overflow-hidden ${big ? 'mx-2.5 mt-2.5 rounded-[14px]' : 'mx-2 mt-2 rounded-[11px]'}`}
        style={{ background: 'color-mix(in srgb, var(--c) 16%, var(--color-raised))' }}
      >
        {image ? (
          <img src={image} alt="" draggable={false} className="absolute top-[-10%] left-[-10%] w-[120%] max-w-none select-none" />
        ) : (
          <span className="absolute inset-0 grid place-items-center text-5xl font-extrabold text-[var(--c)] opacity-70">{card.name.slice(0, 1)}</span>
        )}
        {!big && (
          <div className="pointer-events-none absolute inset-0 flex flex-col justify-center bg-bg/92 p-2.5 text-[11.5px] leading-snug text-ink opacity-0 transition-opacity group-hover:opacity-100">
            {description}
            {card.flavor && <span className="mt-1 text-[11px] text-muted">{card.flavor}</span>}
          </div>
        )}
      </div>

      <div className={big ? 'px-3.5 pt-3 pb-3.5' : 'px-2.5 pt-2 pb-2.5'}>
        <div className={`flex items-end justify-between ${big ? 'min-h-9' : 'min-h-7'}`}>
          <span className="flex items-center gap-1.5 text-xs font-semibold text-[var(--c)]">
            <i className="size-2 rounded-full bg-[var(--c)]" />
            {category.label}
          </span>
          {power !== null && (
            <span className="flex items-baseline gap-1">
              <b className={`num ${changed ? 'text-accent' : 'text-ink'} ${big ? 'text-4xl' : 'text-2xl'}`}>{power}</b>
              <span className="text-[10px] font-semibold text-muted">ATK</span>
            </span>
          )}
        </div>
        <div className={`truncate font-bold text-ink ${big ? 'mt-1.5 text-[17px]' : 'mt-0.5 text-sm'}`}>{card.name}</div>
        {big && <div className="mt-1 line-clamp-3 text-[12.5px] leading-snug text-muted">{description}</div>}
      </div>
    </div>
  )
}
