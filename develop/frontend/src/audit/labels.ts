import type { AuditActionType, AuditStatus } from './types'

export const ACTION_TYPE_LABELS: Record<AuditActionType, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '刪除',
}

export const STATUS_LABELS: Record<AuditStatus, string> = {
  PENDING: '待審核',
  APPROVED: '已核准',
  REJECTED: '已拒絕',
}
