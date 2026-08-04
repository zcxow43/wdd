import type { DiffRenderer } from '../audit/diffRegistry'

/**
 * `renderSpreadDefaultDiff` — the dedicated `DiffRenderer` for `entityType:
 * "SPREAD_DEFAULT"` (specs/frontend/spread.md), modeled directly on
 * `CurrencyPairDiff.tsx`. Renders the fixed field order 品牌/入金點差/出金點差 in a
 * two-column 修改前/修改後 table, reusing the audit module's generic diff table/
 * row-changed CSS classes. Always an `UPDATE` in practice (a `spread_default` row
 * is never created/deleted through the API) but handles a `null` `before`/`after`
 * directly anyway, matching `renderCurrencyPairDiff`'s contract with
 * `hasDiffRenderer`.
 */

interface SpreadDefaultSnapshot {
  brandCode: string
  depositSpread: number
  withdrawSpread: number
}

interface FieldSpec {
  key: keyof SpreadDefaultSnapshot
  label: string
}

function formatPlain(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

const FIELDS: FieldSpec[] = [
  { key: 'brandCode', label: '品牌' },
  { key: 'depositSpread', label: '入金點差' },
  { key: 'withdrawSpread', label: '出金點差' },
]

export const renderSpreadDefaultDiff: DiffRenderer = (before, after) => {
  return (
    <table className="generic-diff-table">
      <thead>
        <tr>
          <th>欄位</th>
          <th>修改前</th>
          <th>修改後</th>
        </tr>
      </thead>
      <tbody>
        {FIELDS.map(({ key, label }) => {
          const beforeValue = before ? (before as Record<string, unknown>)[key] : undefined
          const afterValue = after ? (after as Record<string, unknown>)[key] : undefined
          const changed =
            before !== null && after !== null && formatPlain(beforeValue) !== formatPlain(afterValue)

          return (
            <tr key={key} className={changed ? 'generic-diff-row-changed' : undefined}>
              <td className="generic-diff-key">{label}</td>
              <td className="generic-diff-before">{before === null ? '—' : formatPlain(beforeValue)}</td>
              <td className="generic-diff-after">{after === null ? '—' : formatPlain(afterValue)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
