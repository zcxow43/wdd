import { get, post } from '../api/client'
import type { AuditRequest, AuditStatus } from './types'

const BASE_PATH = '/api/audit-requests'

export interface AuditRequestListParams {
  entityType?: string
  status?: AuditStatus
}

/**
 * Standalone client for the generic `/api/audit-requests` API
 * (specs/backend/audit.md). Contains no entity-specific logic — any consumer's
 * requests flow through the same three calls below.
 */
export const auditApi = {
  list(params: AuditRequestListParams = {}): Promise<AuditRequest[]> {
    const query = new URLSearchParams()
    if (params.entityType) {
      query.set('entityType', params.entityType)
    }
    if (params.status) {
      query.set('status', params.status)
    }
    const qs = query.toString()
    return get<AuditRequest[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },

  approve(id: number, reviewedBy?: string): Promise<AuditRequest> {
    return post<AuditRequest>(`${BASE_PATH}/${id}/approve`, reviewedBy ? { reviewedBy } : {})
  },

  reject(id: number, rejectReason: string, reviewedBy?: string): Promise<AuditRequest> {
    return post<AuditRequest>(`${BASE_PATH}/${id}/reject`, {
      rejectReason,
      ...(reviewedBy ? { reviewedBy } : {}),
    })
  },
}
