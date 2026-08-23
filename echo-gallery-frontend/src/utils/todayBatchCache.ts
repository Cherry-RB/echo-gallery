import type { CardDto, TodayBatchResponse } from '../types/card'

export const todayBatchQueryKey = ['today', 'batch'] as const

export function updateTodayCard(batch: TodayBatchResponse | undefined, id: string | number, patch: Partial<CardDto>) {
  if (!batch) return batch
  return {
    ...batch,
    cards: batch.cards.map(card => String(card.id) === String(id) ? { ...card, ...patch } : card),
  }
}

export function removeTodayCard(batch: TodayBatchResponse | undefined, id: string | number) {
  if (!batch) return batch
  return {
    ...batch,
    cards: batch.cards.filter(card => String(card.id) !== String(id)),
  }
}

export function resolveNextBatch(current: TodayBatchResponse, next: TodayBatchResponse) {
  if (next.cards.length === 0) {
    return { batch: current, noMoreCards: true }
  }
  return { batch: next, noMoreCards: false }
}
