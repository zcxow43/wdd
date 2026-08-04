import type { DiffRenderer } from '../audit/diffRegistry'

/**
 * `renderCurrencyPairDiff` — the dedicated `DiffRenderer` for `entityType:
 * "CURRENCY_PAIR"` (specs/frontend/currency-pair-approval.md). Renders the known
 * field labels in a fixed order (品牌/基準幣別/對應幣別/匯率/匯率類型/狀態),
 * reusing the audit module's generic diff table/row-changed CSS classes
 * (`generic-diff-table`/`generic-diff-row-changed`, `diffRegistry.css`) for visual
 * consistency with the fallback renderer.
 *
 * Unlike most renderers, this one is registered with the review modal opting into
 * receiving `null` directly for CREATE (`before: null`) / DELETE (`after: null`)
 * requests (via `hasDiffRenderer`, see `AuditReviewModal.tsx`) — historical CREATE
 * requests still exist in the audit log from before the create action was removed,
 * and this renderer must keep showing their real field values on the populated
 * side, not the modal's blanket placeholder. It never highlights a row against a
 * null side.
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

interface FieldSpec {
  key: keyof CurrencyPairSnapshot
  label: string
  format: (value: unknown) => string
}

function formatRate(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

function formatRateType(value: unknown): string {
  return value === 'MANUAL' ? '手動' : '自動'
}

function formatActive(value: unknown): string {
  return value ? '啟用' : '停用'
}

function formatPlain(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

const FIELDS: FieldSpec[] = [
  { key: 'brandCode', label: '品牌', format: formatPlain },
  { key: 'baseCurrencyCode', label: '基準幣別', format: formatPlain },
  { key: 'quoteCurrencyCode', label: '對應幣別', format: formatPlain },
  { key: 'rate', label: '匯率', format: formatRate },
  { key: 'rateType', label: '匯率類型', format: formatRateType },
  { key: 'active', label: '狀態', format: formatActive },
]

export const renderCurrencyPairDiff: DiffRenderer = (before, after) => {
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
        {FIELDS.map(({ key, label, format }) => {
          const beforeValue = before ? (before as Record<string, unknown>)[key] : undefined
          const afterValue = after ? (after as Record<string, unknown>)[key] : undefined
          const changed =
            before !== null && after !== null && format(beforeValue) !== format(afterValue)

          return (
            <tr key={key} className={changed ? 'generic-diff-row-changed' : undefined}>
              <td className="generic-diff-key">{label}</td>
              <td className="generic-diff-before">{before === null ? '—' : format(beforeValue)}</td>
              <td className="generic-diff-after">{after === null ? '—' : format(afterValue)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
