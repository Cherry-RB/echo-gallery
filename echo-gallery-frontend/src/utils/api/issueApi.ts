import type {
  AddIssueCardRequest,
  CardIssue,
  CreateIssueRequest,
  UpdateIssueCardStatusRequest,
  UpdateIssueCardNoteRequest,
  UpdateIssueRequest,
  IssueCard,
  IssueCardPage,
  IssueCardStatus,
  IssueDetail,
  IssueUpdate,
  IssueUpdatePage,
  IssueUpdateRequest,
  IssueSummary,
} from '../../types/issue'
import request from './request'

type ResourceId = string | number

export const issueApi = {
  getIssues(): Promise<IssueSummary[]> {
    return request({
      url: '/issues',
      method: 'GET',
    })
  },

  getIssue(issueId: ResourceId): Promise<IssueDetail> {
    return request({
      url: `/issues/${issueId}`,
      method: 'GET',
    })
  },

  createIssue(data: CreateIssueRequest): Promise<IssueDetail> {
    return request({
      url: '/issues',
      method: 'POST',
      data,
    })
  },

  updateIssue(issueId: ResourceId, data: UpdateIssueRequest): Promise<IssueDetail> {
    return request({
      url: `/issues/${issueId}`,
      method: 'PUT',
      data,
    })
  },

  deleteIssue(issueId: ResourceId): Promise<void> {
    return request({
      url: `/issues/${issueId}`,
      method: 'DELETE',
    })
  },

  getIssueUpdates(issueId: ResourceId, page = 0, size = 5): Promise<IssueUpdatePage> {
    return request({
      url: `/issues/${issueId}/updates`,
      method: 'GET',
      params: { page, size },
    })
  },

  getRecentIssueUpdates(limit = 12): Promise<IssueUpdate[]> {
    return request({
      url: '/issue-updates/recent',
      method: 'GET',
      params: { limit },
    })
  },

  createIssueUpdate(
    issueId: ResourceId,
    data: IssueUpdateRequest,
  ): Promise<IssueUpdate> {
    return request({
      url: `/issues/${issueId}/updates`,
      method: 'POST',
      data,
    })
  },

  updateIssueUpdate(
    issueId: ResourceId,
    updateId: ResourceId,
    data: IssueUpdateRequest,
  ): Promise<IssueUpdate> {
    return request({
      url: `/issues/${issueId}/updates/${updateId}`,
      method: 'PUT',
      data,
    })
  },

  deleteIssueUpdate(issueId: ResourceId, updateId: ResourceId): Promise<void> {
    return request({
      url: `/issues/${issueId}/updates/${updateId}`,
      method: 'DELETE',
    })
  },

  getIssueCards(
    issueId: ResourceId,
    status: IssueCardStatus,
    page = 0,
    size = 10,
  ): Promise<IssueCardPage> {
    return request({
      url: `/issues/${issueId}/cards`,
      method: 'GET',
      params: { status, page, size },
    })
  },

  getCardIssues(cardId: ResourceId): Promise<CardIssue[]> {
    return request({
      url: `/cards/${cardId}/issues`,
      method: 'GET',
    })
  },

  addIssueCard(issueId: ResourceId, data: AddIssueCardRequest): Promise<IssueCard> {
    return request({
      url: `/issues/${issueId}/cards`,
      method: 'POST',
      data,
    })
  },

  removeIssueCard(issueId: ResourceId, cardId: ResourceId): Promise<void> {
    return request({
      url: `/issues/${issueId}/cards/${cardId}`,
      method: 'DELETE',
    })
  },

  updateIssueCardStatus(
    issueId: ResourceId,
    cardId: ResourceId,
    data: UpdateIssueCardStatusRequest,
  ): Promise<IssueCard> {
    return request({
      url: `/issues/${issueId}/cards/${cardId}/status`,
      method: 'PUT',
      data,
    })
  },

  updateIssueCardNote(
    issueId: ResourceId,
    cardId: ResourceId,
    data: UpdateIssueCardNoteRequest,
  ): Promise<IssueCard> {
    return request({
      url: `/issues/${issueId}/cards/${cardId}/note`,
      method: 'PUT',
      data,
    })
  },
}
