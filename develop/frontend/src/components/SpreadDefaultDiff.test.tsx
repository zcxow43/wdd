import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { renderSpreadDefaultDiff } from './SpreadDefaultDiff'

const BEFORE = {
  brandId: 1,
  brandCode: 'AU',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
}

const AFTER = {
  ...BEFORE,
  withdrawSpread: 0.3,
}

describe('renderSpreadDefaultDiff', () => {
  it('renders the fixed field order with 修改前/修改後 headers', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = screen.getAllByRole('row').slice(1).map((row) => row.querySelector('td')?.textContent)
    expect(labels).toEqual(['品牌', '入金點差', '出金點差'])
  })

  it('shows real field values for both sides and highlights only the changed field', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, AFTER)}</>)

    expect(screen.getAllByText('AU')).toHaveLength(2)
    expect(screen.getAllByText('0.1')).toHaveLength(2)

    const withdrawRow = screen.getByText('出金點差').closest('tr')
    expect(withdrawRow).toHaveClass('generic-diff-row-changed')

    const brandRow = screen.getByText('品牌').closest('tr')
    expect(brandRow).not.toHaveClass('generic-diff-row-changed')
  })

  it('renders real values on the populated side and — on the null side for a CREATE request (before: null)', () => {
    render(<>{renderSpreadDefaultDiff(null, AFTER)}</>)

    expect(screen.getByText('AU')).toBeInTheDocument()
    const brandRow = screen.getByText('品牌').closest('tr')
    expect(brandRow?.textContent).toContain('—')
    expect(brandRow).not.toHaveClass('generic-diff-row-changed')
  })

  it('renders real values on the populated side and — on the null side for a DELETE request (after: null)', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, null)}</>)

    expect(screen.getByText('AU')).toBeInTheDocument()
    const withdrawRow = screen.getByText('出金點差').closest('tr')
    expect(withdrawRow).not.toHaveClass('generic-diff-row-changed')
  })
})
