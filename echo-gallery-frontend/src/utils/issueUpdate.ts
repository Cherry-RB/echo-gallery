import type { IssueUpdateRequest } from '../types/issue'

export const normalizeIssueUpdate = (
  value: IssueUpdateRequest,
): IssueUpdateRequest => ({
  changeSummary: value.changeSummary?.trim() || null,
  assessment: value.assessment?.trim() || null,
  nextStep: value.nextStep?.trim() || null,
})

export const hasIssueUpdateContent = (value: IssueUpdateRequest) => {
  const normalized = normalizeIssueUpdate(value)
  return Boolean(normalized.changeSummary || normalized.assessment || normalized.nextStep)
}

export const getIssueUpdateLead = (value: IssueUpdateRequest) => (
  value.changeSummary || value.assessment || value.nextStep || ''
)
