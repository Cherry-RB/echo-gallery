export type WorkMode = 'INTERNAL' | 'EXTERNAL'
export type WorkStage = 'WORKING' | 'FIRST_DRAFT' | 'REVISING' | 'REVISED' | 'SHAREABLE'
export const workStages: Record<WorkStage, string> = { WORKING: '製作中', FIRST_DRAFT: '初版完成', REVISING: '編修中', REVISED: '編修完成', SHAREABLE: '可分享' }
export interface WorkSummary {
  id: number; title: string; description: string | null; experimentId: number | null; experimentTitle: string | null
  editorMode: WorkMode; stage: WorkStage; nextStep: string | null; published: boolean; archived: boolean
  treasureId: number | null; editedAt: string
  characterCount: number | null
}
export interface WorkDetail {
  work: WorkSummary; body: string; bodyVersion: number; externalUrl: string | null
  firstCompletedOn: string | null; stageChangedAt: string; shareUrl: string | null; sharedOn: string | null; shareNote: string | null
}
export interface WorkInfo { title: string; description: string | null; experimentId: number | null; editorMode: WorkMode; externalUrl: string | null }
export interface WorkSource { id: number | null; cardId: number | null; recordId: number | null; title: string; text: string; tryText?: string | null; discovery?: string | null; experimentId: number | null; experimentTitle: string | null; archived: boolean }
export interface WorkUsage { cardId: number | null; recordId: number | null; workId: number; title: string; archived: boolean }
export interface WorkPage<T> { content: T[]; totalPages: number; totalElements: number; page: number; size: number }
export interface ExperimentOption { id: number; title: string; archived: boolean }
