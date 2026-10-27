import type { CardContentRequest } from './card'
import type { ExperimentExplorationDto, ExperimentExplorationRecordDto } from './experiment'

export interface MethodOption {
  id: number
  threadId: number
  threadName: string
  content: string
  version: number
  discoveryCount: number
  hasHistory: boolean
  lastOrganizedAt: string | null
}
export interface ActivityRecord extends ExperimentExplorationRecordDto {
  threadId: number
  threadName: string
  methodId: number | null
  version: number
  occurredAt: string
  updatedAt: string
}
export interface ActivityState extends ExperimentExplorationDto {
  records: ActivityRecord[]
  currentThreadId: number | null
  currentMethod: MethodOption | null
  recentMethods: MethodOption[]
  favoriteMethods: MethodOption[]
  version: number
  recordCount: number
}
export type OrganizationMode = 'HISTORY' | 'UPDATE_METHOD' | 'CARD'
export interface FocusRequest {
  threadId?: number | null
  methodId?: number | null
  newContent?: string
  expectedVersion: number
  action?: 'UPDATE' | 'COPY'
  expectedMethodVersion?: number
  favoriteMethodIds?: number[]
}
export interface OrganizationSelection {
  threadId: number
  methodId: number | null
  recordIds: number[]
  allRecords: boolean
  includeMethod: boolean
  includeHistory: boolean
}
export interface OrganizationPreview {
  token: string
  expiresAt: number
  cardContent: string
  groups: Array<{ key: string; threadId: number; threadName: string; methodId: number | null; methodContent: string; recordCount: number; text: string; historyText: string; lastOrganizedAt: string | null }>
}
export interface OrganizationCommit {
  token: string
  requestId: string
  drafts: Array<{ key: string; text: string }>
  clearRecords: boolean
  newMethodContent?: string
  newCard?: CardContentRequest
  cardContent?: string
  expectedFocusVersion?: number
  focusFavoriteMethodIds?: number[]
}
export interface OrganizationResult { cardId: number | null; clearedCount: number; threadIds: number[] }
export interface ThreadOption { id: number; name: string; available: boolean; systemKey: string | null }
