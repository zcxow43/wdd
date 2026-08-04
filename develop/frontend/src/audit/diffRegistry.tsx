import type { ReactNode } from 'react'
import './diffRegistry.css'

/**
 * By default a diff renderer only ever receives a genuinely-populated snapshot
 * (never `null`) — the review modal handles the CREATE/DELETE `null` cases
 * generically, once, before ever calling into the registry. A renderer registered
 * via `registerDiffRenderer` may instead opt into receiving `null` directly (see
 * `hasDiffRenderer` below and `AuditReviewModal`'s use of it) if it wants to render
 * the real field values on a CREATE/DELETE request's populated side rather than a
 * blanket placeholder — in which case it must handle a `null` `before`/`after`
 * itself. The `| null` in the signature accommodates both cases.
 */
export type DiffRenderer = (
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
) => ReactNode

const DIFF_RENDERERS: Record<string, DiffRenderer> = {}

/**
 * Consumers register their own renderer once, from their own module — e.g. a
 * module-level `registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)` call
 * in the currency-pair feature's own source file, executed once at app startup via
 * that module simply being imported. This audit module ships with zero
 * entity-specific renderers of its own; registration happens entirely from the
 * consumer side.
 */
export function registerDiffRenderer(entityType: string, renderer: DiffRenderer): void {
  DIFF_RENDERERS[entityType] = renderer
}

/**
 * Lets a caller (the review modal) distinguish "a dedicated renderer is registered
 * for this entityType" from "it would fall back to the generic renderer" — without
 * this module knowing anything about *why* that distinction matters to the caller
 * (e.g. opting a dedicated renderer into receiving a `null` before/after directly
 * instead of the modal's own blanket placeholder). Purely a registry-lookup helper;
 * carries no entity-specific knowledge itself.
 */
export function hasDiffRenderer(entityType: string): boolean {
  return entityType in DIFF_RENDERERS
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

/**
 * The only renderer this module ships with by default: iterates the raw key/value
 * pairs of `before`/`after` and lists them as-is in a simple two-column (well,
 * three-column: key/before/after) table, highlighting any key present in both with a
 * different value. Not pretty, but correct and non-breaking for any entity type
 * without a dedicated renderer.
 */
export function renderGenericDiff(
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
): ReactNode {
  const keys = Array.from(new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})]))

  if (keys.length === 0) {
    return <div className="generic-diff-empty">（無資料）</div>
  }

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
        {keys.map((key) => {
          const hasBefore = before !== null && Object.prototype.hasOwnProperty.call(before, key)
          const hasAfter = after !== null && Object.prototype.hasOwnProperty.call(after, key)
          const changed =
            hasBefore && hasAfter && formatValue(before?.[key]) !== formatValue(after?.[key])

          return (
            <tr key={key} className={changed ? 'generic-diff-row-changed' : undefined}>
              <td className="generic-diff-key">{key}</td>
              <td className="generic-diff-before">{hasBefore ? formatValue(before?.[key]) : '—'}</td>
              <td className="generic-diff-after">{hasAfter ? formatValue(after?.[key]) : '—'}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

/**
 * Registry lookup with fallback: an `entityType` without a registered renderer falls
 * back to `renderGenericDiff` so the page never hard-fails when a brand-new entity
 * type starts sending requests before its dedicated renderer exists.
 */
export function renderAuditDiff(
  entityType: string,
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
): ReactNode {
  const renderer = DIFF_RENDERERS[entityType] ?? renderGenericDiff
  return renderer(before, after)
}
