import { apiRequest } from './http'

export type AuditEntityType =
  | 'CURRENCY_PAIR'
  | 'BRAND_SPREAD'
  | 'SPREAD_GROUP'
  | 'SPREAD_GROUP_MEMBER'

export type AuditActionType = 'CREATE' | 'UPDATE' | 'DELETE'

export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface AuditRequestSummary {
  id: number
  entityType: AuditEntityType
  actionType: AuditActionType
  entityId: number
  brandId: number | null
  summary: string
  status: AuditStatus
  requestedBy: string
  requestedAt: string
  reviewedBy: string | null
  reviewedAt: string | null
  reviewComment: string | null
  applyError: string | null
}

export interface AuditRequestDetail extends AuditRequestSummary {
  beforeData: Record<string, unknown> | null
  afterData: Record<string, unknown> | null
}

export interface AuditRequestListFilters {
  status?: string
  entityType?: string
  brandId?: number
}

export function fetchAuditRequests(
  filters: AuditRequestListFilters = {},
): Promise<AuditRequestSummary[]> {
  const params = new URLSearchParams()
  if (filters.status) {
    params.set('status', filters.status)
  }
  if (filters.entityType) {
    params.set('entityType', filters.entityType)
  }
  if (filters.brandId != null) {
    params.set('brandId', String(filters.brandId))
  }
  const query = params.toString()
  return apiRequest<AuditRequestSummary[]>(
    `/audit-requests${query ? `?${query}` : ''}`,
  )
}

export function fetchAuditRequest(id: number): Promise<AuditRequestDetail> {
  return apiRequest<AuditRequestDetail>(`/audit-requests/${id}`)
}

export function approveAuditRequest(
  id: number,
  comment: string,
  actor: string,
): Promise<AuditRequestDetail> {
  return apiRequest<AuditRequestDetail>(`/audit-requests/${id}/approve`, {
    method: 'POST',
    headers: { 'X-Actor': actor },
    body: JSON.stringify({ comment }),
  })
}

export function rejectAuditRequest(
  id: number,
  comment: string,
  actor: string,
): Promise<AuditRequestDetail> {
  return apiRequest<AuditRequestDetail>(`/audit-requests/${id}/reject`, {
    method: 'POST',
    headers: { 'X-Actor': actor },
    body: JSON.stringify({ comment }),
  })
}
