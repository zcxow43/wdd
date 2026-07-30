import { describe, expect, it } from 'vitest'
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
  it('renders all five labeled fields in order with 修改前/修改後 headers', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = ['品牌', '名稱', '入金點差', '出金點差', '幣種對']
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => row.querySelector('td')?.textContent)).toEqual(labels)
  })

  it('highlights the 幣種對 row when membership differs', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, AFTER)}</>)

    const pairRow = screen.getByText('幣種對').closest('tr')!
    expect(pairRow).toHaveClass('audit-generic-diff-row--changed')

    const nameRow = screen.getByText('名稱').closest('tr')!
    expect(nameRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('does not highlight 幣種對 when membership is unchanged', () => {
    const sameAfter = { ...BEFORE, depositSpread: 0.15 }
    render(<>{renderSpreadGroupDiff(BEFORE, sameAfter)}</>)

    const pairRow = screen.getByText('幣種對').closest('tr')!
    expect(pairRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('renders member codes as BASE/QUOTE badges', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, AFTER)}</>)

    expect(screen.getAllByText('USD/JPY').length).toBeGreaterThan(0)
    expect(screen.getByText('USD/EUR')).toBeInTheDocument()
  })

  it('shows — for an empty members array', () => {
    const emptyAfter = { ...AFTER, members: [] }
    render(<>{renderSpreadGroupDiff(BEFORE, emptyAfter)}</>)

    const pairRow = screen.getByText('幣種對').closest('tr')!
    expect(pairRow.textContent).toContain('—')
  })

  it('shows real field values on the populated side and — on the null side for a CREATE (before === null)', () => {
    render(<>{renderSpreadGroupDiff(null, AFTER)}</>)

    expect(screen.getByText('Group A')).toBeInTheDocument()
    const brandRow = screen.getByText('品牌').closest('tr')!
    expect(brandRow.textContent).toContain('—')
    const pairRow = screen.getByText('幣種對').closest('tr')!
    expect(pairRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('shows real field values on the populated side and — on the null side for a DELETE (after === null)', () => {
    render(<>{renderSpreadGroupDiff(BEFORE, null)}</>)

    const pairRow = screen.getByText('幣種對').closest('tr')!
    expect(pairRow.textContent).toContain('USD/JPY')
    expect(pairRow.textContent).toContain('—')
  })
})
