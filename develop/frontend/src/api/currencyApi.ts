import { apiClient } from './client'
import type { Currency, CurrencyInput } from '../types/currency'

const BASE_PATH = '/api/currencies'

export const currencyApi = {
  list: (active?: boolean) => {
    const query = active === undefined ? '' : `?active=${active}`
    return apiClient.get<Currency[]>(`${BASE_PATH}${query}`)
  },
  create: (input: CurrencyInput) => apiClient.post<Currency>(BASE_PATH, input),
  update: (id: number, input: Partial<CurrencyInput>) =>
    apiClient.put<Currency>(`${BASE_PATH}/${id}`, input),
  remove: (id: number) => apiClient.delete<void>(`${BASE_PATH}/${id}`),
}
