import { apiClient } from './client'
import type { AuditRequest } from '../audit/types'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'

const BASE_PATH = '/api/currency-pairs'

export interface CurrencyPairListParams {
  brandId?: number
  active?: boolean
}

/**
 * Create/update/delete no longer apply directly — they submit a request
 * through the generic audit module and return `202 Accepted` with the
 * resulting `AuditRequest` instead of the currency pair itself, per
 * specs/frontend/currency-pair-approval.md. `list`/`getById` are unaffected.
 */
export const currencyPairApi = {
  list: (params?: CurrencyPairListParams) => {
    const query = new URLSearchParams()
    if (params?.brandId !== undefined) query.set('brandId', String(params.brandId))
    if (params?.active !== undefined) query.set('active', String(params.active))
    const qs = query.toString()
    return apiClient.get<CurrencyPair[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },
  create: (input: CurrencyPairInput) => apiClient.post<AuditRequest>(BASE_PATH, input),
  update: (id: number, input: Partial<CurrencyPairInput>) =>
    apiClient.put<AuditRequest>(`${BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<AuditRequest>(`${BASE_PATH}/${id}`),
}
