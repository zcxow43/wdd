import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { renderSpreadGroupDiff } from './SpreadGroupDiff'

const BEFORE = {
  brandId: 1,
  brandCode: 'AU',
  name: 'Group A',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  currencyPairIds: [3],
  members: [{ currencyPairId: 3, baseCurrencyCode: 'USD', quoteCurrencyCode: 'JPY' }],
}

const AFTER = {
  ...BEFORE,
  currencyPairIds: [3, 4],
  members: [
    { currencyPairId: 3, baseCurrencyCode: 'USD', quoteCurrencyCode: 'JPY' },
    { currencyPairId: 4, baseCurrencyCode: 'USD', quoteCurrencyCode: 'EUR' },
  ],
}

describe('renderSpreadGroupDiff', () => {
  it('renders the fixed field order with 修改前/修改後 headers', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = screen.getAllByRole('row').slice(1).map((row) => row.querySelector('td')?.textContent)
    expect(labels).toEqual(['品牌', '名稱', '入金點差', '出金點差', '幣種對'])
  })

  it('renders 幣種對 as comma-joined BASE/QUOTE badges and highlights the row when membership changes', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, AFTER)}</>)

    expect(screen.getAllByText('USD/JPY').length).toBeGreaterThan(0)
    expect(screen.getByText('USD/EUR')).toBeInTheDocument()

    const membersRow = screen.getByText('幣種對').closest('tr')
    expect(membersRow).toHaveClass('generic-diff-row-changed')
  })

  it('does not highlight 幣種對 when membership is unchanged', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, { ...BEFORE, name: 'Group A Renamed' })}</>)

    const membersRow = screen.getByText('幣種對').closest('tr')
    expect(membersRow).not.toHaveClass('generic-diff-row-changed')

    const nameRow = screen.getByText('名稱').closest('tr')
    expect(nameRow).toHaveClass('generic-diff-row-changed')
  })

  it('renders — for an empty members array', () => {
    render(<>{renderSpreadGroupDiff({ ...BEFORE, members: [] }, { ...AFTER, members: [] })}</>)

    const membersRow = screen.getByText('幣種對').closest('tr')
    expect(membersRow?.textContent).toContain('—')
  })

  it('renders real values on the populated side and — on the null side for a CREATE request (before: null)', () => {
    render(<>{renderSpreadGroupDiff(null, AFTER)}</>)

    expect(screen.getByText('Group A')).toBeInTheDocument()
    const nameRow = screen.getByText('名稱').closest('tr')
    expect(nameRow?.textContent).toContain('—')
    expect(nameRow).not.toHaveClass('generic-diff-row-changed')

    const membersRow = screen.getByText('幣種對').closest('tr')
    expect(membersRow?.textContent).toContain('—')
    expect(membersRow).not.toHaveClass('generic-diff-row-changed')
  })

  it('renders real values on the populated side and — on the null side for a DELETE request (after: null)', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, null)}</>)

    expect(screen.getByText('USD/JPY')).toBeInTheDocument()
    const membersRow = screen.getByText('幣種對').closest('tr')
    expect(membersRow).not.toHaveClass('generic-diff-row-changed')
  })
})
