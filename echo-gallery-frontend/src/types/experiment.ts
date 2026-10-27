import type { CardContentRequest, PageResponse } from './card'

export type ExperimentThemeColor = 'LEAF' | 'LAKE' | 'AMBER' | 'LAVENDER' | 'CORAL' | 'MIST'
export type ExplorationThreadStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PAUSED' | 'CONCLUDED'
export type ExplorationThreadCardRole = 'MATERIAL' | 'RESULT'

export interface ExperimentDto {
  id: number
  title: string
  description: string | null
  hypothesis: string | null
  conclusion: string | null
  currentTry: string | null
  explorationRecordCount: number
  themeColor: ExperimentThemeColor
  isArchived: boolean
  createdAt: string
  updatedAt: string
}

export interface ExperimentRequest {
  title: string
  description?: string
  hypothesis?: string
  conclusion?: string
  themeColor?: ExperimentThemeColor
}

export interface ExperimentExplorationCardRequest extends CardContentRequest {
  recordIds: number[]
}

export interface ExperimentExplorationDto {
  recordCount?: number
  version?: number
  currentTry: string
  currentTrySourceMethodId: number | null
  favoriteTries: string[]
  records: ExperimentExplorationRecordDto[]
}

export interface ExperimentExplorationRecordDto {
  threadId?: number
  threadName?: string
  methodId?: number | null
  version?: number
  occurredAt?: string
  updatedAt?: string
  id: number
  tryText: string | null
  sourceThreadMethodId?: number | null
  discovery: string
  createdAt: string
  exports: Array<{
    cardId: number
    cardTitle: string
    exportedAt: string
  }>
}

export type ExperimentPage = PageResponse<ExperimentDto>

export interface ExplorationThreadMethodDto {
  version?: number
  discoveryCount?: number
  hasHistory?: boolean
  lastOrganizedAt?: string | null
  id: number
  content: string
  sortOrder: number
  sourceRecordId: number | null
  createdAt: string
  updatedAt: string
}

export interface ExplorationThreadRecordItemDto {
  version?: number
  threadId?: number
  tryText?: string | null
  id: number
  methodId: number | null
  discovery: string
  sortOrder: number
  sourceRecordId: number | null
  occurredAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ExplorationThreadCardDto {
  id: number
  cardId: number
  cardTitle: string
  cardNote: string | null
  cardType: string
  role: ExplorationThreadCardRole
  note: string | null
  sortOrder: number
  cardArchived: boolean
  createdAt: string
}

export interface ExplorationThreadWorkDto {
  id: number
  workId: number
  workTitle: string
  stage: string
  workArchived: boolean
  createdAt: string
}

export interface ExplorationThreadDto {
  version?: number
  unassignedVersion?: number
  systemKey?: string | null
  methodCount?: number
  recordCount?: number
  hasUnassignedHistory?: boolean
  unassignedRecordCount?: number
  id: number
  experimentId: number
  name: string
  status: ExplorationThreadStatus
  conclusion: string | null
  sortOrder: number
  lastActivityAt: string
  createdAt: string
  updatedAt: string
  methods: ExplorationThreadMethodDto[]
  records: ExplorationThreadRecordItemDto[]
  cards: ExplorationThreadCardDto[]
  works: ExplorationThreadWorkDto[]
}

export interface ExplorationThreadSummaryDto {
  orderVersion?: number
  version?: number
  systemKey?: string | null
  id: number
  experimentId: number
  name: string
  status: ExplorationThreadStatus
  conclusion: string | null
  sortOrder: number
  lastActivityAt: string
  materialCount: number
  methodCount: number
  recordCount: number
  resultCardCount: number
  workCount: number
  firstMethodContent: string | null
  recentMaterials: Array<{ cardId: number; title: string; note: string | null }>
  recentRecords: Array<{ id: number; methodContent: string | null; discovery: string; occurredAt: string | null; createdAt: string }>
}

export interface CardExplorationThreadDto {
  threadId: number
  threadName: string
  experimentId: number
  experimentTitle: string
  experimentArchived: boolean
  status: ExplorationThreadStatus
  role: ExplorationThreadCardRole
  note: string | null
}

export interface ExplorationThreadRequest {
  expectedVersion?: number
  name: string
  status?: ExplorationThreadStatus
  conclusion?: string
}

export interface ExplorationThreadImportItem {
  recordId: number
  includeMethod: boolean
  includeDiscovery: boolean
  methodContent?: string
  discovery?: string
  targetMethodId?: number | null
}
