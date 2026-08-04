export interface SpreadDefault {
  id: number
  brandId: number
  brandCode: string
  depositSpread: number
  withdrawSpread: number
  createdAt: string
  updatedAt: string
}

export interface SpreadDefaultInput {
  depositSpread: number
  withdrawSpread: number
}

export interface SpreadGroupMember {
  currencyPairId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
}

export interface SpreadGroup {
  id: number
  brandId: number
  brandCode: string
  name: string
  depositSpread: number
  withdrawSpread: number
  members: SpreadGroupMember[]
  createdAt: string
  updatedAt: string
}

export interface SpreadGroupInput {
  brandId: number
  name: string
  depositSpread: number
  withdrawSpread: number
  currencyPairIds: number[]
}

export type SpreadSource = 'DEFAULT' | 'GROUP'

export interface SpreadResolution {
  currencyPairId: number
  brandId: number
  source: SpreadSource
  spreadGroupId: number | null
  spreadGroupName: string | null
  depositSpread: number
  withdrawSpread: number
}
