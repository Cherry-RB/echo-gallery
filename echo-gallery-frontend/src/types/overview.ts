import type { TreasureEvent, TreasureGrowth, TreasureSummary } from './treasure'

export type OverviewPeriodDays = 7 | 30 | 90

export interface OverviewResponse {
  periodDays: OverviewPeriodDays
  periodStartAt: string
  periodEndAt: string
  current: {
    todayReturnPoolCount: number
    recurringCardCount: number
    pausedCardCount: number
    needsProcessingCardCount: number
    activeExperimentCount: number
    treasureCount: number
    workCount: number
    experimentTries: OverviewExperimentTry[]
    treasureActions: TreasureSummary[]
    attentionSignals: OverviewAttentionSignal[]
  }
  period: {
    flow: { reengagedCardCount: number; reviewedCardCount: number }
    treasureGrowth: TreasureGrowth
    recentTreasureYields: TreasureEvent[]
    recentTreasureProgress: TreasureEvent[]
    activities: OverviewActivity[]
    recentExperimentMaterials: OverviewExperimentMaterial[]
  }
}

export interface OverviewActivity {
  key: 'created' | 'offered' | 'reviewed' | 'experiment-material' | 'exploration-record' | 'treasure-collected' | 'treasure-promoted' | 'treasure-yield'
  value: number
}

export interface OverviewExperimentMaterial {
  cardId: number
  title: string
  addedAt: string
  experimentId: number
  experimentTitle: string
  sourceKind: 'EXPLORATION' | 'MATERIAL'
  tags: string[]
}

export interface OverviewRecentResponse {
  periodDays: OverviewPeriodDays
  periodStartAt: string
  periodEndAt: string
  period: OverviewResponse['period']
}

export interface OverviewExperimentTry {
  experimentId: number
  experimentTitle: string
  currentTry: string
  explorationRecordCount: number
}

export interface OverviewAttentionSignal {
  key: 'processing' | 'snooze'
  cardCount: number
}

export interface CardReturnOverviewResponse {
  state: {
    todayReturnPoolCount: number
    recurringCardCount: number
    pausedCardCount: number
    archivedCardCount: number
    needsProcessingCardCount: number
    neverReviewedCardCount: number
    scheduleIssueCardCount: number
  }
  cadenceBands: Array<{ key: string; cardCount: number }>
  forecastDays: Array<{ date: string; cardCount: number }>
  snoozeBands: Array<{ key: string; cardCount: number }>
}
