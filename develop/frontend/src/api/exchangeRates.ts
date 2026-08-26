import { apiRequest } from './http'

export interface ExchangeRateLatest {
  currencyPairDefinitionId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
  precision: number
  brandId: number
  brandCode: string
  rate: number | null
  depositRate: number | null
  withdrawalRate: number | null
  rateMinute: string | null
  source: string | null
}

export interface ExchangeRateSyncUpdatedItem {
  currencyPairDefinitionId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
  brandId: number
  brandCode: string
  rate: number
  depositRate: number
  withdrawalRate: number
}

export interface ExchangeRateSyncSkippedItem {
  currencyPairDefinitionId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
  reason: string
}

export interface ExchangeRateSyncResponse {
  syncedAt: string
  updated: ExchangeRateSyncUpdatedItem[]
  skipped: ExchangeRateSyncSkippedItem[]
}

export function fetchLatestExchangeRates(
  brandId: number,
): Promise<ExchangeRateLatest[]> {
  return apiRequest<ExchangeRateLatest[]>(
    `/exchange-rates/latest?brandId=${brandId}`,
  )
}

export function syncExchangeRates(): Promise<ExchangeRateSyncResponse> {
  return apiRequest<ExchangeRateSyncResponse>('/exchange-rates/sync', {
    method: 'POST',
  })
}
