import type { CardReturnOverviewResponse, OverviewPeriodDays, OverviewResponse } from '../../types/overview'
import request from './request'

export const overviewApi = {
  getOverview(periodDays: OverviewPeriodDays): Promise<OverviewResponse> {
    return request({ url: '/overview', method: 'GET', params: { periodDays } })
  },

  getCardReturnOverview(): Promise<CardReturnOverviewResponse> {
    return request({ url: '/overview/card-return', method: 'GET' })
  },
}
