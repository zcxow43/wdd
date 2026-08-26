import { apiRequest } from './http'

export interface CurrencyPairDefinition {
  id: number
  baseCurrencyId: number
  baseCurrencyCode: string
  quoteCurrencyId: number
  quoteCurrencyCode: string
  precision: number
  createdAt: string
  updatedAt: string
}

export interface CurrencyPair {
  id: number
  currencyPairDefinitionId: number
  baseCurrencyCode?: string
  quoteCurrencyCode?: string
  brandId: number
  brandCode: string
  rateType: string
  rate: number | null
  active: boolean
  spreadGroupId?: number | null
  spreadGroupName?: string | null
  depositRate: number | null
  withdrawalRate: number | null
  createdAt: string
  updatedAt: string
}

export interface CurrencyPairUpdateRequest {
  rateType?: string
  rate?: number | null
  active?: boolean
}

/**
 * Response body for a submitted (not yet applied) change to a `CurrencyPair`.
 * `PUT`/`DELETE /api/currency-pairs/{id}` now return `202` with this shape —
 * the change becomes a pending audit request instead of being applied
 * immediately; `entityId` matches the row's `id`.
 */
export interface CurrencyPairAuditSubmission {
  auditRequestId: number
  status: string
  entityType: string
  actionType: string
  entityId: number
  summary: string
}

export interface CurrencyPairDefinitionCreateRequest {
  baseCurrencyId: number
  quoteCurrencyId: number
  precision: number
}

export interface CurrencyPairDefinitionCreateResponse
  extends CurrencyPairDefinition {
  currencyPairs: CurrencyPair[]
}

export function fetchCurrencyPairDefinitions(): Promise<
  CurrencyPairDefinition[]
> {
  return apiRequest<CurrencyPairDefinition[]>('/currency-pair-definitions')
}

export function createCurrencyPairDefinition(
  request: CurrencyPairDefinitionCreateRequest,
): Promise<CurrencyPairDefinitionCreateResponse> {
  return apiRequest<CurrencyPairDefinitionCreateResponse>(
    '/currency-pair-definitions',
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
  )
}

export function updateCurrencyPairDefinitionPrecision(
  id: number,
  precision: number,
): Promise<CurrencyPairDefinition> {
  return apiRequest<CurrencyPairDefinition>(
    `/currency-pair-definitions/${id}`,
    {
      method: 'PUT',
      body: JSON.stringify({ precision }),
    },
  )
}

export function deleteCurrencyPairDefinition(id: number): Promise<void> {
  return apiRequest<void>(`/currency-pair-definitions/${id}`, {
    method: 'DELETE',
  })
}

export function fetchCurrencyPairsByDefinition(
  currencyPairDefinitionId: number,
): Promise<CurrencyPair[]> {
  return apiRequest<CurrencyPair[]>(
    `/currency-pairs?currencyPairDefinitionId=${currencyPairDefinitionId}`,
  )
}

export function fetchCurrencyPairsByBrand(
  brandId: number,
): Promise<CurrencyPair[]> {
  return apiRequest<CurrencyPair[]>(`/currency-pairs?brandId=${brandId}`)
}

export function updateCurrencyPair(
  id: number,
  request: CurrencyPairUpdateRequest,
): Promise<CurrencyPairAuditSubmission> {
  return apiRequest<CurrencyPairAuditSubmission>(`/currency-pairs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteCurrencyPair(
  id: number,
): Promise<CurrencyPairAuditSubmission> {
  return apiRequest<CurrencyPairAuditSubmission>(`/currency-pairs/${id}`, {
    method: 'DELETE',
  })
}
