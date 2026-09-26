import type { CardDto, PageResponse } from '../../types/card'
import type {
  CardExperimentDto,
  CardExperimentContextDto,
  CardRelationDto,
  ExperimentCardDto,
  ExperimentCardRequest,
  ExperimentExplorationCardRequest,
  ExperimentExplorationDto,
  ExperimentDto,
  ExperimentGrowRequest,
  ExperimentRequest,
  ExperimentStage
} from '../../types/experiment'
import request from './request'

export const experimentApi = {
  getExperiments(archived = false, page = 0, size = 20): Promise<PageResponse<ExperimentDto>> {
    return request({ url: '/experiments', method: 'GET', params: { archived, page, size } })
  },
  getExperiment(id: number): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}`, method: 'GET' })
  },
  createExperiment(data: ExperimentRequest): Promise<ExperimentDto> {
    return request({ url: '/experiments', method: 'POST', data })
  },
  updateExperiment(id: number, data: ExperimentRequest): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}`, method: 'PUT', data })
  },
  setExperimentArchived(id: number, archived: boolean): Promise<ExperimentDto> {
    return request({ url: `/experiments/${id}/${archived ? 'archive' : 'restore'}`, method: 'PUT' })
  },
  deleteExperiment(id: number): Promise<void> {
    return request({ url: `/experiments/${id}`, method: 'DELETE' })
  },
  getExperimentCards(experimentId: number, stage: ExperimentStage, page = 0, size = 10): Promise<PageResponse<ExperimentCardDto>> {
    return request({ url: `/experiments/${experimentId}/cards`, method: 'GET', params: { stage, page, size } })
  },
  addExperimentCard(experimentId: number, data: ExperimentCardRequest): Promise<ExperimentCardDto> {
    return request({ url: `/experiments/${experimentId}/cards`, method: 'POST', data })
  },
  updateExperimentCardStage(experimentId: number, cardId: number, stage: ExperimentStage): Promise<ExperimentCardDto> {
    return request({ url: `/experiments/${experimentId}/cards/${cardId}/stage`, method: 'PUT', data: { stage } })
  },
  updateExperimentCardNote(experimentId: number, cardId: number, note: string): Promise<ExperimentCardDto> {
    return request({ url: `/experiments/${experimentId}/cards/${cardId}/note`, method: 'PUT', data: { note } })
  },
  removeExperimentCard(experimentId: number, cardId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/cards/${cardId}`, method: 'DELETE' })
  },
  growCard(experimentId: number, data: ExperimentGrowRequest): Promise<CardDto> {
    return request({ url: `/experiments/${experimentId}/grow`, method: 'POST', data })
  },
  createExplorationCard(experimentId: number, data: ExperimentExplorationCardRequest): Promise<CardDto> {
    return request({ url: `/experiments/${experimentId}/exploration/cards`, method: 'POST', data })
  },
  getExploration(experimentId: number): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration`, method: 'GET' })
  },
  updateCurrentTry(experimentId: number, currentTry: string): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/current-try`, method: 'PUT', data: { currentTry } })
  },
  updateFavoriteTries(experimentId: number, favoriteTries: string[]): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/favorite-tries`, method: 'PUT', data: { favoriteTries } })
  },
  createExplorationRecord(experimentId: number, discovery: string, includeCurrentTry: boolean): Promise<ExperimentExplorationDto> {
    return request({ url: `/experiments/${experimentId}/exploration/records`, method: 'POST', data: { discovery, includeCurrentTry } })
  },
  deleteExplorationRecord(experimentId: number, recordId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/exploration/records/${recordId}`, method: 'DELETE' })
  },
  clearExploration(experimentId: number): Promise<void> {
    return request({ url: `/experiments/${experimentId}/exploration`, method: 'DELETE' })
  },
  appendExplorationToCard(experimentId: number, cardId: number, recordIds: number[], content: string): Promise<CardDto> {
    return request({
      url: `/experiments/${experimentId}/exploration/cards/${cardId}`,
      method: 'POST',
      data: { recordIds, content },
    })
  },
  getCardExperiments(cardId: string | number): Promise<CardExperimentDto[]> {
    return request({ url: `/cards/${cardId}/experiments`, method: 'GET' })
  },
  getCardRelations(cardId: string | number): Promise<CardRelationDto[]> {
    return request({ url: `/cards/${cardId}/relations`, method: 'GET' })
  },
  getCardExperimentContext(cardId: string | number): Promise<CardExperimentContextDto> {
    return request({ url: `/cards/${cardId}/experiment-context`, method: 'GET' })
  }
}
