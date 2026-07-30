import type { DiffRenderer } from '../audit/diffRegistry'
import '../audit/diffRegistry.css'

/**
 * Shape of `before`/`after` for a `SPREAD_DEFAULT` audit request, matching
 * `SpreadDefaultAuditHandler`'s snapshot on the backend
 * (specs/backend/spread.md).
 */
interface SpreadDefaultSnapshot {
  brandId: number
  brandCode: string
  depositSpread: number
  withdrawSpread: number
}

interface FieldDef {
  label: string
  format: (snapshot: SpreadDefaultSnapshot) => string
}

function formatSpread(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return Number(value.toFixed(8)).toString()
}

// Fixed field order per specs/frontend/spread.md: 品牌/入金點差/出金點差
const FIELDS: FieldDef[] = [
  { label: '品牌', format: (snapshot) => snapshot.brandCode ?? '—' },
  { label: '入金點差', format: (snapshot) => formatSpread(snapshot.depositSpread) },
  { label: '出金點差', format: (snapshot) => formatSpread(snapshot.withdrawSpread) },
]

/**
 * Renders a labeled 修改前/修改後 comparison for a `SPREAD_DEFAULT` audit
 * request, registered against `entityType: "SPREAD_DEFAULT"` in the audit
 * module's diff-renderer registry. Modeled directly on
 * `renderCurrencyPairDiff` (specs/frontend/currency-pair-approval.md).
 *
 * In practice `SPREAD_DEFAULT` requests are always `UPDATE`s (a
 * `spread_default` row is never created/deleted through the API), but this
 * still handles a `null` `before`/`after` defensively, same as
 * `renderCurrencyPairDiff`.
 */
export const renderSpreadDefaultDiff: DiffRenderer = (before, after) => {
  const beforeSnapshot = before as SpreadDefaultSnapshot | null
  const afterSnapshot = after as SpreadDefaultSnapshot | null

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
