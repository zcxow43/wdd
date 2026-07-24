import type { Currency } from '../types/currency'
import './CurrencyTable.css'

interface CurrencyTableProps {
  currencies: Currency[]
  onEdit: (currency: Currency) => void
  onDelete: (currency: Currency) => void
}

export function CurrencyTable({ currencies, onEdit, onDelete }: CurrencyTableProps) {
  if (currencies.length === 0) {
    return <div className="currency-table-empty">目前沒有符合條件的幣種</div>
  }

  return (
    <table className="currency-table">
      <thead>
        <tr>
          <th style={{ width: 80 }}>Code</th>
          <th>Name</th>
          <th style={{ width: 120 }}>中文名稱</th>
          <th style={{ width: 80 }}>Symbol</th>
          <th style={{ width: 100 }}>Decimal Places</th>
          <th style={{ width: 80 }}>Active</th>
          <th style={{ width: 120 }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {currencies.map((currency) => (
          <tr key={currency.id}>
            <td className="currency-code">{currency.code}</td>
            <td>{currency.name}</td>
            <td>{currency.nameZh || '-'}</td>
            <td className="align-center">{currency.symbol || '-'}</td>
            <td className="align-center">{currency.decimalPlaces}</td>
            <td className="align-center">
              <span
                className={`status-dot ${currency.active ? 'status-dot--active' : 'status-dot--inactive'}`}
                role="img"
                aria-label={currency.active ? '啟用' : '停用'}
              />
            </td>
            <td className="currency-actions">
              <button type="button" className="btn btn-link" onClick={() => onEdit(currency)}>
                Edit
              </button>
              <button type="button" className="btn btn-link btn-link--danger" onClick={() => onDelete(currency)}>
                Delete
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
