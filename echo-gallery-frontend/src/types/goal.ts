import type { CardDto } from './card'
export type GoalStatus = 'PLANNED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ENDED'
export type GoalKind = 'TAG' | 'EXPERIMENT' | 'THREAD' | 'WORK' | 'TREASURE' | 'CARD'
export interface GoalSource { kind: GoalKind; id: number; excluded: boolean }
export interface GoalDefinition {
  name: string; startsOn: string; endsOn: string; expectation: string; motivation: string; imageUrl: string
  status: GoalStatus; archived: boolean; pinned: boolean; pinOrder: number; reviewDays: number
  remindersEnabled: boolean; allTags: boolean; sources: GoalSource[]; metrics: string[]
}
export interface GoalView { id: number; definition: GoalDefinition; effectiveStatus: GoalStatus; version: number; cardCount: number; trackingStartedAt: string; closedAt: string | null }
export interface GoalOption { kind: GoalKind; id: number; title: string; parentId?: number | null; parentTitle?: string | null }
export interface GoalSourceView extends GoalSource { title: string; available: boolean; path: string; parentId?: number | null; parentTitle?: string | null }
export interface GoalQuery { keyword?: string; statuses?: GoalStatus[]; archiveScope?: 'UNARCHIVED' | 'ARCHIVED' | 'ALL'; pinned?: boolean; sort?: 'created' | 'pinOrder'; page?: number; size?: number; status?: string; archived?: boolean }
export interface GoalPreference { allActive: boolean; goalIds: number[]; includePaused: boolean; includeArchived: boolean; version: number }
export interface GoalBoardPage { content: CardDto[]; total: number; selectedGoalCount?: number; hasMore: boolean; nextCursor: string; fingerprint: string; emptyReason?: string }
export interface GoalAssociation { goalId: number; name: string; status: GoalStatus; reasons: string[] }
export const goalStatuses: Record<GoalStatus, string> = { PLANNED: '未開始', ACTIVE: '進行中', PAUSED: '暫停', COMPLETED: '已完成', ENDED: '已結束' }
export const goalKinds: Record<GoalKind, string> = { TAG: '標籤', EXPERIMENT: '實驗', THREAD: '探索線', WORK: '作品', TREASURE: '展品', CARD: '個別卡片' }
export function isGoalPeriodDue(goal: GoalView) {
  return ['ACTIVE', 'PAUSED'].includes(goal.effectiveStatus) && goal.definition.endsOn < new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Taipei' })
}
export const goalMetrics: Record<string, string> = {
  M01: '目前相關卡片', M02: '本期新增卡片', M03: '本期修改過內容的卡片', M04: '有內容修改的日期', M05: '本期完成今日回顧的卡片', M06: '本期再次運用的卡片',
  M07: '相關實驗／探索線', M08: '目前行動備選', M09: '目前保留發現', M10: '本期新增發現', M11: '本期新增行動備選', M12: '整理次數',
  M13: '目前作品階段', M14: '本期初版完成', M15: '目前關聯展品', M16: '本期入藏', M17: '試煉嘗試／通過', M18: '本期價值產出', M19: '站外活動量', M20: '復盤自評',
}
