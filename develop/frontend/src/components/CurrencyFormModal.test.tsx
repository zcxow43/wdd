import { describe, expect, it, vi } from 'vitest'
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
  it('shows validation errors when required fields are missing', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<CurrencyFormModal mode="create" onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('代碼為必填')).toBeInTheDocument()
    expect(screen.getByText('名稱為必填')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects a code that is not 3 uppercase letters', async () => {
    const user = userEvent.setup()
    render(<CurrencyFormModal mode="create" onSubmit={vi.fn()} onClose={vi.fn()} />)

    await user.type(screen.getByLabelText('代碼'), 'ab1')
    await user.type(screen.getByLabelText('名稱'), 'Test Currency')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('代碼須為 3 位大寫英文字母')).toBeInTheDocument()
  })

  it('submits a valid create form with normalized values', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<CurrencyFormModal mode="create" onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.type(screen.getByLabelText('代碼'), 'krw')
    await user.type(screen.getByLabelText('名稱'), 'South Korean Won')
    await user.type(screen.getByLabelText('中文名稱'), '韓元')
    await user.type(screen.getByLabelText('符號'), '₩')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        code: 'KRW',
        name: 'South Korean Won',
        nameZh: '韓元',
        symbol: '₩',
        decimalPlaces: 2,
      }),
    )
  })

  it('disables the code field and pre-fills values in edit mode', () => {
    render(<CurrencyFormModal mode="edit" initial={EXISTING} onSubmit={vi.fn()} onClose={vi.fn()} />)

    const codeInput = screen.getByLabelText('代碼') as HTMLInputElement
    expect(codeInput).toBeDisabled()
    expect(codeInput.value).toBe('TWD')
    expect((screen.getByLabelText('名稱') as HTMLInputElement).value).toBe('New Taiwan Dollar')
  })

  it('shows an inline conflict message when the API returns 409', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(409, { error: 'Currency code already exists' }, 'Conflict'))
    render(<CurrencyFormModal mode="create" onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.type(screen.getByLabelText('代碼'), 'KRW')
    await user.type(screen.getByLabelText('名稱'), 'South Korean Won')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('幣種代碼已存在')).toBeInTheDocument()
  })

  it('shows a network error message when the request fails to reach the server', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('network down'))
    render(<CurrencyFormModal mode="create" onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.type(screen.getByLabelText('代碼'), 'KRW')
    await user.type(screen.getByLabelText('名稱'), 'South Korean Won')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })
})
