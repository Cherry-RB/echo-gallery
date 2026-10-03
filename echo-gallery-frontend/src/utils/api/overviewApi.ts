import type { CardReturnOverviewResponse, OverviewPeriodDays, OverviewRecentResponse, OverviewResponse } from '../../types/overview'
import request from './request'

export const overviewApi = {
  getCurrentOverview(): Promise<OverviewResponse['current']> {
    return request({ url: '/overview/current', method: 'GET' })
  },

  getRecentOverview(periodDays: OverviewPeriodDays): Promise<OverviewRecentResponse> {
    return request({ url: '/overview/recent', method: 'GET', params: { periodDays } })
  },

  getCardReturnOverview(): Promise<CardReturnOverviewResponse> {
    return request({ url: '/overview/card-return', method: 'GET' })
  },
}
