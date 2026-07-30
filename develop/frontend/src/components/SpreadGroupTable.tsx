import type { ReactNode } from 'react'
import type { SpreadGroup } from '../types/spread'
import './SpreadGroupTable.css'

interface SpreadGroupTableProps {
  groups: SpreadGroup[]
  pendingIds: Set<number>
  onEdit: (group: SpreadGroup) => void
  onDelete: (group: SpreadGroup) => void
}

function formatSpread(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return Number(value.toFixed(8)).toString()
}

function renderMemberBadges(group: SpreadGroup): ReactNode {
  if (group.members.length === 0) return '—'
  return (
    <span className="spread-group-pair-badges">
      {group.members.map((member, index) => (
        <span key={member.currencyPairId}>
          {index > 0 && ', '}
          <span className="currency-code">
            {member.baseCurrencyCode}/{member.quoteCurrencyCode}
          </span>
        </span>
      ))}
    </span>
  )
}

export function SpreadGroupTable({ groups, pendingIds, onEdit, onDelete }: SpreadGroupTableProps) {
  if (groups.length === 0) {
    return <div className="table-empty">目前沒有點差群組</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 160 }}>名稱</th>
          <th style={{ width: 100 }}>入金點差</th>
          <th style={{ width: 100 }}>出金點差</th>
          <th>幣種對</th>
          <th style={{ width: 140 }}>操作</th>
        </tr>
      </thead>
      <tbody>
        {groups.map((group) => {
          const isPending = pendingIds.has(group.id)
          return (
            <tr key={group.id}>
              <td>{group.name}</td>
              <td className="align-right">{formatSpread(group.depositSpread)}</td>
              <td className="align-right">{formatSpread(group.withdrawSpread)}</td>
              <td>{renderMemberBadges(group)}</td>
              <td>
                <div className="action-buttons">
                  {isPending && <span className="pending-badge">審核中</span>}
                  <button type="button" className="action-btn" onClick={() => onEdit(group)} disabled={isPending}>
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
