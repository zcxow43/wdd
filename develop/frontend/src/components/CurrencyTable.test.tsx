import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyTable } from './CurrencyTable'
import type { Currency } from '../types/currency'

const CURRENCIES: Currency[] = [
  {
    id: 1,
    code: 'TWD',
    name: 'New Taiwan Dollar',
    nameZh: '新台幣',
    symbol: 'NT$',
    decimalPlaces: 0,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
  {
    id: 2,
    code: 'USD',
    name: 'United States Dollar',
    nameZh: null,
    symbol: '$',
    decimalPlaces: 2,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

describe('CurrencyTable', () => {
  it('renders all columns for each currency row', () => {
    render(<CurrencyTable currencies={CURRENCIES} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(screen.getByText('New Taiwan Dollar')).toBeInTheDocument()
    expect(screen.getByText('新台幣')).toBeInTheDocument()
    expect(screen.getByText('NT$')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })

  it('shows a dash for empty nameZh', () => {
    render(<CurrencyTable currencies={CURRENCIES} onEdit={vi.fn()} onDelete={vi.fn()} />)

    const usdRow = screen.getByText('USD').closest('tr')!
    expect(usdRow).toHaveTextContent('-')
  })

  it('renders the empty state when there are no currencies', () => {
    render(<CurrencyTable currencies={[]} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('目前沒有符合條件的幣種')).toBeInTheDocument()
  })

  it('calls onEdit and onDelete with the clicked currency', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<CurrencyTable currencies={CURRENCIES} onEdit={onEdit} onDelete={onDelete} />)

    const twdRow = screen.getByText('TWD').closest('tr')!
    await user.click(within(twdRow).getByText('Edit'))
    expect(onEdit).toHaveBeenCalledWith(CURRENCIES[0])

    await user.click(within(twdRow).getByText('Delete'))
    expect(onDelete).toHaveBeenCalledWith(CURRENCIES[0])
  })
})
