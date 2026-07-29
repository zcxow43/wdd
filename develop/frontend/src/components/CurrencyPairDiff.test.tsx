import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { renderCurrencyPairDiff } from './CurrencyPairDiff'

const BEFORE = {
  brandId: 3,
  brandCode: 'PUG',
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 1,
  quoteCurrencyCode: 'TWD',
  rate: 32.5,
  rateType: 'MANUAL' as const,
  active: true,
}

const AFTER = {
  ...BEFORE,
  rate: 33,
}

describe('renderCurrencyPairDiff', () => {
  it('renders all six labeled fields in order with 修改前/修改後 headers', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = ['品牌', '基準幣別', '對應幣別', '匯率', '匯率類型', '狀態']
    const rows = screen.getAllByRole('row').slice(1) // skip header row
    expect(rows.map((row) => row.querySelector('td')?.textContent)).toEqual(labels)
  })

  it('highlights only the changed field (rate) between before/after', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    const rateRow = screen.getByText('匯率').closest('tr')!
    expect(rateRow).toHaveClass('audit-generic-diff-row--changed')

    const brandRow = screen.getByText('品牌').closest('tr')!
    expect(brandRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('renders 32.5 and 33 for the changed rate values', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('32.5')).toBeInTheDocument()
    expect(screen.getByText('33')).toBeInTheDocument()
  })

  it('renders — for a null rate (AUTO)', () => {
    const autoAfter = { ...BEFORE, rate: null, rateType: 'AUTO' as const }
    render(<>{renderCurrencyPairDiff(BEFORE, autoAfter)}</>)

    const rateRow = screen.getByText('匯率').closest('tr')!
    expect(rateRow.textContent).toContain('—')
  })

  it('renders 啟用/停用 for active true/false', () => {
    const inactiveAfter = { ...BEFORE, active: false }
    render(<>{renderCurrencyPairDiff(BEFORE, inactiveAfter)}</>)

    expect(screen.getByText('啟用')).toBeInTheDocument()
    expect(screen.getByText('停用')).toBeInTheDocument()
  })

  it('renders 手動/自動 for MANUAL/AUTO rate type', () => {
    const autoAfter = { ...BEFORE, rateType: 'AUTO' as const, rate: null }
    render(<>{renderCurrencyPairDiff(BEFORE, autoAfter)}</>)

    expect(screen.getByText('手動')).toBeInTheDocument()
    expect(screen.getByText('自動')).toBeInTheDocument()
  })

  it('shows real field values on the populated side and — on the null side for a CREATE (before === null)', () => {
    render(<>{renderCurrencyPairDiff(null, AFTER)}</>)

    expect(screen.getByText('PUG')).toBeInTheDocument()
    expect(screen.getByText('33')).toBeInTheDocument()
    const brandRow = screen.getByText('品牌').closest('tr')!
    expect(brandRow.textContent).toContain('—')
    expect(brandRow).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('shows real field values on the populated side and — on the null side for a DELETE (after === null)', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, null)}</>)

    expect(screen.getByText('PUG')).toBeInTheDocument()
    const rateRow = screen.getByText('匯率').closest('tr')!
    expect(rateRow.textContent).toContain('32.5')
    expect(rateRow.textContent).toContain('—')
  })
})
