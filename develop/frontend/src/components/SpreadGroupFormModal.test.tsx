import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadGroupFormModal } from './SpreadGroupFormModal'
import { ApiError, NetworkError } from '../api/client'
import type { CurrencyPair } from '../types/currencyPair'
import type { SpreadGroup } from '../types/spread'

const USD_JPY: CurrencyPair = {
  id: 3,
  brandId: 1,
  brandCode: 'AU',
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 3,
  quoteCurrencyCode: 'JPY',
  rate: 150,
  rateType: 'MANUAL',
  active: true,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

const USD_EUR: CurrencyPair = {
  ...USD_JPY,
  id: 4,
  quoteCurrencyId: 4,
  quoteCurrencyCode: 'EUR',
}

const GROUP_A: SpreadGroup = {
  id: 10,
  brandId: 1,
  brandCode: 'AU',
  name: 'Group A',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  members: [{ currencyPairId: 3, baseCurrencyCode: 'USD', quoteCurrencyCode: 'JPY' }],
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

describe('SpreadGroupFormModal', () => {
  it('lists all available pairs as unassigned in create mode', () => {
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[]}
        onSubmit={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText('未加入本群組')).toBeInTheDocument()
    expect(screen.getByText('尚未加入任何幣種對')).toBeInTheDocument()
    expect(screen.getByText('USD/JPY')).toBeInTheDocument()
    expect(screen.getByText('USD/EUR')).toBeInTheDocument()
  })

  it('moves a pair from unassigned to assigned and submits it as a member', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[]}
        onSubmit={onSubmit}
        onClose={vi.fn()}
      />,
    )

    await user.type(screen.getByLabelText('名稱'), 'Group A')
    await user.type(screen.getByLabelText('入金點差'), '0.1')
    await user.type(screen.getByLabelText('出金點差'), '0.2')

    const usdJpyRow = screen.getByText('USD/JPY').closest('li')!
    await user.click(within(usdJpyRow).getByRole('button', { name: '加入 →' }))

    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({
        brandId: 1,
        name: 'Group A',
        depositSpread: 0.1,
        withdrawSpread: 0.2,
        currencyPairIds: [3],
      }),
    )
  })

  it('pre-fills members and shows a hint for a pair already in another group', async () => {
    const otherGroup: SpreadGroup = {
      ...GROUP_A,
      id: 20,
      name: 'Group B',
      members: [{ currencyPairId: 4, baseCurrencyCode: 'USD', quoteCurrencyCode: 'EUR' }],
    }
    render(
      <SpreadGroupFormModal
        mode="edit"
        initial={GROUP_A}
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[GROUP_A, otherGroup]}
        onSubmit={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    const assignedPanel = screen.getByText('已加入本群組').closest('.pair-assigner-panel') as HTMLElement
    expect(within(assignedPanel).getByText('USD/JPY')).toBeInTheDocument()

    expect(screen.getByText('目前屬於：Group B，核准後將自動移出')).toBeInTheDocument()
  })

  it('shows an inline name-taken error on a live-duplicate 409, without closing', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'Spread group name already exists for this brand' }, 'Conflict'))
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY]}
        groups={[]}
        onSubmit={onSubmit}
        onClose={onClose}
      />,
    )

    await user.type(screen.getByLabelText('名稱'), 'Group A')
    await user.type(screen.getByLabelText('入金點差'), '0.1')
    await user.type(screen.getByLabelText('出金點差'), '0.2')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此名稱已被使用')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a network error message on network failure', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new NetworkError(new TypeError('fail')))
    const user = userEvent.setup()
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY]}
        groups={[]}
        onSubmit={onSubmit}
        onClose={vi.fn()}
      />,
    )

    await user.type(screen.getByLabelText('名稱'), 'Group A')
    await user.type(screen.getByLabelText('入金點差'), '0.1')
    await user.type(screen.getByLabelText('出金點差'), '0.2')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('requires a non-blank name before submitting', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY]}
        groups={[]}
        onSubmit={onSubmit}
        onClose={vi.fn()}
      />,
    )

    await user.type(screen.getByLabelText('入金點差'), '0.1')
    await user.type(screen.getByLabelText('出金點差'), '0.2')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('名稱為必填')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
