import type { CardContentRequest, CardType, PageResponse } from './card'

export type ExperimentStage = 'SEED' | 'GROWING' | 'MATURE'
export type ExperimentThemeColor = 'LEAF' | 'LAKE' | 'AMBER' | 'LAVENDER' | 'CORAL' | 'MIST'

export interface ExperimentDto {
  id: number
  title: string
  description: string | null
  hypothesis: string | null
  themeColor: ExperimentThemeColor
  isArchived: boolean
  seedCount: number
  growingCount: number
  matureCount: number
  createdAt: string
  updatedAt: string
}

export interface ExperimentCardDto {
  cardId: number
  cardType: CardType
  cardTitle: string
  cardReason: string | null
  cardSummary: string | null
  cardTags: string[]
  cardArchived: boolean
  intervalDays: number | null
  nextShowAt: string | null
  needsProcessing: boolean
  stage: ExperimentStage
  note: string | null
  addedAt: string
}

export interface ExperimentRequest {
  title: string
  description?: string
  hypothesis?: string
  themeColor?: ExperimentThemeColor
}

export interface ExperimentCardRequest {
  cardId: number
  stage: ExperimentStage
  note?: string
}

export interface CardExperimentDto {
  experimentId: number
  experimentTitle: string
  experimentHypothesis: string | null
  experimentArchived: boolean
  stage: ExperimentStage
  note: string | null
  addedAt: string
}

export interface CardLineageCardDto {
  cardId: number
  cardTitle: string
  cardType: CardType
  cardArchived: boolean
}

export interface CardRelationDto {
  id: number
  experimentId: number
  experimentTitle: string
  experimentHypothesis: string | null
  relationType: 'DERIVED_FROM'
  sourceCard: CardLineageCardDto
  derivedCard: CardLineageCardDto
  createdAt: string
}

export interface ExperimentGrowRequest extends CardContentRequest {
  sourceCardIds: number[]
  stage: ExperimentStage
  note?: string
}

export interface CardExperimentContextDto {
  experiments: CardExperimentDto[]
  relations: CardRelationDto[]
}

export type ExperimentPage = PageResponse<ExperimentDto>
export type ExperimentCardPage = PageResponse<ExperimentCardDto>
