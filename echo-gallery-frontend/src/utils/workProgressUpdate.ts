import type { WorkProgressUpdateRequest } from '../types/work'

export const normalizeWorkProgressUpdate = (
  value: WorkProgressUpdateRequest,
): WorkProgressUpdateRequest => ({
  changeSummary: value.changeSummary?.trim() || null,
  assessment: value.assessment?.trim() || null,
  nextStep: value.nextStep?.trim() || null,
})

export const hasWorkProgressUpdateContent = (value: WorkProgressUpdateRequest) => {
  const normalized = normalizeWorkProgressUpdate(value)
  return Boolean(normalized.changeSummary || normalized.assessment || normalized.nextStep)
}

export const getWorkProgressUpdateLead = (value: WorkProgressUpdateRequest) => (
  value.changeSummary || value.assessment || value.nextStep || ''
)
