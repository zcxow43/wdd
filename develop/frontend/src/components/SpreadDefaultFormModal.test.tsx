import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadDefaultFormModal } from './SpreadDefaultFormModal'
import { ApiError } from '../api/client'
import type { SpreadDefault } from '../types/spread'

const INITIAL: SpreadDefault = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  createdAt: '',
  updatedAt: '',
}

describe('SpreadDefaultFormModal', () => {
  it('pre-fills the deposit/withdraw values from initial', () => {
    render(<SpreadDefaultFormModal initial={INITIAL} onClose={vi.fn()} onSubmit={vi.fn()} />)

    expect(screen.getByLabelText('入金點差')).toHaveValue(0.1)
    expect(screen.getByLabelText('出金點差')).toHaveValue(0.2)
  })

  it('shows validation errors when fields are cleared', async () => {
    render(<SpreadDefaultFormModal initial={INITIAL} onClose={vi.fn()} onSubmit={vi.fn()} />)

    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.clear(screen.getByLabelText('出金點差'))
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('入金點差為必填，且不可小於 0')).toBeInTheDocument()
    expect(screen.getByText('出金點差為必填，且不可小於 0')).toBeInTheDocument()
  })

  it('rejects a negative value', async () => {
    render(<SpreadDefaultFormModal initial={INITIAL} onClose={vi.fn()} onSubmit={vi.fn()} />)

    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.type(screen.getByLabelText('入金點差'), '-1')
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('入金點差為必填，且不可小於 0')).toBeInTheDocument()
  })

  it('submits the numeric deposit/withdraw values', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<SpreadDefaultFormModal initial={INITIAL} onClose={vi.fn()} onSubmit={onSubmit} />)

    await userEvent.clear(screen.getByLabelText('出金點差'))
    await userEvent.type(screen.getByLabelText('出金點差'), '0.5')
    await userEvent.click(screen.getByText('送出'))

    expect(onSubmit).toHaveBeenCalledWith({ depositSpread: 0.1, withdrawSpread: 0.5 })
  })

  it('shows an inline general error on a 400 without closing', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new ApiError(400, { error: 'Invalid spread' }))
    render(<SpreadDefaultFormModal initial={INITIAL} onClose={vi.fn()} onSubmit={onSubmit} />)

    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('輸入資料有誤，請確認後再試')).toBeInTheDocument()
    expect(screen.getByText('編輯預設點差')).toBeInTheDocument()
  })
})
