import type { Currency } from '../types/currency'
import './CurrencyTable.css'

interface CurrencyTableProps {
  currencies: Currency[]
  onEdit: (currency: Currency) => void
  onDelete: (currency: Currency) => void
}

export function CurrencyTable({ currencies, onEdit, onDelete }: CurrencyTableProps) {
  if (currencies.length === 0) {
    return <div className="table-empty">目前沒有符合條件的幣種</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 80 }}>Code</th>
          <th>Name</th>
          <th style={{ width: 120 }}>中文名稱</th>
          <th style={{ width: 80 }}>Symbol</th>
          <th style={{ width: 100 }}>Decimal Places</th>
          <th style={{ width: 140 }}>Actions</th>
        </tr>
      </thead>
      <tbody>
        {currencies.map((currency) => (
          <tr key={currency.id}>
            <td>
              <span className="currency-code">{currency.code}</span>
            </td>
            <td>{currency.name}</td>
            <td>{currency.nameZh || '-'}</td>
            <td className="align-center">{currency.symbol || '-'}</td>
            <td className="align-center">{currency.decimalPlaces}</td>
            <td>
              <div className="action-buttons">
                <button type="button" className="action-btn" onClick={() => onEdit(currency)}>
                  Edit
                </button>
                <button type="button" className="action-btn action-btn--danger" onClick={() => onDelete(currency)}>
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
