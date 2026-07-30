import type { ReactNode } from 'react'
import type { DiffRenderer } from '../audit/diffRegistry'
import '../audit/diffRegistry.css'

/**
 * Shape of `before`/`after` for a `SPREAD_GROUP` audit request, matching
 * `SpreadGroupAuditHandler`'s snapshot on the backend
 * (specs/backend/spread.md). `members` is enrichment carried along purely so
 * this renderer can show pair codes without a second lookup.
 */
interface SpreadGroupMemberSnapshot {
  currencyPairId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
}

interface SpreadGroupSnapshot {
  brandId: number
  brandCode: string
  name: string
  depositSpread: number
  withdrawSpread: number
  currencyPairIds?: number[]
  members: SpreadGroupMemberSnapshot[]
}

interface FieldDef {
  label: string
  format: (snapshot: SpreadGroupSnapshot) => string
}

function formatSpread(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return Number(value.toFixed(8)).toString()
}

function memberCodes(members: SpreadGroupMemberSnapshot[] | undefined): string[] {
  return (members ?? []).map((member) => `${member.baseCurrencyCode}/${member.quoteCurrencyCode}`)
}

function memberBadges(members: SpreadGroupMemberSnapshot[] | undefined): ReactNode {
  const codes = memberCodes(members)
  if (codes.length === 0) return '—'
  return codes.map((code, index) => (
    <span key={code}>
      {index > 0 && ', '}
      <span className="currency-code">{code}</span>
    </span>
  ))
}

// Fixed field order per specs/frontend/spread.md: 品牌/名稱/入金點差/出金點差 (幣種對 rendered separately below).
const FIELDS: FieldDef[] = [
  { label: '品牌', format: (snapshot) => snapshot.brandCode ?? '—' },
  { label: '名稱', format: (snapshot) => snapshot.name ?? '—' },
  { label: '入金點差', format: (snapshot) => formatSpread(snapshot.depositSpread) },
  { label: '出金點差', format: (snapshot) => formatSpread(snapshot.withdrawSpread) },
]

/**
 * Renders a labeled 修改前/修改後 comparison for a `SPREAD_GROUP` audit
 * request, registered against `entityType: "SPREAD_GROUP"` in the audit
 * module's diff-renderer registry. Modeled directly on
 * `renderCurrencyPairDiff` (specs/frontend/currency-pair-approval.md), with
 * an extra 幣種對 row rendering `members` as comma-joined `BASE/QUOTE`
 * badges, highlighted as changed whenever the joined membership differs.
 *
 * Also handles a `null` `before` (CREATE) or `after` (DELETE) directly, same
 * as `renderCurrencyPairDiff`.
 */
export const renderSpreadGroupDiff: DiffRenderer = (before, after) => {
  const beforeSnapshot = before as SpreadGroupSnapshot | null
  const afterSnapshot = after as SpreadGroupSnapshot | null

  const beforeJoined = memberCodes(beforeSnapshot?.members).join(', ')
  const afterJoined = memberCodes(afterSnapshot?.members).join(', ')
  const membersChanged = beforeSnapshot !== null && afterSnapshot !== null && beforeJoined !== afterJoined

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
        <tr className={membersChanged ? 'audit-generic-diff-row--changed' : undefined}>
          <td>幣種對</td>
          <td>{beforeSnapshot ? memberBadges(beforeSnapshot.members) : '—'}</td>
          <td>{afterSnapshot ? memberBadges(afterSnapshot.members) : '—'}</td>
        </tr>
      </tbody>
    </table>
  )
}
