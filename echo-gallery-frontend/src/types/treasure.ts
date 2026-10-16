import type { PageResponse } from './card'

export type TreasureRank = 'STONE' | 'BRONZE' | 'SILVER' | 'GOLD'
export type TrialStatus = 'PLANNED' | 'ATTEMPTED' | 'NOT_PASSED' | 'CANCELLED' | 'PASSED'
export interface TreasureReference { id: number; title: string }
export interface TreasureSummary {
  id: number
  title: string
  description: string | null
  coverImageUrl: string | null
  rank: TreasureRank
  acquiredOn: string
  isArchived: boolean
  createdAt: string
  updatedAt: string
  activeTrial: TreasureTrial | null
  yieldCount: number
}
export interface TreasureDetail extends TreasureSummary {
  description: string | null
  artifactUrl: string | null
  nextRank: TreasureRank | null
  sourceCard: TreasureReference | null
  sourceExperiment: TreasureReference | null
  sourceTreasures: TreasureReference[]
  activeTrial: TreasureTrial | null
  trialCount: number
  yieldCount: number
}
export interface TreasureContent {
  title: string
  description: string
  artifactUrl: string
  coverImageUrl: string
  acquiredOn: string
  sourceCardId: number | null
  sourceExperimentId: number | null
  sourceTreasureIds: number[]
}
export interface TreasureTrial {
  id: number
  targetRank: TreasureRank | null
  title: string
  criteria: string
  status: TrialStatus
  resultNote: string | null
  evidenceUrl: string | null
  attemptedAt: string | null
  passedAt: string | null
  createdAt: string
  updatedAt: string
}
export interface TrialRequest {
  targetRank: TreasureRank | null
  title: string
  criteria: string
}
export interface TreasureAttempt {
  id: number
  titleSnapshot: string
  criteriaSnapshot: string
  status: TrialStatus
  resultNote: string
  evidenceUrl: string | null
  attemptedAt: string
  createdAt: string
}
export interface AttemptRequest {
  status: 'ATTEMPTED' | 'NOT_PASSED' | 'PASSED'
  resultNote: string
  evidenceUrl: string
  attemptedAt: string
}
export interface TreasureYield {
  id: number
  title: string
  description: string | null
  occurredOn: string
  relatedCard: TreasureReference | null
  createdAt: string
  updatedAt: string
}
export interface YieldRequest {
  title: string
  description: string
  occurredOn: string
  relatedCardId: number | null
}
export interface TreasureStats { total: number; stone: number; bronze: number; silver: number; gold: number }
export interface TreasureGrowth { collectedCount: number; promotedTreasureCount: number; yieldTreasureCount: number; yieldRecordCount: number }
export interface TreasureEvent {
  treasureId: number
  treasureTitle: string
  kind: 'COLLECTED' | 'TRIAL_ATTEMPTED' | 'PROMOTED' | 'YIELD'
  eventTitle: string
  eventAt: string
  rank: TreasureRank | null
}
export type TreasurePage<T> = PageResponse<T>
