/**
 * Generic audit-request types, mirroring specs/backend/audit.md's `AuditRequestResponse`.
 * `entityType` is deliberately an open `string` — new entity types need no frontend type change.
 */
export type AuditActionType = 'CREATE' | 'UPDATE' | 'DELETE'
export type AuditStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AuditRequest {
  id: number
  entityType: string
  actionType: AuditActionType
  entityId: number | null
  status: AuditStatus
  summary: string | null
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  requestedBy: string | null
  requestedAt: string
  reviewedBy: string | null
  reviewedAt: string | null
  rejectReason: string | null
  createdAt: string
  updatedAt: string
}
