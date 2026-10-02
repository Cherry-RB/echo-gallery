import type { IssueDetail, IssueStatus, UpdateIssueRequest } from '../types/issue'

export const toIssueUpdateRequest = (
  issue: IssueDetail,
  status: IssueStatus = issue.status,
): UpdateIssueRequest => ({
  title: issue.title,
  objective: issue.objective,
  description: issue.description,
  currentAssessment: issue.currentAssessment,
  keyStates: issue.keyStates,
  dominantLoops: issue.dominantLoops,
  primaryConstraint: issue.primaryConstraint,
  leveragePoint: issue.leveragePoint,
  watchSignals: issue.watchSignals,
  nonInterventionNote: issue.nonInterventionNote,
  outcomeCriteria: issue.outcomeCriteria,
  externalUrl: issue.externalUrl,
  status,
})
