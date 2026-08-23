import type { CardSearchParams } from '../types/card'

export const normalizeWorkCardSearch = (keyword: string): Pick<CardSearchParams, 'id' | 'title'> => {
  const normalized = keyword.trim()
  const idMatch = normalized.match(/^#?(\d+)$/)
  if (idMatch) {
    const id = Number(idMatch[1])
    if (Number.isSafeInteger(id) && id > 0) return { id }
  }
  return normalized ? { title: normalized } : {}
}

export const cardSearchQueryKey = (params: CardSearchParams) => [
  'cards',
  'search',
  {
    ...params,
    tagIds: [...(params.tagIds ?? [])].sort((a, b) => a - b),
    growthStatuses: [...(params.growthStatuses ?? [])].sort(),
  },
] as const
