import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'
import './CurrencyPairDefinitionTable.css'

interface CurrencyPairDefinitionTableProps {
  definitions: CurrencyPairDefinition[]
  onEdit: (definition: CurrencyPairDefinition) => void
  onDelete: (definition: CurrencyPairDefinition) => void
}

export function CurrencyPairDefinitionTable({ definitions, onEdit, onDelete }: CurrencyPairDefinitionTableProps) {
  if (definitions.length === 0) {
    return <div className="table-empty">目前尚無幣種對主檔</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 100 }}>基準幣別</th>
          <th style={{ width: 100 }}>對應幣別</th>
          <th style={{ width: 100 }}>正向精度</th>
          <th style={{ width: 100 }}>反向精度</th>
          <th style={{ width: 140 }}>操作</th>
        </tr>
      </thead>
      <tbody>
        {definitions.map((definition) => (
          <tr key={definition.id}>
            <td>
              <span className="currency-code">{definition.baseCurrencyCode}</span>
            </td>
            <td>
              <span className="currency-code">{definition.quoteCurrencyCode}</span>
            </td>
            <td className="align-center">{definition.forwardPrecision}</td>
            <td className="align-center">{definition.reversePrecision}</td>
            <td>
              <div className="action-buttons">
                <button type="button" className="action-btn" onClick={() => onEdit(definition)}>
                  編輯
                </button>
                <button
                  type="button"
                  className="action-btn action-btn--danger"
                  onClick={() => onDelete(definition)}
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
