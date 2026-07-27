import { apiClient } from './client'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'

const BASE_PATH = '/api/currency-pairs'

export interface CurrencyPairListParams {
  brandId?: number
  active?: boolean
}

export const currencyPairApi = {
  list: (params?: CurrencyPairListParams) => {
    const query = new URLSearchParams()
    if (params?.brandId !== undefined) query.set('brandId', String(params.brandId))
    if (params?.active !== undefined) query.set('active', String(params.active))
    const qs = query.toString()
    return apiClient.get<CurrencyPair[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },
  create: (input: CurrencyPairInput) => apiClient.post<CurrencyPair>(BASE_PATH, input),
  update: (id: number, input: Partial<CurrencyPairInput>) =>
    apiClient.put<CurrencyPair>(`${BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`${BASE_PATH}/${id}`),
}
