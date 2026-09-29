import type { CardFilter } from '../types'

/** 서버가 보낸 규칙 데이터를 사람이 읽는 문구로 바꾼다 (화면 표시 전용, 판정에는 쓰지 않는다) */

const CATEGORY: Record<string, string> = { ATTACK: '공격', SUPPORT: '보조', BENEFIT: '이득', CURSE: '저주', MISC: '기타' }
const TAG: Record<string, string> = { FIRE: '화염', ELECTRIC: '전기' }

const list = (v: string | string[] | undefined) => (v === undefined ? [] : Array.isArray(v) ? v : [v])

/** 예: { category: 'ATTACK', attackGte: 30 } → "공격력 30 이상 공격 카드" */
export function describeFilter(filter: CardFilter | null | undefined): string {
  if (!filter) return '모든 카드'
  const parts: string[] = []
  if (filter.tags?.length) parts.push(`${filter.tags.map((t) => TAG[t] ?? t).join('·')} 속성`)
  if (filter.attackGte !== undefined) parts.push(`공격력 ${filter.attackGte} 이상`)
  if (filter.attackLte !== undefined) parts.push(`공격력 ${filter.attackLte} 이하`)
  if (filter.hasEffects === true) parts.push('효과가 있는')
  if (Array.isArray(filter.hasEffects) && filter.hasEffects.includes('HEAL')) parts.push('회복 효과가 있는')
  const categories = list(filter.category).map((c) => CATEGORY[c] ?? c)
  parts.push(categories.length ? `${categories.join('·')} 카드` : '카드')
  return parts.join(' ')
}

export const STATUS_TEXT: Record<string, { icon: string; label: string }> = {
  UNTARGETABLE: { icon: '🛡️', label: '보호막' },
  REGEN: { icon: '🍎', label: '재생' },
  NEXT_ATTACK_BONUS: { icon: '💉', label: '다음 공격 강화' },
}

export const EXTRA_MODE_TEXT: Record<string, string> = {
  ANY: '아무 카드나 한 장 더',
  SUM: '공격 카드 한 장 더 (공격력 합산)',
  DOUBLE: '한 장 더 (공격 카드면 공격력 2배)',
  HEAL_SELF: '공격 카드 한 장 더 (공격력만큼 회복)',
}
