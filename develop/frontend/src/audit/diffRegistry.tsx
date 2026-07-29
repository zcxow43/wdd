import type { ReactNode } from 'react'
import './diffRegistry.css'

/**
 * Renders a before/after comparison for one entity type's audit requests.
 *
 * In practice the review modal only ever invokes a renderer once both
 * `before` and `after` are genuinely-populated snapshots — the `null` cases
 * (CREATE has no `before`, DELETE has no `after`) are handled once,
 * generically, by the modal itself, so individual renderers never need to
 * special-case `null`. The `| null` in this signature exists only to match
 * the shape of the API's raw `before`/`after` fields.
 */
export type DiffRenderer = (
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
) => ReactNode

const DIFF_RENDERERS: Record<string, DiffRenderer> = {}

/**
 * Registers a diff renderer for a given `entityType`. Consumers (e.g. the
 * currency-pair feature) call this once, from their own module, so that this
 * audit module never needs to know about them.
 */
export function registerDiffRenderer(entityType: string, renderer: DiffRenderer): void {
  DIFF_RENDERERS[entityType] = renderer
}

/**
 * Whether a dedicated renderer is registered for `entityType`. Used by the
 * review modal to decide whether to hand a `null` before/after straight to
 * the renderer (letting it decide how to display a CREATE/DELETE) or fall
 * back to the modal's own generic placeholder text, so entity types without
 * a dedicated renderer keep their existing, tested behavior.
 */
export function hasDiffRenderer(entityType: string): boolean {
  return entityType in DIFF_RENDERERS
}

function formatGenericValue(value: unknown): string {
  if (value === undefined) return '—'
  if (value === null) return 'null'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/**
 * Fallback renderer used for any `entityType` without a registered renderer.
 * Lists the raw key/value pairs of `before`/`after` as-is in a simple
 * two-column table, highlighting any key present in both with a different
 * value. Not pretty, but correct and non-breaking for any entity type.
 */
export const renderGenericDiff: DiffRenderer = (before, after) => {
  const beforeSnapshot = before ?? {}
  const afterSnapshot = after ?? {}
  const keys = Array.from(new Set([...Object.keys(beforeSnapshot), ...Object.keys(afterSnapshot)])).sort()

  if (keys.length === 0) {
    return <div className="audit-diff-empty">無資料</div>
  }

  return (
    <table className="audit-generic-diff-table">
      <thead>
        <tr>
          <th>欄位</th>
          <th>異動前</th>
          <th>異動後</th>
        </tr>
      </thead>
      <tbody>
        {keys.map((key) => {
          const beforeValue = beforeSnapshot[key]
          const afterValue = afterSnapshot[key]
          const changed =
            Object.prototype.hasOwnProperty.call(beforeSnapshot, key) &&
            Object.prototype.hasOwnProperty.call(afterSnapshot, key) &&
            JSON.stringify(beforeValue) !== JSON.stringify(afterValue)
          return (
            <tr key={key} className={changed ? 'audit-generic-diff-row--changed' : undefined}>
              <td>{key}</td>
              <td>{formatGenericValue(beforeValue)}</td>
              <td>{formatGenericValue(afterValue)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

/**
 * Looks up the registered renderer for `entityType`, falling back to
 * `renderGenericDiff` for any entity type without one, so the audit page
 * never hard-fails when a brand-new entity type starts sending requests
 * before its dedicated renderer exists.
 */
export function renderAuditDiff(
  entityType: string,
  before: Record<string, unknown> | null,
  after: Record<string, unknown> | null,
): ReactNode {
  const renderer = DIFF_RENDERERS[entityType] ?? renderGenericDiff
  return renderer(before, after)
}
