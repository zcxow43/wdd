import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairDefinitionTable } from './CurrencyPairDefinitionTable'
import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'

const USD_JPY: CurrencyPairDefinition = {
  id: 1,
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 3,
  quoteCurrencyCode: 'JPY',
  forwardPrecision: 2,
  reversePrecision: 5,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyPairDefinitionTable', () => {
  it('renders all columns and the row data', () => {
    render(
      <CurrencyPairDefinitionTable
        definitions={[USD_JPY]}
        loading={false}
        error={false}
        onRetry={vi.fn()}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('基準幣別')).toBeInTheDocument()
    expect(screen.getByText('對應幣別')).toBeInTheDocument()
    expect(screen.getByText('正向精度')).toBeInTheDocument()
    expect(screen.getByText('反向精度')).toBeInTheDocument()
    expect(screen.getByText('操作')).toBeInTheDocument()

    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('JPY')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })

  it('renders an empty state when there are no definitions', () => {
    render(
      <CurrencyPairDefinitionTable
        definitions={[]}
        loading={false}
        error={false}
        onRetry={vi.fn()}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('目前沒有幣種對主檔資料')).toBeInTheDocument()
  })

  it('renders a loading state', () => {
    render(
      <CurrencyPairDefinitionTable
        definitions={[]}
        loading={true}
        error={false}
        onRetry={vi.fn()}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('載入中...')).toBeInTheDocument()
  })

  it('renders an error state with a retry button, taking precedence over loading/empty', () => {
    const onRetry = vi.fn()
    render(
      <CurrencyPairDefinitionTable
        definitions={[]}
        loading={true}
        error={true}
        onRetry={onRetry}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    expect(screen.getByText('資料載入失敗')).toBeInTheDocument()
    expect(screen.queryByText('載入中...')).not.toBeInTheDocument()
    expect(screen.queryByText('目前沒有幣種對主檔資料')).not.toBeInTheDocument()
  })

  it('calls onRetry when the 重試 button is clicked', async () => {
    const onRetry = vi.fn()
    render(
      <CurrencyPairDefinitionTable
        definitions={[]}
        loading={false}
        error={true}
        onRetry={onRetry}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('重試'))
    expect(onRetry).toHaveBeenCalled()
  })

  it('calls onEdit/onDelete when the row action buttons are clicked', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(
      <CurrencyPairDefinitionTable
        definitions={[USD_JPY]}
        loading={false}
        error={false}
        onRetry={vi.fn()}
        onEdit={onEdit}
        onDelete={onDelete}
      />,
    )

    await userEvent.click(screen.getByText('編輯'))
    expect(onEdit).toHaveBeenCalledWith(USD_JPY)

    await userEvent.click(screen.getByText('刪除'))
    expect(onDelete).toHaveBeenCalledWith(USD_JPY)
  })
})
