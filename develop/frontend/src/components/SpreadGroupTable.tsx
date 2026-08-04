import type { SpreadGroup } from '../types/spread'
import './SpreadGroupTable.css'

interface SpreadGroupTableProps {
  groups: SpreadGroup[]
  /** ids of groups with a `PENDING` audit request against them — badged + Edit/Delete disabled. */
  pendingIds: Set<number>
  onEdit: (group: SpreadGroup) => void
  onDelete: (group: SpreadGroup) => void
}

export function SpreadGroupTable({ groups, pendingIds, onEdit, onDelete }: SpreadGroupTableProps) {
  if (groups.length === 0) {
    return <div className="table-empty">目前沒有點差群組</div>
  }

  return (
    <table className="spread-group-table data-table">
      <thead>
        <tr>
          <th className="col-spread-name">名稱</th>
          <th className="col-spread-value">入金點差</th>
          <th className="col-spread-value">出金點差</th>
          <th className="col-spread-members">幣種對</th>
          <th className="col-spread-actions">操作</th>
        </tr>
      </thead>
      <tbody>
        {groups.map((group) => {
          const isPending = pendingIds.has(group.id)
          return (
            <tr key={group.id}>
              <td className="col-spread-name">{group.name}</td>
              <td className="col-spread-value">{group.depositSpread}</td>
              <td className="col-spread-value">{group.withdrawSpread}</td>
              <td className="col-spread-members">
                {group.members.length === 0
                  ? '—'
                  : group.members.map((member, index) => (
                      <span key={member.currencyPairId}>
                        {index > 0 && ', '}
                        <span className="currency-code">{`${member.baseCurrencyCode}/${member.quoteCurrencyCode}`}</span>
                      </span>
                    ))}
              </td>
              <td className="col-spread-actions">
                <div className="action-buttons">
                  {isPending && (
                    <span className="status-badge status-badge--pending pending-badge">
                      <span className="status-dot" />
                      審核中
                    </span>
                  )}
                  <button
                    type="button"
                    className="action-btn"
                    onClick={() => onEdit(group)}
                    disabled={isPending}
                  >
                    編輯
                  </button>
                  <button
                    type="button"
                    className="action-btn action-btn--danger"
                    onClick={() => onDelete(group)}
                    disabled={isPending}
                  >
                    刪除
                  </button>
                </div>
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
