import type { IssueStatus } from '../types/issue'

export const issueStatusMeta: Record<IssueStatus, { label: string; tone: string }> = {
  IDEA: { label: '探索', tone: 'exploring' },
  DRAFT: { label: '建模', tone: 'modeling' },
  ACTIVE: { label: '介入／觀察', tone: 'intervening' },
  DONE: { label: '收斂', tone: 'converged' },
  ARCHIVED: { label: '封存', tone: 'archived' },
}

export const issueStatusOptions = (Object.keys(issueStatusMeta) as IssueStatus[])
  .map((value) => ({ value, label: issueStatusMeta[value].label }))
