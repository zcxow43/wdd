import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'
import './CurrencyPairDefinitionTable.css'

interface CurrencyPairDefinitionTableProps {
  definitions: CurrencyPairDefinition[]
  loading: boolean
  /** Non-null when the initial/refetch load failed — takes precedence over loading/empty. */
  error: boolean
  onRetry: () => void
  onEdit: (definition: CurrencyPairDefinition) => void
  onDelete: (definition: CurrencyPairDefinition) => void
}

export function CurrencyPairDefinitionTable({
  definitions,
  loading,
  error,
  onRetry,
  onEdit,
  onDelete,
}: CurrencyPairDefinitionTableProps) {
  if (error) {
    return (
      <div className="table-empty table-error">
        <p>資料載入失敗</p>
        <button type="button" className="btn btn-secondary" onClick={onRetry}>
          重試
        </button>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="table-empty" role="status">
        載入中...
      </div>
    )
  }

  if (definitions.length === 0) {
    return <div className="table-empty">目前沒有幣種對主檔資料</div>
  }

  return (
    <table className="currency-pair-definition-table data-table">
      <thead>
        <tr>
          <th className="col-currency">基準幣別</th>
          <th className="col-currency">對應幣別</th>
          <th className="col-precision">正向精度</th>
          <th className="col-precision">反向精度</th>
          <th className="col-actions">操作</th>
        </tr>
      </thead>
      <tbody>
        {definitions.map((definition) => (
          <tr key={definition.id}>
            <td className="col-currency currency-code">{definition.baseCurrencyCode}</td>
            <td className="col-currency currency-code">{definition.quoteCurrencyCode}</td>
            <td className="col-precision">{definition.forwardPrecision}</td>
            <td className="col-precision">{definition.reversePrecision}</td>
            <td className="col-actions">
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
