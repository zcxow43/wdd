import type { Currency } from '../types/currency'
import './CurrencyTable.css'

interface CurrencyTableProps {
  currencies: Currency[]
  loading: boolean
  onEdit: (currency: Currency) => void
  onDelete: (currency: Currency) => void
}

export function CurrencyTable({ currencies, loading, onEdit, onDelete }: CurrencyTableProps) {
  if (loading) {
    return (
      <div className="table-empty" role="status">
        載入中...
      </div>
    )
  }

  if (currencies.length === 0) {
    return <div className="table-empty">目前沒有幣種資料</div>
  }

  return (
    <table className="currency-table data-table">
      <thead>
        <tr>
          <th className="col-code">Code</th>
          <th className="col-name">Name</th>
          <th className="col-name-zh">中文名稱</th>
          <th className="col-symbol">Symbol</th>
          <th className="col-decimal">Decimal Places</th>
          <th className="col-actions">Actions</th>
        </tr>
      </thead>
      <tbody>
        {currencies.map((currency) => (
          <tr key={currency.id}>
            <td className="col-code currency-code">{currency.code}</td>
            <td className="col-name">{currency.name}</td>
            <td className="col-name-zh">{currency.nameZh || '—'}</td>
            <td className="col-symbol">{currency.symbol || '—'}</td>
            <td className="col-decimal">{currency.decimalPlaces}</td>
            <td className="col-actions">
              <div className="action-buttons">
                <button type="button" className="action-btn" onClick={() => onEdit(currency)}>
                  編輯
                </button>
                <button
                  type="button"
                  className="action-btn action-btn--danger"
                  onClick={() => onDelete(currency)}
                >
                  刪除
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
