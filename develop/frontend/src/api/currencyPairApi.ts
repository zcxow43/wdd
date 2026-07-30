import { apiClient } from './client'
import type { AuditRequest } from '../audit/types'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'

const BASE_PATH = '/api/currency-pairs'

export interface CurrencyPairListParams {
  brandId?: number
  active?: boolean
}

/**
 * There is no create — a brand's pair can only come into existence via the
 * global 幣種對主檔 page (specs/frontend/currency-pair-definition.md), which
 * fans a new pair out to every brand. Update/delete no longer apply
 * directly — they submit a request through the generic audit module and
 * return `202 Accepted` with the resulting `AuditRequest` instead of the
 * currency pair itself, per specs/frontend/currency-pair-approval.md.
 * `list` is unaffected.
 */
export const currencyPairApi = {
  list: (params?: CurrencyPairListParams) => {
    const query = new URLSearchParams()
    if (params?.brandId !== undefined) query.set('brandId', String(params.brandId))
    if (params?.active !== undefined) query.set('active', String(params.active))
    const qs = query.toString()
    return apiClient.get<CurrencyPair[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },
  update: (id: number, input: Partial<CurrencyPairInput>) =>
    apiClient.put<AuditRequest>(`${BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<AuditRequest>(`${BASE_PATH}/${id}`),
}
