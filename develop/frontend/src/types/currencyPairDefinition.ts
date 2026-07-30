export interface CurrencyPairDefinition {
  id: number
  baseCurrencyId: number
  baseCurrencyCode: string
  quoteCurrencyId: number
  quoteCurrencyCode: string
  forwardPrecision: number
  reversePrecision: number
  createdAt: string
  updatedAt: string
}

export interface CurrencyPairDefinitionCreateInput {
  baseCurrencyId: number
  quoteCurrencyId: number
  forwardPrecision: number
  reversePrecision: number
}

export interface CurrencyPairDefinitionUpdateInput {
  forwardPrecision: number
  reversePrecision: number
}
