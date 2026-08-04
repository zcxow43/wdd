import type { ReactNode } from 'react'
import type { DiffRenderer } from '../audit/diffRegistry'

/**
 * `renderSpreadGroupDiff` — the dedicated `DiffRenderer` for `entityType:
 * "SPREAD_GROUP"` (specs/frontend/spread.md), modeled directly on
 * `CurrencyPairDiff.tsx`. Renders the fixed field order 品牌/名稱/入金點差/出金點差/
 * 幣種對, where 幣種對 renders the `members` array as comma-joined `BASE/QUOTE`
 * `.currency-code` badges (reusing `CurrencyPairTable`'s inline pair-code
 * formatting) and is highlighted as changed if the joined membership string
 * differs between `before`/`after`. Handles a `null` `before` (CREATE, show `—`
 * on the left) / `null` `after` (DELETE, show `—` on the right) directly, same as
 * `renderCurrencyPairDiff`.
 */

interface SpreadGroupMemberSnapshot {
  currencyPairId: number
  baseCurrencyCode: string
  quoteCurrencyCode: string
}

interface SimpleFieldSpec {
  key: 'brandCode' | 'name' | 'depositSpread' | 'withdrawSpread'
  label: string
}

function formatPlain(value: unknown): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

function asMembers(value: unknown): SpreadGroupMemberSnapshot[] {
  return Array.isArray(value) ? (value as SpreadGroupMemberSnapshot[]) : []
}

function formatMembers(value: unknown): string {
  const members = asMembers(value)
  if (members.length === 0) {
    return '—'
  }
  return members.map((member) => `${member.baseCurrencyCode}/${member.quoteCurrencyCode}`).join(', ')
}

function renderMembers(value: unknown): ReactNode {
  const members = asMembers(value)
  if (members.length === 0) {
    return '—'
  }
  return members.map((member, index) => (
    <span key={member.currencyPairId}>
      {index > 0 && ', '}
      <span className="currency-code">{`${member.baseCurrencyCode}/${member.quoteCurrencyCode}`}</span>
    </span>
  ))
}

const SIMPLE_FIELDS: SimpleFieldSpec[] = [
  { key: 'brandCode', label: '品牌' },
  { key: 'name', label: '名稱' },
  { key: 'depositSpread', label: '入金點差' },
  { key: 'withdrawSpread', label: '出金點差' },
]

export const renderSpreadGroupDiff: DiffRenderer = (before, after) => {
  const beforeMembers = before ? (before as Record<string, unknown>).members : undefined
  const afterMembers = after ? (after as Record<string, unknown>).members : undefined
  const membersChanged =
    before !== null && after !== null && formatMembers(beforeMembers) !== formatMembers(afterMembers)

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
        {SIMPLE_FIELDS.map(({ key, label }) => {
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
        <tr className={membersChanged ? 'generic-diff-row-changed' : undefined}>
          <td className="generic-diff-key">幣種對</td>
          <td className="generic-diff-before">{before === null ? '—' : renderMembers(beforeMembers)}</td>
          <td className="generic-diff-after">{after === null ? '—' : renderMembers(afterMembers)}</td>
        </tr>
      </tbody>
    </table>
  )
}
