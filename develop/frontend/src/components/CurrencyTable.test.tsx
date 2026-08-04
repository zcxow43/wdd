import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyTable } from './CurrencyTable'
import type { Currency } from '../types/currency'

const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: '新台幣',
  symbol: 'NT$',
  decimalPlaces: 0,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'US Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 2,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyTable', () => {
  it('renders all columns and no Active column', () => {
    render(
      <CurrencyTable currencies={[TWD]} loading={false} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    expect(screen.getByText('Code')).toBeInTheDocument()
    expect(screen.getByText('Name')).toBeInTheDocument()
    expect(screen.getByText('中文名稱')).toBeInTheDocument()
    expect(screen.getByText('Symbol')).toBeInTheDocument()
    expect(screen.getByText('Decimal Places')).toBeInTheDocument()
    expect(screen.getByText('Actions')).toBeInTheDocument()
    expect(screen.queryByText('Active')).not.toBeInTheDocument()

    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('新台幣')).toBeInTheDocument()
    expect(screen.getByText('NT$')).toBeInTheDocument()
  })

  it('renders a dash fallback when nameZh/symbol are empty', () => {
    render(
      <CurrencyTable currencies={[USD]} loading={false} onEdit={vi.fn()} onDelete={vi.fn()} />,
    )

    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBe(2)
  })

  it('renders an empty state when there are no currencies', () => {
    render(<CurrencyTable currencies={[]} loading={false} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('目前沒有幣種資料')).toBeInTheDocument()
  })

  it('renders a loading state', () => {
    render(<CurrencyTable currencies={[]} loading={true} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('載入中...')).toBeInTheDocument()
  })

  it('calls onEdit/onDelete when the row action buttons are clicked', async () => {
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(
      <CurrencyTable currencies={[TWD]} loading={false} onEdit={onEdit} onDelete={onDelete} />,
    )

    await userEvent.click(screen.getByText('編輯'))
    expect(onEdit).toHaveBeenCalledWith(TWD)

    await userEvent.click(screen.getByText('刪除'))
    expect(onDelete).toHaveBeenCalledWith(TWD)
  })
})
