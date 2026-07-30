import { apiClient } from './client'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
  CurrencyPairDefinitionUpdateInput,
} from '../types/currencyPairDefinition'

const BASE_PATH = '/api/currency-pair-definitions'

export interface CurrencyPairDefinitionListParams {
  baseCurrencyId?: number
  quoteCurrencyId?: number
}

/**
 * Brand-agnostic currency pair master data. Unlike `currencyPairApi`, this
 * feature is not audit-gated — every call resolves the entity directly
 * (201/200/204), per specs/frontend/currency-pair-definition.md.
 */
export const currencyPairDefinitionApi = {
  list: (params?: CurrencyPairDefinitionListParams) => {
    const query = new URLSearchParams()
    if (params?.baseCurrencyId !== undefined) query.set('baseCurrencyId', String(params.baseCurrencyId))
    if (params?.quoteCurrencyId !== undefined) query.set('quoteCurrencyId', String(params.quoteCurrencyId))
    const qs = query.toString()
    return apiClient.get<CurrencyPairDefinition[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },
  create: (input: CurrencyPairDefinitionCreateInput) =>
    apiClient.post<CurrencyPairDefinition>(BASE_PATH, input),
  update: (id: number, input: CurrencyPairDefinitionUpdateInput) =>
    apiClient.put<CurrencyPairDefinition>(`${BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`${BASE_PATH}/${id}`),
}
