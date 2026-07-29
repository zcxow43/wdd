import type { AuditRequest } from './types'
import './AuditRequestTable.css'

interface AuditRequestTableProps {
  requests: AuditRequest[]
  onView: (request: AuditRequest) => void
}

const ACTION_TYPE_LABELS: Record<AuditRequest['actionType'], string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '刪除',
}

const STATUS_LABELS: Record<AuditRequest['status'], string> = {
  PENDING: '待審核',
  APPROVED: '已核准',
  REJECTED: '已拒絕',
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

export function AuditRequestTable({ requests, onView }: AuditRequestTableProps) {
  if (requests.length === 0) {
    return <div className="table-empty">目前沒有符合條件的審核申請</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 90 }}>類型</th>
          <th>摘要</th>
          <th style={{ width: 140 }}>申請人</th>
          <th style={{ width: 160 }}>申請時間</th>
          <th style={{ width: 100 }}>狀態</th>
          <th style={{ width: 100 }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {requests.map((request) => (
          <tr key={request.id}>
            <td>
              <span
                className={`audit-action-badge audit-action-badge--${request.actionType.toLowerCase()}`}
              >
                {ACTION_TYPE_LABELS[request.actionType]}
              </span>
            </td>
            <td>{request.summary ?? '—'}</td>
            <td>{request.requestedBy ?? '—'}</td>
            <td>{formatDateTime(request.requestedAt)}</td>
            <td>
              <span className={`audit-status-badge audit-status-badge--${request.status.toLowerCase()}`}>
                {STATUS_LABELS[request.status]}
              </span>
            </td>
            <td>
              <div className="action-buttons">
                <button type="button" className="action-btn" onClick={() => onView(request)}>
                  查看
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
