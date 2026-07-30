import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadDefaultFormModal } from './SpreadDefaultFormModal'
import { ApiError, NetworkError } from '../api/client'
import type { SpreadDefault } from '../types/spread'

const SPREAD_DEFAULT: SpreadDefault = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

describe('SpreadDefaultFormModal', () => {
  it('pre-fills the current deposit/withdraw spread values', () => {
    render(<SpreadDefaultFormModal spreadDefault={SPREAD_DEFAULT} onSubmit={vi.fn()} onClose={vi.fn()} />)

    expect(screen.getByLabelText('入金點差')).toHaveValue(0.1)
    expect(screen.getByLabelText('出金點差')).toHaveValue(0.2)
  })

  it('submits the edited values', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(<SpreadDefaultFormModal spreadDefault={SPREAD_DEFAULT} onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.clear(screen.getByLabelText('入金點差'))
    await user.type(screen.getByLabelText('入金點差'), '0.15')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ depositSpread: 0.15, withdrawSpread: 0.2 }),
    )
  })

  it('shows an inline error for a negative value without submitting', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()
    render(<SpreadDefaultFormModal spreadDefault={SPREAD_DEFAULT} onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.clear(screen.getByLabelText('入金點差'))
    await user.type(screen.getByLabelText('入金點差'), '-1')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('入金點差為必填，且須大於等於 0')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('shows a generic inline error for a 400 response', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, { error: 'Invalid' }, 'Bad Request'))
    const user = userEvent.setup()
    render(<SpreadDefaultFormModal spreadDefault={SPREAD_DEFAULT} onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('入金點差與出金點差為必填，且須大於等於 0')).toBeInTheDocument()
  })

  it('shows a network error message on network failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new NetworkError(new TypeError('fail')))
    const user = userEvent.setup()
    render(<SpreadDefaultFormModal spreadDefault={SPREAD_DEFAULT} onSubmit={onSubmit} onClose={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })
})
