import type { CurrencyPair } from '../types/currencyPair'
import './CurrencyPairTable.css'

interface CurrencyPairTableProps {
  pairs: CurrencyPair[]
  loading: boolean
  /** ids of pairs with a `PENDING` audit request against them — badged + Edit/Delete disabled. */
  pendingIds: Set<number>
  onEdit: (pair: CurrencyPair) => void
  onDelete: (pair: CurrencyPair) => void
}

function formatRate(rate: number | null): string {
  if (rate === null) {
    return '—'
  }
  // Up to 8dp, trailing zeros trimmed (e.g. 32.5, 157.3).
  return Number(rate.toFixed(8)).toString()
}

export function CurrencyPairTable({ pairs, loading, pendingIds, onEdit, onDelete }: CurrencyPairTableProps) {
  if (loading) {
    return (
      <div className="table-empty" role="status">
        載入中...
      </div>
    )
  }

  if (pairs.length === 0) {
    return <div className="table-empty">目前沒有幣種對資料</div>
  }

  return (
    <table className="currency-pair-table data-table">
      <thead>
        <tr>
          <th className="col-brand">品牌</th>
          <th className="col-currency">基準幣別</th>
          <th className="col-currency">對應幣別</th>
          <th className="col-rate">匯率</th>
          <th className="col-rate-type">匯率類型</th>
          <th className="col-status">狀態</th>
          <th className="col-actions">Actions</th>
        </tr>
      </thead>
      <tbody>
        {pairs.map((pair) => {
          const isPending = pendingIds.has(pair.id)
          return (
            <tr key={pair.id}>
              <td className="col-brand currency-code">{pair.brandCode}</td>
              <td className="col-currency currency-code">{pair.baseCurrencyCode}</td>
              <td className="col-currency currency-code">{pair.quoteCurrencyCode}</td>
              <td className="col-rate">{formatRate(pair.rate)}</td>
              <td className="col-rate-type">
                <span
                  className={`rate-type-badge rate-type-badge--${pair.rateType.toLowerCase()}`}
                >
                  {pair.rateType === 'MANUAL' ? '手動' : '自動'}
                </span>
              </td>
              <td className="col-status">
                <span className={`status-badge status-badge--${pair.active ? 'active' : 'inactive'}`}>
                  <span className="status-dot" />
                  {pair.active ? '啟用' : '停用'}
                </span>
              </td>
              <td className="col-actions">
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
                    onClick={() => onEdit(pair)}
                    disabled={isPending}
                  >
                    編輯
                  </button>
                  <button
                    type="button"
                    className="action-btn action-btn--danger"
                    onClick={() => onDelete(pair)}
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
