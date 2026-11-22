import type { PageResponse } from '../../types/card'
import type {
  ExperimentExplorationDto,
  ExperimentDto,
  ExperimentRequest,
  ExplorationThreadDto,
  ExplorationThreadSummaryDto,
  ExplorationThreadRequest,
  ExplorationThreadStatus,
  ExplorationThreadCardRole,
  CardExplorationThreadDto,
  ExperimentRecordDto,
  ExperimentRecordRequest,
  ExperimentTopicDto,
  ExperimentPinnedPracticeDto,
  ExperimentRecordReferenceDto,
  ExperimentRecordReferenceRequest,
} from '../../types/experiment'
import request from './request'

export const experimentApi = {
  getCardRecordReferences(cardId: string | number): Promise<Array<{ referenceId: number; recordId: number; experimentId: number; experimentTitle: string; recordTitle: string; experimentArchived: boolean }>> {
    return request({ url: `/cards/${cardId}/experiment-record-references`, method: 'GET' })
  },
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
  getRecords(experimentId: number, page = 0, size = 20, params: { keyword?: string; understandingNode?: boolean; methodCandidate?: boolean; highlight?: string; dateFrom?: string; dateTo?: string; topicIds?: number[]; topicMatch?: 'ANY' | 'ALL'; unclassified?: boolean; sort?: string } = {}): Promise<PageResponse<ExperimentRecordDto>> {
    const { topicIds, ...rest } = params
    return request({ url: `/experiments/${experimentId}/records`, method: 'GET', params: { page, size, ...rest, topicIds: topicIds?.join(',') } })
  },
  getRecord(experimentId: number, recordId: number): Promise<ExperimentRecordDto> {
    return request({ url: `/experiments/${experimentId}/records/${recordId}`, method: 'GET' })
  },
  getRecentPractices(experimentId: number): Promise<string[]> { return request({ url: `/experiments/${experimentId}/recent-practices`, method: 'GET' }) },
  createRecord(experimentId: number, data: ExperimentRecordRequest): Promise<ExperimentRecordDto> {
    return request({ url: `/experiments/${experimentId}/records`, method: 'POST', data })
  },
  createRecordFromCard(experimentId: number, cardId: number, record: ExperimentRecordRequest): Promise<ExperimentRecordDto> {
    return request({ url: `/experiments/${experimentId}/records/from-card`, method: 'POST', data: { cardId, record } })
  },
  updateRecord(experimentId: number, recordId: number, data: ExperimentRecordRequest): Promise<ExperimentRecordDto> {
    return request({ url: `/experiments/${experimentId}/records/${recordId}`, method: 'PUT', data })
  },
  deleteRecord(experimentId: number, recordId: number, expectedVersion: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/records/${recordId}`, method: 'DELETE', params: { expectedVersion } })
  },
  getRecordReferences(experimentId: number, recordId: number): Promise<ExperimentRecordReferenceDto[]> { return request({ url: `/experiments/${experimentId}/records/${recordId}/references`, method: 'GET' }) },
  createRecordReference(experimentId: number, recordId: number, data: ExperimentRecordReferenceRequest): Promise<ExperimentRecordReferenceDto> { return request({ url: `/experiments/${experimentId}/records/${recordId}/references`, method: 'POST', data }) },
  deleteRecordReference(experimentId: number, recordId: number, referenceId: number): Promise<void> { return request({ url: `/experiments/${experimentId}/records/${recordId}/references/${referenceId}`, method: 'DELETE' }) },
  getTopics(experimentId: number): Promise<ExperimentTopicDto[]> { return request({ url: `/experiments/${experimentId}/topics`, method: 'GET' }) },
  createTopic(experimentId: number, name: string): Promise<ExperimentTopicDto> { return request({ url: `/experiments/${experimentId}/topics`, method: 'POST', data: { name } }) },
  pinTopic(experimentId: number, topicId: number, slot: number): Promise<ExperimentTopicDto> { return request({ url: `/experiments/${experimentId}/topics/${topicId}/pin`, method: 'PUT', data: { slot } }) },
  deleteTopic(experimentId: number, topicId: number): Promise<void> { return request({ url: `/experiments/${experimentId}/topics/${topicId}`, method: 'DELETE' }) },
  getRecordTopics(experimentId: number, recordId: number): Promise<ExperimentTopicDto[]> { return request({ url: `/experiments/${experimentId}/records/${recordId}/topics`, method: 'GET' }) },
  setRecordTopics(experimentId: number, recordId: number, topicIds: number[]): Promise<ExperimentTopicDto[]> { return request({ url: `/experiments/${experimentId}/records/${recordId}/topics`, method: 'PUT', data: { topicIds } }) },
  getPinnedPractices(experimentId: number): Promise<ExperimentPinnedPracticeDto[]> { return request({ url: `/experiments/${experimentId}/pinned-practices`, method: 'GET' }) },
  pinPractice(experimentId: number, content: string, slot?: number): Promise<ExperimentPinnedPracticeDto> { return request({ url: `/experiments/${experimentId}/pinned-practices`, method: 'POST', data: { content, slot } }) },
  unpinPractice(experimentId: number, practiceId: number): Promise<void> { return request({ url: `/experiments/${experimentId}/pinned-practices/${practiceId}`, method: 'DELETE' }) },
  getExploration(experimentId: number): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/activity`, method: 'GET' })
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
  createThreadWithMaterial(experimentId: number, name: string, cardId: number): Promise<ExplorationThreadDto> {
    return request({ url: `/experiments/${experimentId}/threads/materials`, method: 'POST', data: { name, cardId } })
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
  reorderThreads(experimentId: number, threadIds: number[], expectedVersion: number): Promise<ExplorationThreadDto[]> {
    return request({ url: `/experiments/${experimentId}/threads/order`, method: 'PUT', data: { threadIds, expectedVersion } })
  },
  moveThread(experimentId: number, threadId: number, targetThreadId: number, after: boolean, expectedVersion: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/threads/${threadId}/position`, method: 'PUT', data: { targetThreadId, after, expectedVersion } })
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
