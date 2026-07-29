import { apiClient } from '../api/client'
import type { AuditActionType, AuditRequest, AuditStatus } from './types'

const BASE_PATH = '/api/audit-requests'

export interface AuditRequestListParams {
  entityType?: string
  status?: AuditStatus
  actionType?: AuditActionType
}

export const auditApi = {
  list: (params?: AuditRequestListParams) => {
    const query = new URLSearchParams()
    if (params?.entityType) query.set('entityType', params.entityType)
    if (params?.status) query.set('status', params.status)
    if (params?.actionType) query.set('actionType', params.actionType)
    const qs = query.toString()
    return apiClient.get<AuditRequest[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },
  approve: (id: number, reviewedBy?: string) =>
    apiClient.post<AuditRequest>(`${BASE_PATH}/${id}/approve`, reviewedBy ? { reviewedBy } : {}),
  reject: (id: number, rejectReason: string, reviewedBy?: string) =>
    apiClient.post<AuditRequest>(`${BASE_PATH}/${id}/reject`, {
      rejectReason,
      ...(reviewedBy ? { reviewedBy } : {}),
    }),
}
