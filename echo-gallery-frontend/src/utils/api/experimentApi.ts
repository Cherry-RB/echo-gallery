import type { CardDto, PageResponse } from '../../types/card'
import type {
  ExperimentExplorationCardRequest,
  ExperimentExplorationDto,
  ExperimentDto,
  ExperimentRequest,
  ExplorationThreadDto,
  ExplorationThreadSummaryDto,
  ExplorationThreadRequest,
  ExplorationThreadImportItem,
  ExplorationThreadStatus,
  ExplorationThreadCardRole,
  CardExplorationThreadDto,
} from '../../types/experiment'
import request from './request'

export const experimentApi = {
  getExperiments(archived: false | true | 'all' = false, page = 0, size = 20): Promise<PageResponse<ExperimentDto>> {
    return request({ url: '/experiments', method: 'GET', params: { archived, page, size } })
  },
  getExperiment(id: number): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}`, method: 'GET' })
  },
  createExperiment(data: ExperimentRequest): Promise<ExperimentDto> {
    return request({ url: '/experiments', method: 'POST', data })
  },
  updateExperiment(id: number, data: ExperimentRequest): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}`, method: 'PUT', data })
  },
  setExperimentArchived(id: number, archived: boolean): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}/${archived ? 'archive' : 'restore'}`, method: 'PUT' })
  },
  deleteExperiment(id: number): Promise<void> {
    return request({ url: `/experiments/${id}`, method: 'DELETE' })
  },
  createExplorationCard(experimentId: number, data: ExperimentExplorationCardRequest): Promise<CardDto> {
    return request({ url: `/experiments/${experimentId}/exploration/cards`, method: 'POST', data })
  },
  getExploration(experimentId: number): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration`, method: 'GET' })
  },
  updateCurrentTry(experimentId: number, currentTry: string, sourceMethodId: number | null = null): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/current-try`, method: 'PUT', data: { currentTry, sourceMethodId } })
  },
  updateFavoriteTries(experimentId: number, favoriteTries: string[]): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/favorite-tries`, method: 'PUT', data: { favoriteTries } })
  },
  createExplorationRecord(experimentId: number, discovery: string, includeCurrentTry: boolean): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/records`, method: 'POST', data: { discovery, includeCurrentTry } })
  },
  updateExplorationRecord(experimentId: number, recordId: number, tryText: string, discovery: string): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/records/${recordId}`, method: 'PUT', data: { tryText, discovery } })
  },
  deleteExplorationRecord(experimentId: number, recordId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/exploration/records/${recordId}`, method: 'DELETE' })
  },
  clearExploration(experimentId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/exploration`, method: 'DELETE' })
  },
  appendExplorationToCard(experimentId: number, cardId: number, recordIds: number[], content: string): Promise<CardDto> {
    return request({
      url: `/experiments/${experimentId}/exploration/cards/${cardId}`,
      method: 'POST',
      data: { recordIds, content },
    })
  },
  getCardThreads(cardId: string | number): Promise<CardExplorationThreadDto[]> {
    return request({ url: `/cards/${cardId}/exploration-threads`, method: 'GET' })
  },
  getThreads(experimentId: number): Promise<ExplorationThreadDto[]> {
    return request({ url: `/experiments/${experimentId}/threads`, method: 'GET' })
  },
  getThreadSummaries(experimentId: number, page = 0, size = 20): Promise<PageResponse<ExplorationThreadSummaryDto>> {
    return request({ url: `/experiments/${experimentId}/threads/summaries`, method: 'GET', params: { page, size } })
  },
  getThread(experimentId: number, threadId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}`, method: 'GET' })
  },
  getWorkThreads(workId: number): Promise<Array<{ threadId: number; threadName: string; experimentId: number; experimentTitle: string; status: ExplorationThreadStatus }>> {
    return request({ url: `/creative-works/${workId}/exploration-threads`, method: 'GET' })
  },
  createThreadWithImports(experimentId: number, name: string, items: ExplorationThreadImportItem[]): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/imports`, method: 'POST', data: { name, items } })
  },
  createThreadWithMaterial(experimentId: number, name: string, cardId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/materials`, method: 'POST', data: { name, cardId } })
  },
  createThreadDiscovery(experimentId: number, threadId: number, data: { methodId: number | null; tryText: string; discovery: string }): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/discoveries`, method: 'POST', data })
  },
  addThreadCards(experimentId: number, threadId: number, cardIds: number[], role: ExplorationThreadCardRole): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/cards/batch`, method: 'POST', data: { cardIds, role } })
  },
  updateThreadCardNote(experimentId: number, threadId: number, linkId: number, note: string): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/cards/${linkId}/note`, method: 'PUT', data: { note } })
  },
  createThread(experimentId: number, data: ExplorationThreadRequest): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads`, method: 'POST', data })
  },
  updateThread(experimentId: number, threadId: number, data: ExplorationThreadRequest): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}`, method: 'PUT', data })
  },
  deleteThread(experimentId: number, threadId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}`, method: 'DELETE' })
  },
  reorderThreads(experimentId: number, threadIds: number[]): Promise<ExplorationThreadDto[]> {
    return request({ url: `/experiments/${experimentId}/threads/order`, method: 'PUT', data: { threadIds } })
  },
  moveThread(experimentId: number, threadId: number, targetThreadId: number, after: boolean): Promise<void> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/position`, method: 'PUT', data: { targetThreadId, after } })
  },
  addThreadMethod(experimentId: number, threadId: number, content: string, sourceRecordId: number | null = null): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/methods`, method: 'POST', data: { content, sourceRecordId } })
  },
  updateThreadMethod(experimentId: number, threadId: number, methodId: number, content: string, sourceRecordId: number | null = null): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/methods/${methodId}`, method: 'PUT', data: { content, sourceRecordId } })
  },
  deleteThreadMethod(experimentId: number, threadId: number, methodId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/methods/${methodId}`, method: 'DELETE' })
  },
  addThreadRecord(experimentId: number, threadId: number, data: { methodId?: number | null; discovery: string; sourceRecordId?: number | null; occurredAt?: string | null }): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/records`, method: 'POST', data })
  },
  updateThreadRecord(experimentId: number, threadId: number, recordId: number, data: { methodId?: number | null; discovery: string; sourceRecordId?: number | null; occurredAt?: string | null }): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/records/${recordId}`, method: 'PUT', data })
  },
  importThreadRecords(experimentId: number, threadId: number, items: ExplorationThreadImportItem[]): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/imports`, method: 'POST', data: { items } })
  },
  deleteThreadRecord(experimentId: number, threadId: number, recordId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/records/${recordId}`, method: 'DELETE' })
  },
  addThreadCard(experimentId: number, threadId: number, cardId: number, role: ExplorationThreadCardRole, note = ''): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/cards`, method: 'POST', data: { cardId, role, note } })
  },
  removeThreadCard(experimentId: number, threadId: number, linkId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/cards/${linkId}`, method: 'DELETE' })
  },
  addThreadWork(experimentId: number, threadId: number, workId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/works`, method: 'POST', data: { workId } })
  },
  removeThreadWork(experimentId: number, threadId: number, linkId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/works/${linkId}`, method: 'DELETE' })
  },
  threadStatusLabel(status: ExplorationThreadStatus): string {
    return ({ NOT_STARTED: '未開始', IN_PROGRESS: '進行中', PAUSED: '暫放', CONCLUDED: '已收束' })[status]
  },
}
