import { describe, expect, it } from 'vitest'
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
  depositSpread: 0.15,
}

describe('renderSpreadDefaultDiff', () => {
  it('renders all three labeled fields in order with 修改前/修改後 headers', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = ['品牌', '入金點差', '出金點差']
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => row.querySelector('td')?.textContent)).toEqual(labels)
  })

  it('highlights only the changed field (depositSpread) between before/after', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, AFTER)}</>)

    const depositRow = screen.getByText('入金點差').closest('tr')!
    expect(depositRow).toHaveClass('audit-generic-diff-row--changed')

    const brandRow = screen.getByText('品牌').closest('tr')!
    expect(brandRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('renders 0.1 and 0.15 for the changed deposit spread values', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('0.1')).toBeInTheDocument()
    expect(screen.getByText('0.15')).toBeInTheDocument()
  })

  it('shows real field values on the populated side and — on the null side for a null before', () => {
    render(<>{renderSpreadDefaultDiff(null, AFTER)}</>)

    expect(screen.getByText('AU')).toBeInTheDocument()
    const brandRow = screen.getByText('品牌').closest('tr')!
    expect(brandRow.textContent).toContain('—')
    expect(brandRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('shows real field values on the populated side and — on the null side for a null after', () => {
    render(<>{renderSpreadDefaultDiff(BEFORE, null)}</>)

    const depositRow = screen.getByText('入金點差').closest('tr')!
    expect(depositRow.textContent).toContain('0.1')
    expect(depositRow.textContent).toContain('—')
  })
})
