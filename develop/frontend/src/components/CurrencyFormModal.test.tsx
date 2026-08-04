import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyFormModal } from './CurrencyFormModal'
import { ApiError } from '../api/client'
import type { Currency } from '../types/currency'

const EXISTING: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: '新台幣',
  symbol: 'NT$',
  decimalPlaces: 0,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('CurrencyFormModal', () => {
  it('has no Active toggle', () => {
    render(<CurrencyFormModal initial={null} onClose={vi.fn()} onSubmit={vi.fn()} />)

    expect(screen.queryByText(/active/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('shows validation errors when required fields are missing', async () => {
    render(<CurrencyFormModal initial={null} onClose={vi.fn()} onSubmit={vi.fn()} />)

    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('Code 為必填')).toBeInTheDocument()
    expect(screen.getByText('Name 為必填')).toBeInTheDocument()
    expect(screen.getByText('Decimal Places 為必填')).toBeInTheDocument()
  })

  it('normalizes code to uppercase and submits a valid create form', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<CurrencyFormModal initial={null} onClose={vi.fn()} onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('Code'), 'krw')
    await userEvent.type(screen.getByLabelText('Name'), 'South Korean Won')
    await userEvent.type(screen.getByLabelText('中文名稱'), '韓元')
    await userEvent.type(screen.getByLabelText('Symbol'), '₩')
    await userEvent.type(screen.getByLabelText('Decimal Places'), '0')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        code: 'KRW',
        name: 'South Korean Won',
        nameZh: '韓元',
        symbol: '₩',
        decimalPlaces: 0,
      }),
    )
  })

  it('disables the Code field on edit', () => {
    render(<CurrencyFormModal initial={EXISTING} onClose={vi.fn()} onSubmit={vi.fn()} />)

    expect(screen.getByLabelText('Code')).toBeDisabled()
    expect(screen.getByLabelText('Code')).toHaveValue('TWD')
  })

  it('shows an inline "幣種代碼已存在" error on a 409 response', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(409, { error: 'Currency code already exists', code: 'KRW' }))
    render(<CurrencyFormModal initial={null} onClose={vi.fn()} onSubmit={onSubmit} />)

    await userEvent.type(screen.getByLabelText('Code'), 'KRW')
    await userEvent.type(screen.getByLabelText('Name'), 'South Korean Won')
    await userEvent.type(screen.getByLabelText('Decimal Places'), '0')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('幣種代碼已存在')).toBeInTheDocument()
  })
})
