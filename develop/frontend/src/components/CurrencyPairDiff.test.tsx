import { describe, it, expect } from 'vitest'
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
  rateType: 'MANUAL',
  active: true,
}

const AFTER = {
  ...BEFORE,
  rate: 33.0,
}

describe('renderCurrencyPairDiff', () => {
  it('renders the fixed field order with 修改前/修改後 headers', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    expect(screen.getByText('修改前')).toBeInTheDocument()
    expect(screen.getByText('修改後')).toBeInTheDocument()

    const labels = screen.getAllByRole('row').slice(1).map((row) => row.querySelector('td')?.textContent)
    expect(labels).toEqual(['品牌', '基準幣別', '對應幣別', '匯率', '匯率類型', '狀態'])
  })

  it('shows real field values for both sides and highlights only the changed field', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    expect(screen.getAllByText('PUG')).toHaveLength(2)
    expect(screen.getAllByText('USD')).toHaveLength(2)
    expect(screen.getAllByText('TWD')).toHaveLength(2)

    const rateRow = screen.getByText('匯率').closest('tr')
    expect(rateRow).toHaveClass('generic-diff-row-changed')

    const brandRow = screen.getByText('品牌').closest('tr')
    expect(brandRow).not.toHaveClass('generic-diff-row-changed')
  })

  it('renders — for a null rate (AUTO pair)', () => {
    render(<>{renderCurrencyPairDiff({ ...BEFORE, rate: null, rateType: 'AUTO' }, { ...AFTER, rate: null, rateType: 'AUTO' })}</>)

    const rateRow = screen.getByText('匯率').closest('tr')
    expect(rateRow?.textContent).toContain('—')
  })

  it('maps 啟用/停用 for active and 手動/自動 for rateType', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, AFTER)}</>)

    expect(screen.getAllByText('啟用').length).toBeGreaterThan(0)
    expect(screen.getAllByText('手動').length).toBeGreaterThan(0)
  })

  it('renders real values on the populated side and — on the null side for a CREATE request (before: null)', () => {
    render(<>{renderCurrencyPairDiff(null, AFTER)}</>)

    expect(screen.getByText('PUG')).toBeInTheDocument()
    const brandRow = screen.getByText('品牌').closest('tr')
    expect(brandRow?.textContent).toContain('—')
    // Never highlighted against a null side.
    expect(brandRow).not.toHaveClass('generic-diff-row-changed')
  })

  it('renders real values on the populated side and — on the null side for a DELETE request (after: null)', () => {
    render(<>{renderCurrencyPairDiff(BEFORE, null)}</>)

    expect(screen.getByText('PUG')).toBeInTheDocument()
    const rateRow = screen.getByText('匯率').closest('tr')
    expect(rateRow).not.toHaveClass('generic-diff-row-changed')
  })
})
