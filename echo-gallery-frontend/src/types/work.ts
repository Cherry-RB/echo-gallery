import type { CardGrowthStatus, CardType } from './card'

export type WorkStatus = 'IDEA' | 'DRAFT' | 'ACTIVE' | 'DONE' | 'ARCHIVED'

export type WorkCardStatus = 'CANDIDATE' | 'USED'

export interface WorkContentRequest {
  title: string
  description?: string | null
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

export interface WorkSummary {
  id: number
  title: string
  status: WorkStatus
  completedAt: string | null
  updatedAt: string
  candidateCount: number
  usedCount: number
}

export interface WorkDetail {
  id: number
  title: string
  description: string | null
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
