import type {
  AddWorkCardRequest,
  CardWork,
  CreateWorkRequest,
  UpdateWorkCardStatusRequest,
  UpdateWorkCardNoteRequest,
  UpdateWorkRequest,
  WorkCard,
  WorkDetail,
  WorkSummary,
} from '../../types/work'
import request from './request'

type ResourceId = string | number

export const workApi = {
  getWorks(): Promise<WorkSummary[]> {
    return request({
      url: '/works',
      method: 'GET',
    })
  },

  getWork(workId: ResourceId): Promise<WorkDetail> {
    return request({
      url: `/works/${workId}`,
      method: 'GET',
    })
  },

  createWork(data: CreateWorkRequest): Promise<WorkDetail> {
    return request({
      url: '/works',
      method: 'POST',
      data,
    })
  },

  updateWork(workId: ResourceId, data: UpdateWorkRequest): Promise<WorkDetail> {
    return request({
      url: `/works/${workId}`,
      method: 'PUT',
      data,
    })
  },

  getWorkCards(workId: ResourceId): Promise<WorkCard[]> {
    return request({
      url: `/works/${workId}/cards`,
      method: 'GET',
    })
  },

  getCardWorks(cardId: ResourceId): Promise<CardWork[]> {
    return request({
      url: `/cards/${cardId}/works`,
      method: 'GET',
    })
  },

  addWorkCard(workId: ResourceId, data: AddWorkCardRequest): Promise<WorkCard> {
    return request({
      url: `/works/${workId}/cards`,
      method: 'POST',
      data,
    })
  },

  removeWorkCard(workId: ResourceId, cardId: ResourceId): Promise<void> {
    return request({
      url: `/works/${workId}/cards/${cardId}`,
      method: 'DELETE',
    })
  },

  updateWorkCardStatus(
    workId: ResourceId,
    cardId: ResourceId,
    data: UpdateWorkCardStatusRequest,
  ): Promise<WorkCard> {
    return request({
      url: `/works/${workId}/cards/${cardId}/status`,
      method: 'PUT',
      data,
    })
  },

  updateWorkCardNote(
    workId: ResourceId,
    cardId: ResourceId,
    data: UpdateWorkCardNoteRequest,
  ): Promise<WorkCard> {
    return request({
      url: `/works/${workId}/cards/${cardId}/note`,
      method: 'PUT',
      data,
    })
  },
}
