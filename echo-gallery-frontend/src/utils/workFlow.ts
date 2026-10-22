import { shallowRef } from 'vue'

export interface WorkUseTarget {
  cardIds?: number[]
  cardTitle?: string
  recordIds?: number[]
  experimentId?: number
  returnToHistory?: boolean
}

export const workUseTarget = shallowRef<WorkUseTarget>()
