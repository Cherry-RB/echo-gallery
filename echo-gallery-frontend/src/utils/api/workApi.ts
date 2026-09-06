import type {
  AddWorkCardRequest,
  CardWork,
  CreateWorkRequest,
  UpdateWorkCardStatusRequest,
  UpdateWorkCardNoteRequest,
  UpdateWorkRequest,
  WorkCard,
  WorkCardPage,
  WorkCardStatus,
  WorkDetail,
  WorkProgressUpdate,
  WorkProgressUpdatePage,
  WorkProgressUpdateRequest,
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

  deleteWork(workId: ResourceId): Promise<void> {
    return request({
      url: `/works/${workId}`,
      method: 'DELETE',
    })
  },

  getWorkUpdates(workId: ResourceId, page = 0, size = 5): Promise<WorkProgressUpdatePage> {
    return request({
      url: `/works/${workId}/updates`,
      method: 'GET',
      params: { page, size },
    })
  },

  getRecentWorkUpdates(limit = 12): Promise<WorkProgressUpdate[]> {
    return request({
      url: '/work-updates/recent',
      method: 'GET',
      params: { limit },
    })
  },

  createWorkUpdate(
    workId: ResourceId,
    data: WorkProgressUpdateRequest,
  ): Promise<WorkProgressUpdate> {
    return request({
      url: `/works/${workId}/updates`,
      method: 'POST',
      data,
    })
  },

  updateWorkUpdate(
    workId: ResourceId,
    updateId: ResourceId,
    data: WorkProgressUpdateRequest,
  ): Promise<WorkProgressUpdate> {
    return request({
      url: `/works/${workId}/updates/${updateId}`,
      method: 'PUT',
      data,
    })
  },

  deleteWorkUpdate(workId: ResourceId, updateId: ResourceId): Promise<void> {
    return request({
      url: `/works/${workId}/updates/${updateId}`,
      method: 'DELETE',
    })
  },

  getWorkCards(
    workId: ResourceId,
    status: WorkCardStatus,
    page = 0,
    size = 10,
  ): Promise<WorkCardPage> {
    return request({
      url: `/works/${workId}/cards`,
      method: 'GET',
      params: { status, page, size },
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
