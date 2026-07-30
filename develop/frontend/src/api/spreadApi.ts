import { apiClient } from './client'
import type { AuditRequest } from '../audit/types'
import type {
  SpreadDefault,
  SpreadDefaultInput,
  SpreadGroup,
  SpreadGroupInput,
  SpreadResolution,
} from '../types/spread'

const DEFAULT_BASE_PATH = '/api/spread-defaults'
const GROUP_BASE_PATH = '/api/spread-groups'

/**
 * `update`/`create`/`update`/`remove` no longer apply directly — they submit
 * a request through the generic audit module and return `202 Accepted` with
 * the resulting `AuditRequest` instead of the spread entity itself, per
 * specs/frontend/spread.md (mirroring currencyPairApi.ts's post-audit-
 * integration convention). `list`/`resolve` are unaffected.
 */
export const spreadDefaultApi = {
  list: (brandId?: number) => {
    const qs = brandId !== undefined ? `?brandId=${brandId}` : ''
    return apiClient.get<SpreadDefault[]>(`${DEFAULT_BASE_PATH}${qs}`)
  },
  update: (id: number, input: SpreadDefaultInput) =>
    apiClient.put<AuditRequest>(`${DEFAULT_BASE_PATH}/${id}`, input),
}

export const spreadGroupApi = {
  list: (brandId?: number) => {
    const qs = brandId !== undefined ? `?brandId=${brandId}` : ''
    return apiClient.get<SpreadGroup[]>(`${GROUP_BASE_PATH}${qs}`)
  },
  create: (input: SpreadGroupInput) => apiClient.post<AuditRequest>(GROUP_BASE_PATH, input),
  update: (id: number, input: Partial<SpreadGroupInput>) =>
    apiClient.put<AuditRequest>(`${GROUP_BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<AuditRequest>(`${GROUP_BASE_PATH}/${id}`),
  resolve: (currencyPairId: number) =>
    apiClient.get<SpreadResolution>(`${GROUP_BASE_PATH}/resolve/${currencyPairId}`),
}
