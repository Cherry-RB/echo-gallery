import type { CardType } from './card'

export type IssueStatus = 'IDEA' | 'DRAFT' | 'ACTIVE' | 'DONE' | 'ARCHIVED'

export type IssueCardStatus = 'CANDIDATE' | 'USED'

export interface IssueContentRequest {
  title: string
  objective?: string | null
  description?: string | null
  currentAssessment?: string | null
  outcomeCriteria?: string | null
  externalUrl?: string | null
}

export type CreateIssueRequest = IssueContentRequest

export interface UpdateIssueRequest extends IssueContentRequest {
  status: IssueStatus
}

export interface AddIssueCardRequest {
  cardId: number
  note?: string | null
}

export interface UpdateIssueCardStatusRequest {
  status: IssueCardStatus
}

export interface UpdateIssueCardNoteRequest {
  note?: string | null
}

export interface IssueSummary {
  id: number
  title: string
  objective: string | null
  description: string | null
  currentAssessment: string | null
  outcomeCriteria: string | null
  externalUrl: string | null
  status: IssueStatus
  completedAt: string | null
  updatedAt: string
  latestProgressAt: string | null
  latestProgressChangeSummary: string | null
  latestProgressAssessment: string | null
  latestProgressNextStep: string | null
  candidateCount: number
  usedCount: number
}

export interface IssueUpdateRequest {
  changeSummary?: string | null
  assessment?: string | null
  nextStep?: string | null
}

export interface IssueUpdate {
  id: number
  issueId: number
  issueTitle: string
  changeSummary: string | null
  assessment: string | null
  nextStep: string | null
  createdAt: string
  updatedAt: string
}

export interface IssueUpdatePage {
  items: IssueUpdate[]
  page: number
  size: number
  hasNext: boolean
}

export interface IssueDetail {
  id: number
  title: string
  objective: string | null
  description: string | null
  currentAssessment: string | null
  outcomeCriteria: string | null
  status: IssueStatus
  externalUrl: string | null
  completedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface IssueCard {
  id: number
  issueId: number
  cardId: number
  cardTitle: string
  cardType: CardType
  tags: string[]
  status: IssueCardStatus
  note: string | null
  linkedAt: string
  usedAt: string | null
}

export interface IssueCardPage {
  items: IssueCard[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CardIssue {
  issueId: number
  issueTitle: string
  issueStatus: IssueStatus
  status: IssueCardStatus
  note: string | null
  linkedAt: string
  usedAt: string | null
}
