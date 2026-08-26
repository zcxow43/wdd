import { apiRequest } from './http'

/**
 * Every write below now goes through the audit workflow: instead of
 * applying immediately, the server records a pending request and responds
 * `202` with this shape. `entityId` is `null` for a group create (the
 * group itself does not exist until the request is approved).
 */
export interface SpreadAuditSubmission {
  auditRequestId: number
  status: string
  entityType: string
  actionType: string
  entityId: number | null
  summary: string
}

export interface BrandSpread {
  brandId: number
  brandCode: string
  depositSpreadPercent: number
  withdrawalSpreadPercent: number
  createdAt: string
  updatedAt: string
}

export interface BrandSpreadUpdateRequest {
  depositSpreadPercent: number
  withdrawalSpreadPercent: number
}

export interface SpreadGroup {
  id: number
  brandId: number
  brandCode: string
  name: string
  depositSpreadPercent: number
  withdrawalSpreadPercent: number
  memberCount: number
  createdAt: string
  updatedAt: string
}

export interface SpreadGroupMember {
  currencyPairId: number
  currencyPairDefinitionId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
  active: boolean
}

export interface SpreadGroupDetail extends SpreadGroup {
  members: SpreadGroupMember[]
}

export interface SpreadGroupCreateRequest {
  brandId: number
  name: string
  depositSpreadPercent: number
  withdrawalSpreadPercent: number
}

export interface SpreadGroupUpdateRequest {
  name?: string
  depositSpreadPercent?: number
  withdrawalSpreadPercent?: number
}

export interface SpreadGroupMemberAssignRequest {
  currencyPairIds: number[]
}

export interface EffectiveSpread {
  currencyPairId: number
  currencyPairDefinitionId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
  brandId: number
  brandCode: string
  spreadGroupId: number | null
  spreadGroupName: string | null
  source: 'GROUP' | 'DEFAULT'
  depositSpreadPercent: number
  withdrawalSpreadPercent: number
}

export function fetchBrandSpread(brandId: number): Promise<BrandSpread> {
  return apiRequest<BrandSpread>(`/brand-spreads/${brandId}`)
}

export function updateBrandSpread(
  brandId: number,
  request: BrandSpreadUpdateRequest,
): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>(`/brand-spreads/${brandId}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function fetchSpreadGroups(brandId: number): Promise<SpreadGroup[]> {
  return apiRequest<SpreadGroup[]>(`/spread-groups?brandId=${brandId}`)
}

export function fetchSpreadGroup(id: number): Promise<SpreadGroupDetail> {
  return apiRequest<SpreadGroupDetail>(`/spread-groups/${id}`)
}

export function createSpreadGroup(
  request: SpreadGroupCreateRequest,
): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>('/spread-groups', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function updateSpreadGroup(
  id: number,
  request: SpreadGroupUpdateRequest,
): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>(`/spread-groups/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export function deleteSpreadGroup(id: number): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>(`/spread-groups/${id}`, {
    method: 'DELETE',
  })
}

export function addSpreadGroupMembers(
  id: number,
  currencyPairIds: number[],
): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>(`/spread-groups/${id}/members`, {
    method: 'POST',
    body: JSON.stringify({ currencyPairIds } as SpreadGroupMemberAssignRequest),
  })
}

export function removeSpreadGroupMember(
  id: number,
  currencyPairId: number,
): Promise<SpreadAuditSubmission> {
  return apiRequest<SpreadAuditSubmission>(
    `/spread-groups/${id}/members/${currencyPairId}`,
    {
      method: 'DELETE',
    },
  )
}

export function fetchEffectiveSpreads(
  brandId: number,
): Promise<EffectiveSpread[]> {
  return apiRequest<EffectiveSpread[]>(
    `/spreads/effective?brandId=${brandId}`,
  )
}
