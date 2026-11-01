import request from './request'
import type { GoalAssociation, GoalBoardPage, GoalDefinition, GoalEvidence, GoalEvidencePage, GoalKind, GoalMetric, GoalOption, GoalPreference, GoalQuery, GoalSource, GoalSourceView, GoalView } from '../../types/goal'
interface GoalPage { content: GoalView[]; totalElements: number; totalPages: number }
export const goalApi = {
  list: (params: GoalQuery = {}) => request.get<never, GoalPage>('/goals', { params, paramsSerializer: { indexes: null } }),
  detail: (id: number) => request.get<never, GoalView>(`/goals/${id}`),
  sources: (id: number) => request.get<never, GoalSourceView[]>(`/goals/${id}/sources`),
  overview: () => request.get<never, GoalView[]>('/goals/overview'),
  save: (id: number | null, definition: GoalDefinition, version: number, reason: string, requestId: string) => id
    ? request.put<never, GoalView>(`/goals/${id}`, { definition, version, reason, requestId })
    : request.post<never, GoalView>('/goals', { definition, version, reason, requestId }),
  delete: (id: number, version: number) => request.delete<never, void>(`/goals/${id}`, { params: { version } }),
  preview: (definition: Pick<GoalDefinition, 'sources' | 'allTags'>) => request.post<never, { cardCount: number }>('/goals/tracking-preview', definition),
  options: (kind: GoalKind, keyword: string, page: number, experimentId?: number) => request.get<never, GoalOption[]>('/goals/options', { params: { kind, keyword, page, experimentId } }),
  pinOrder: (goal: GoalView, adjacent: GoalView, direction: 'UP' | 'DOWN') => request.post(`/goals/${goal.id}/pin-order`, { direction, version: goal.version, adjacentId: adjacent.id, adjacentVersion: adjacent.version }),
  revisions: (id: number) => request.get<never, Array<{ revision: number; definition: GoalDefinition; reason: string; effectiveAt: string }>>(`/goals/${id}/revisions`),
  metrics: (id: number) => request.get<never, GoalMetric[]>(`/goals/${id}/metrics`),
  evidence: (id: number, metric?: string, page = 0) => request.get<never, GoalEvidencePage>(`/goals/${id}/evidence`, { params: { metric, page } }),
  saveEvidence: (id: number, evidenceId: string, excluded: boolean, reason: string, version: number) => request.put<never, GoalEvidence>(`/goals/${id}/evidence/${evidenceId}`, { excluded, reason, version }),
  preference: () => request.get<never, GoalPreference>('/goal-board/preferences'),
  savePreference: (selection: GoalPreference) => request.put<never, GoalPreference>('/goal-board/preferences', selection),
  board: (selection: GoalPreference, seed: string, cursor?: string, pageIds?: number[]) => request.post<never, GoalBoardPage>('/goal-board/cards', { selection, seed, cursor, pageIds }),
  associations: (kind: GoalKind, id: number) => request.get<never, GoalAssociation[]>('/goal-associations', { params: { kind, id } }),
  counts: (objects: GoalSource[]) => request.post<never, Record<string, number>>('/goal-associations/summary', { objects }),
}
