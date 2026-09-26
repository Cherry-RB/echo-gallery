export type OverviewPeriodDays = 7 | 30 | 90

export interface OverviewResponse {
  periodDays: OverviewPeriodDays
  periodStartAt: string
  periodEndAt: string
  current: {
    recurringCardCount: number
    pausedCardCount: number
    needsProcessingCardCount: number
    activeExperimentCount: number
    workWithNextStepCount: number
    experimentTries: OverviewExperimentTry[]
    nextSteps: OverviewNextStep[]
    attentionSignals: OverviewAttentionSignal[]
  }
  period: {
    flow: { reengagedCardCount: number; reviewedCardCount: number }
    generativity: { derivedCardCount: number; sourceCardCount: number }
    closure: { workWithFollowUpCount: number }
    activities: OverviewActivity[]
    derivedCards: OverviewDerivedCard[]
    recentExperimentMaterials: OverviewExperimentMaterial[]
  }
}

export interface OverviewActivity {
  key: 'created' | 'offered' | 'reviewed' | 'experiment-material' | 'work-linked' | 'derived' | 'exploration-record' | 'work-update'
  value: number
}

export interface OverviewDerivedCard {
  cardId: number
  title: string
  createdAt: string
  experimentId: number
  experimentTitle: string
  sourceCards: Array<{ cardId: number; title: string }>
  tags: string[]
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

export interface OverviewNextStep {
  workId: number
  workTitle: string
  nextStep: string
  updatedAt: string
}

export interface OverviewExperimentTry {
  experimentId: number
  experimentTitle: string
  currentTry: string
}

export interface OverviewAttentionSignal {
  key: 'processing' | 'snooze'
  cardCount: number
}

export interface CardReturnOverviewResponse {
  state: {
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
