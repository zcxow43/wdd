export interface Currency {
  id: number
  code: string
  name: string
  nameZh: string | null
  symbol: string | null
  decimalPlaces: number
  createdAt: string
  updatedAt: string
}

export interface CurrencyInput {
  code: string
  name: string
  nameZh: string
  symbol: string
  decimalPlaces: number
}
