import request from './request'
import type { TreasureAttempt, AttemptRequest } from '../../types/treasure'
import type { TreasureContent, TreasureDetail, TreasurePage, TreasureReference, TreasureSummary, TreasureRank, TreasureStats, TreasureTrial, TreasureYield, TrialRequest, YieldRequest } from '../../types/treasure'

export const treasureApi = {
  list(params: { keyword?: string; rank?: TreasureRank; archived?: boolean; page?: number; size?: number }): Promise<TreasurePage<TreasureSummary>> {
    return request({ url: '/treasures', method: 'GET', params })
  },
  detail(id: number): Promise<TreasureDetail> { return request({ url: `/treasures/${id}`, method: 'GET' }) },
  create(data: TreasureContent): Promise<TreasureDetail> { return request({ url: '/treasures', method: 'POST', data }) },
  update(id: number, data: TreasureContent): Promise<TreasureDetail> { return request({ url: `/treasures/${id}`, method: 'PUT', data }) },
  archive(id: number, archived: boolean): Promise<TreasureDetail> { return request({ url: `/treasures/${id}/${archived ? 'archive' : 'restore'}`, method: 'PUT' }) },
  delete(id: number): Promise<void> { return request({ url: `/treasures/${id}`, method: 'DELETE' }) },
  stats(): Promise<TreasureStats> { return request({ url: '/treasures/stats', method: 'GET' }) },
  relations(id: number, page = 0): Promise<TreasurePage<TreasureReference>> { return request({ url: `/treasures/${id}/relations`, method: 'GET', params: { page, size: 10 } }) },
  trials(id: number, page = 0): Promise<TreasurePage<TreasureTrial>> { return request({ url: `/treasures/${id}/trials`, method: 'GET', params: { page, size: 10 } }) },
  route(id: number): Promise<TreasureTrial[]> { return request({ url: `/treasures/${id}/challenge-route`, method: 'GET' }) },
  attempts(id: number, trialId: number, page = 0): Promise<TreasurePage<TreasureAttempt>> { return request({ url: `/treasures/${id}/trials/${trialId}/attempts`, method: 'GET', params: { page } }) },
  recordAttempt(id: number, trialId: number, data: AttemptRequest): Promise<TreasureAttempt> { return request({ url: `/treasures/${id}/trials/${trialId}/attempts`, method: 'POST', data }) },
  createTrial(id: number, data: TrialRequest): Promise<TreasureTrial> { return request({ url: `/treasures/${id}/trials`, method: 'POST', data }) },
  updateTrial(id: number, trialId: number, data: TrialRequest): Promise<TreasureTrial> { return request({ url: `/treasures/${id}/trials/${trialId}`, method: 'PUT', data }) },
  passTrial(id: number, trialId: number, data: { resultNote: string; evidenceUrl: string; attemptedAt: string }): Promise<TreasureTrial> { return request({ url: `/treasures/${id}/trials/${trialId}/pass`, method: 'POST', data }) },
  yields(id: number, page = 0): Promise<TreasurePage<TreasureYield>> { return request({ url: `/treasures/${id}/yields`, method: 'GET', params: { page, size: 10 } }) },
  createYield(id: number, data: YieldRequest): Promise<TreasureYield> { return request({ url: `/treasures/${id}/yields`, method: 'POST', data }) },
  updateYield(id: number, yieldId: number, data: YieldRequest): Promise<TreasureYield> { return request({ url: `/treasures/${id}/yields/${yieldId}`, method: 'PUT', data }) },
  deleteYield(id: number, yieldId: number): Promise<void> { return request({ url: `/treasures/${id}/yields/${yieldId}`, method: 'DELETE' }) },
}
