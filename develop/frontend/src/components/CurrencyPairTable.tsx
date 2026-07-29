import type { CurrencyPair } from '../types/currencyPair'
import './CurrencyPairTable.css'

interface CurrencyPairTableProps {
  pairs: CurrencyPair[]
  onEdit: (pair: CurrencyPair) => void
  onDelete: (pair: CurrencyPair) => void
}

function formatRate(rate: number | null): string {
  if (rate === null) {
    return '—'
  }
  return Number(rate.toFixed(8)).toString()
}

export function CurrencyPairTable({ pairs, onEdit, onDelete }: CurrencyPairTableProps) {
  if (pairs.length === 0) {
    return <div className="table-empty">目前沒有符合條件的幣種對</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 90 }}>品牌</th>
          <th style={{ width: 80 }}>基準幣別</th>
          <th style={{ width: 80 }}>對應幣別</th>
          <th style={{ width: 120 }}>匯率</th>
          <th style={{ width: 100 }}>匯率類型</th>
          <th style={{ width: 110 }}>狀態</th>
          <th style={{ width: 140 }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {pairs.map((pair) => (
          <tr key={pair.id}>
            <td>
              <span className="currency-code">{pair.brandCode}</span>
            </td>
            <td>
              <span className="currency-code">{pair.baseCurrencyCode}</span>
            </td>
            <td>
              <span className="currency-code">{pair.quoteCurrencyCode}</span>
            </td>
            <td className="align-right">{formatRate(pair.rate)}</td>
            <td className="align-center">
              <span
                className={`rate-type-badge ${
                  pair.rateType === 'AUTO' ? 'rate-type-badge--auto' : 'rate-type-badge--manual'
                }`}
              >
                {pair.rateType === 'AUTO' ? '自動' : '手動'}
              </span>
            </td>
            <td className="align-center">
              <span
                className={`status-badge ${pair.active ? 'status-badge--active' : 'status-badge--inactive'}`}
                role="img"
                aria-label={pair.active ? '啟用' : '停用'}
              >
                <span className="status-dot" aria-hidden="true" />
                {pair.active ? 'ACTIVE' : 'INACTIVE'}
              </span>
            </td>
            <td>
              <div className="action-buttons">
                <button type="button" className="action-btn" onClick={() => onEdit(pair)}>
                  Edit
                </button>
                <button type="button" className="action-btn action-btn--danger" onClick={() => onDelete(pair)}>
                  Delete
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
