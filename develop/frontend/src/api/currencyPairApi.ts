import { get, put, del } from './client'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'
import type { AuditRequest } from '../audit/types'

const BASE_PATH = '/api/currency-pairs'

export interface CurrencyPairListParams {
  brandId?: number
  active?: boolean
}

/**
 * Standalone client for `/api/currency-pairs` (specs/backend/currency-pair.md +
 * specs/backend/currency-pair-approval.md). `GET` reads live data directly. There is
 * no `create` export at all — a brand's pair can only come into existence via the
 * global 幣種對主檔 page's fan-out (specs/frontend/currency-pair-definition.md);
 * `update`/`remove` both resolve an `AuditRequest` (the backend now returns `202` +
 * an `AuditRequestResponse` for both, never mutating `currency_pair` directly).
 */
export const currencyPairApi = {
  list(params: CurrencyPairListParams = {}): Promise<CurrencyPair[]> {
    const query = new URLSearchParams()
    if (params.brandId !== undefined) {
      query.set('brandId', String(params.brandId))
    }
    if (params.active !== undefined) {
      query.set('active', String(params.active))
    }
    const qs = query.toString()
    return get<CurrencyPair[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },

  update(id: number, input: CurrencyPairInput): Promise<AuditRequest> {
    return put<AuditRequest>(`${BASE_PATH}/${id}`, input)
  },

  remove(id: number): Promise<AuditRequest> {
    return del<AuditRequest>(`${BASE_PATH}/${id}`)
  },
}
