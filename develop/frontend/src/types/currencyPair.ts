export type RateType = 'MANUAL' | 'AUTO'

export interface CurrencyPair {
  id: number
  brandId: number
  brandCode: string
  baseCurrencyId: number
  baseCurrencyCode: string
  quoteCurrencyId: number
  quoteCurrencyCode: string
  rate: number | null
  rateType: RateType
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CurrencyPairInput {
  brandId: number
  baseCurrencyId: number
  quoteCurrencyId: number
  rate: number | null
  rateType: RateType
  active: boolean
}
