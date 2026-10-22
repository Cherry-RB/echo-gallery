import request from './request'
import type { WorkDetail, WorkInfo, WorkPage, WorkSource, WorkSummary, WorkUsage, ExperimentOption } from '../../types/work'
import type { TreasureContent } from '../../types/treasure'
const root = '/creative-works'
export const workApi = {
  list: (params: Record<string, unknown> = {}) => request.get<never, WorkPage<WorkSummary>>(root, { params }),
  detail: (id: number) => request.get<never, WorkDetail>(`${root}/${id}`),
  fromTreasure: (id: number) => request.get<never, WorkDetail | null>(`${root}/treasure/${id}`),
  experiments: () => request.get<never, ExperimentOption[]>(`${root}/experiments`),
  create: (data: WorkInfo & { sources?: Array<{ cardId?: number; recordId?: number }> }) => request.post<never, WorkDetail>(root, data),
  info: (id: number, data: WorkInfo) => request.put<never, WorkDetail>(`${root}/${id}`, data),
  body: (id: number, body: string, expectedVersion: number) => request.put<never, { body: string; bodyVersion: number; editedAt: string }>(`${root}/${id}/body`, { body, expectedVersion }),
  update: (id: number, section: string, data?: unknown) => request.put<never, WorkDetail>(`${root}/${id}/${section}`, data),
  delete: (id: number) => request.delete(`${root}/${id}`),
  sources: (id: number) => request.get<never, WorkSource[]>(`${root}/${id}/sources`),
  addSources: (id: number, sources: Array<{ cardId?: number | null; recordId?: number | null }>) => request.post<never, WorkSource[]>(`${root}/${id}/sources`, { sources: sources.map(({ cardId, recordId }) => ({ cardId, recordId })) }),
  removeSource: (id: number, sourceId: number) => request.delete(`${root}/${id}/sources/${sourceId}`),
  candidates: (params: Record<string, unknown>) => request.get<never, WorkPage<WorkSource>>(`${root}/candidates`, { params }),
  usages: (cardIds: number[], recordIds: number[]) => request.get<never, WorkUsage[]>(`${root}/usages`, { params: { cardIds: cardIds.join(','), recordIds: recordIds.join(',') } }),
  createTreasure: (id: number, data: TreasureContent) => request.post<never, WorkDetail>(`${root}/${id}/treasure`, data),
  unlink: (id: number) => request.delete<never, WorkDetail>(`${root}/${id}/treasure`),
}
