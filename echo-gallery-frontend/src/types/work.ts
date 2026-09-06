import type { CardGrowthStatus, CardType } from './card'

export type WorkStatus = 'IDEA' | 'DRAFT' | 'ACTIVE' | 'DONE' | 'ARCHIVED'

export type WorkCardStatus = 'CANDIDATE' | 'USED'

export interface WorkContentRequest {
  title: string
  objective?: string | null
  description?: string | null
  currentAssessment?: string | null
  outcomeCriteria?: string | null
  externalUrl?: string | null
}

export type CreateWorkRequest = WorkContentRequest

export interface UpdateWorkRequest extends WorkContentRequest {
  status: WorkStatus
}

export interface AddWorkCardRequest {
  cardId: number
  note?: string | null
}

export interface UpdateWorkCardStatusRequest {
  status: WorkCardStatus
}

export interface UpdateWorkCardNoteRequest {
  note?: string | null
}

export interface WorkSummary {
  id: number
  title: string
  objective: string | null
  description: string | null
  currentAssessment: string | null
  outcomeCriteria: string | null
  externalUrl: string | null
  status: WorkStatus
  completedAt: string | null
  updatedAt: string
  latestProgressAt: string | null
  latestProgressChangeSummary: string | null
  latestProgressAssessment: string | null
  latestProgressNextStep: string | null
  candidateCount: number
  usedCount: number
}

export interface WorkProgressUpdateRequest {
  changeSummary?: string | null
  assessment?: string | null
  nextStep?: string | null
}

export interface WorkProgressUpdate {
  id: number
  workId: number
  workTitle: string
  changeSummary: string | null
  assessment: string | null
  nextStep: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkProgressUpdatePage {
  items: WorkProgressUpdate[]
  page: number
  size: number
  hasNext: boolean
}

export interface WorkDetail {
  id: number
  title: string
  objective: string | null
  description: string | null
  currentAssessment: string | null
  outcomeCriteria: string | null
  status: WorkStatus
  externalUrl: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkCard {
  id: number
  workId: number
  cardId: number
  cardTitle: string
  cardType: CardType
  cardGrowthStatus: CardGrowthStatus
  tags: string[]
  status: WorkCardStatus
  note: string | null
  linkedAt: string
  usedAt: string | null
}

export interface WorkCardPage {
  items: WorkCard[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CardWork {
  workId: number
  workTitle: string
  workStatus: WorkStatus
  status: WorkCardStatus
  note: string | null
  linkedAt: string
  usedAt: string | null
}
