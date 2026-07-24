export interface Currency {
  id: number
  code: string
  name: string
  nameZh: string | null
  symbol: string | null
  decimalPlaces: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CurrencyInput {
  code: string
  name: string
  nameZh: string
  symbol: string
  decimalPlaces: number
  active: boolean
}

export type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'
