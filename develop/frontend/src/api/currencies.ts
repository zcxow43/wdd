import { apiRequest } from './http'

export interface Currency {
  id: number
  code: string
  name: string
  symbol: string
  decimalPlaces: number
  createdAt: string
  updatedAt: string
}

export interface CurrencyCreateRequest {
  code: string
  name: string
  symbol: string
  decimalPlaces: number
}

export interface CurrencyUpdateRequest {
  name: string
  symbol: string
  decimalPlaces: number
}

export function fetchCurrencies(): Promise<Currency[]> {
  return apiRequest<Currency[]>('/currencies')
}

export function createCurrency(
  request: CurrencyCreateRequest,
): Promise<Currency> {
  return apiRequest<Currency>('/currencies', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateCurrency(
  id: number,
  request: CurrencyUpdateRequest,
): Promise<Currency> {
  return apiRequest<Currency>(`/currencies/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteCurrency(id: number): Promise<void> {
  return apiRequest<void>(`/currencies/${id}`, {
    method: 'DELETE',
  })
}
