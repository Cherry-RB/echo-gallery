import request from './request'
import type { PageResponse } from '../../types/card'
import type { ActivityState, ActivityRecord, MethodOption, FocusRequest, OrganizationMode, OrganizationSelection, OrganizationPreview, OrganizationCommit, OrganizationResult, ThreadOption } from '../../types/explorationActivity'
const root = (id: number) => `/experiments/${id}/activity`
export const explorationActivityApi = {
  method: (id: number, methodId: number): Promise<MethodOption> => request({ url: root(id) + `/methods/${methodId}`, method: 'GET' }),
  state: (id: number): Promise<ActivityState> => request({ url: root(id), method: 'GET' }),
  focus: (id: number, data: FocusRequest): Promise<ActivityState> => request({ url: root(id) + '/focus', method: 'PUT', data }),
  favorites: (id: number, methodIds: number[], expectedVersion: number): Promise<ActivityState> => request({ url: root(id) + '/favorites', method: 'PUT', data: { methodIds, expectedVersion } }),
  methods: (id: number, params: { threadId?: number | null; keyword?: string; page?: number; size?: number } = {}): Promise<PageResponse<MethodOption>> => request({ url: root(id) + '/methods', method: 'GET', params }),
  createMethod: (id: number, data: { threadId?: number | null; content: string; sourceMethodId?: number; expectedVersion?: number }): Promise<MethodOption> => request({ url: root(id) + '/methods', method: 'POST', data }),
  updateMethod: (id: number, methodId: number, content: string, expectedVersion: number): Promise<MethodOption> => request({ url: root(id) + `/methods/${methodId}`, method: 'PUT', data: { content, expectedVersion } }),
  deleteMethod: (id: number, methodId: number, expectedVersion: number, deleteRecords = false): Promise<void> => request({ url: root(id) + `/methods/${methodId}`, method: 'DELETE', params: { expectedVersion, deleteRecords } }),
  records: (id: number, params: { threadId?: number | null; methodId?: number | null; unassigned?: boolean; page?: number; size?: number } = {}): Promise<PageResponse<ActivityRecord>> => request({ url: root(id) + '/records', method: 'GET', params }),
  createRecord: (id: number, data: { threadId: number | null; methodId: number | null; discovery: string; expectedMethodVersion?: number }): Promise<ActivityRecord> => request({ url: root(id) + '/records', method: 'POST', data }),
  updateRecord: (id: number, record: ActivityRecord, discovery: string, methodId: number | null, expectedMethodVersion?: number, threadId = record.threadId): Promise<ActivityRecord> => request({ url: root(id) + `/records/${record.id}`, method: 'PUT', data: { threadId, methodId, discovery, expectedVersion: record.version, expectedMethodVersion } }),
  assign: (id: number, record: ActivityRecord, threadId: number, methodId: number | null, expectedMethodVersion?: number): Promise<ActivityRecord> => request({ url: root(id) + `/records/${record.id}/assignment`, method: 'PUT', data: { threadId, methodId, expectedVersion: record.version, expectedMethodVersion } }),
  deleteRecord: (id: number, recordId: number, expectedVersion: number): Promise<void> => request({ url: root(id) + `/records/${recordId}`, method: 'DELETE', params: { expectedVersion } }),
  history: (id: number, threadId: number, methodId: number | null): Promise<{ text: string; version: number; lastOrganizedAt: string | null }> => request({ url: root(id) + '/history', method: 'GET', params: { threadId, methodId } }),
  preview: (id: number, mode: OrganizationMode, groups: OrganizationSelection[], targetCardId?: number): Promise<OrganizationPreview> => request({ url: root(id) + '/organizations/preview', method: 'POST', data: { mode, groups, targetCardId } }),
  commit: (id: number, data: OrganizationCommit): Promise<OrganizationResult> => request({ url: root(id) + '/organizations', method: 'POST', data }),
  threadOptions: (id: number, params: { page?: number; keyword?: string; cardId?: number } = {}): Promise<PageResponse<ThreadOption>> => request({ url: `/experiments/${id}/threads/options`, method: 'GET', params }),
}
