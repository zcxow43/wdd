import type { DiffRenderer } from '../audit/diffRegistry'
import '../audit/diffRegistry.css'

/**
 * Shape of `before`/`after` for a `CURRENCY_PAIR` audit request, matching
 * `CurrencyPairAuditHandler`'s snapshot on the backend
 * (specs/backend/currency-pair-approval.md).
 */
interface CurrencyPairSnapshot {
  brandId: number
  brandCode: string
  baseCurrencyId: number
  baseCurrencyCode: string
  quoteCurrencyId: number
  quoteCurrencyCode: string
  rate: number | null
  rateType: 'MANUAL' | 'AUTO'
  active: boolean
}

interface FieldDef {
  label: string
  format: (snapshot: CurrencyPairSnapshot) => string
}

function formatRate(rate: number | null): string {
  if (rate === null || rate === undefined) return '—'
  return Number(rate.toFixed(8)).toString()
}

// Fixed field order per specs/frontend/currency-pair-approval.md: 品牌/基準幣別/對應幣別/匯率/匯率類型/狀態
const FIELDS: FieldDef[] = [
  { label: '品牌', format: (snapshot) => snapshot.brandCode ?? '—' },
  { label: '基準幣別', format: (snapshot) => snapshot.baseCurrencyCode ?? '—' },
  { label: '對應幣別', format: (snapshot) => snapshot.quoteCurrencyCode ?? '—' },
  { label: '匯率', format: (snapshot) => formatRate(snapshot.rate) },
  { label: '匯率類型', format: (snapshot) => (snapshot.rateType === 'AUTO' ? '自動' : '手動') },
  { label: '狀態', format: (snapshot) => (snapshot.active ? '啟用' : '停用') },
]

/**
 * Renders a labeled 修改前/修改後 comparison for a `CURRENCY_PAIR` audit
 * request, registered against `entityType: "CURRENCY_PAIR"` in the audit
 * module's diff-renderer registry. Reuses the generic diff table's styling
 * (`audit-generic-diff-table` / `audit-generic-diff-row--changed`).
 *
 * Unlike most renderers, this one is also invoked directly with a `null`
 * `before` (CREATE) or `after` (DELETE) — see `AuditReviewModal`'s
 * `hasDiffRenderer` check — so it can show the real field values on the
 * populated side instead of a blanket placeholder.
 */
export const renderCurrencyPairDiff: DiffRenderer = (before, after) => {
  const beforeSnapshot = before as CurrencyPairSnapshot | null
  const afterSnapshot = after as CurrencyPairSnapshot | null

  return (
    <table className="audit-generic-diff-table">
      <thead>
        <tr>
          <th>欄位</th>
          <th>修改前</th>
          <th>修改後</th>
        </tr>
      </thead>
      <tbody>
        {FIELDS.map((field) => {
          const beforeValue = beforeSnapshot ? field.format(beforeSnapshot) : '—'
          const afterValue = afterSnapshot ? field.format(afterSnapshot) : '—'
          const changed = beforeSnapshot !== null && afterSnapshot !== null && beforeValue !== afterValue
          return (
            <tr key={field.label} className={changed ? 'audit-generic-diff-row--changed' : undefined}>
              <td>{field.label}</td>
              <td>{beforeValue}</td>
              <td>{afterValue}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
