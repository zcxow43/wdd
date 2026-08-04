import { get, post, put, del } from './client'
import type { Currency, CurrencyInput } from '../types/currency'

const BASE_PATH = '/api/currencies'

export const currencyApi = {
  list(): Promise<Currency[]> {
    return get<Currency[]>(BASE_PATH)
  },

  create(input: CurrencyInput): Promise<Currency> {
    return post<Currency>(BASE_PATH, input)
  },

  update(id: number, input: Omit<CurrencyInput, 'code'>): Promise<Currency> {
    return put<Currency>(`${BASE_PATH}/${id}`, input)
  },

  remove(id: number): Promise<void> {
    return del<void>(`${BASE_PATH}/${id}`)
  },
}
