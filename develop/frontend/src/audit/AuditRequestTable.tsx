import type { AuditRequest } from './types'
import { ACTION_TYPE_LABELS, STATUS_LABELS } from './labels'
import { formatDateTime } from './format'
import './AuditRequestTable.css'

interface AuditRequestTableProps {
  requests: AuditRequest[]
  loading: boolean
  onView: (request: AuditRequest) => void
}

/**
 * Generic list table: 類型/摘要/申請人/申請時間/狀態/Actions. Zero per-entity-type
 * logic — 摘要 always renders the API's precomputed `summary` field as-is. An
 * 實體類型 (entity type) column is deliberately omitted while only one entity type
 * exists in the running system.
 */
export function AuditRequestTable({ requests, loading, onView }: AuditRequestTableProps) {
  if (loading) {
    return (
      <div className="table-empty" role="status">
        載入中...
      </div>
    )
  }

  if (requests.length === 0) {
    return <div className="table-empty">目前沒有審核申請</div>
  }

  return (
    <table className="audit-table data-table">
      <thead>
        <tr>
          <th className="col-action-type">類型</th>
          <th className="col-summary">摘要</th>
          <th className="col-requested-by">申請人</th>
          <th className="col-requested-at">申請時間</th>
          <th className="col-status">狀態</th>
          <th className="col-actions">Actions</th>
        </tr>
      </thead>
      <tbody>
        {requests.map((request) => (
          <tr key={request.id}>
            <td className="col-action-type">
              <span className={`audit-badge audit-badge-action-${request.actionType.toLowerCase()}`}>
                {ACTION_TYPE_LABELS[request.actionType]}
              </span>
            </td>
            <td className="col-summary">{request.summary ?? '—'}</td>
            <td className="col-requested-by">{request.requestedBy ?? '—'}</td>
            <td className="col-requested-at">{formatDateTime(request.requestedAt)}</td>
            <td className="col-status">
              <span className={`status-badge status-badge--${request.status.toLowerCase()}`}>
                <span className="status-dot" />
                {STATUS_LABELS[request.status]}
              </span>
            </td>
            <td className="col-actions">
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
