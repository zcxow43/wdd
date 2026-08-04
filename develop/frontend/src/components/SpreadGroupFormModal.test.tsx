import { describe, it, expect, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadGroupFormModal } from './SpreadGroupFormModal'
import { ApiError } from '../api/client'
import type { SpreadGroup } from '../types/spread'
import type { CurrencyPair } from '../types/currencyPair'

function pair(id: number, base: string, quote: string): CurrencyPair {
  return {
    id,
    brandId: 1,
    brandCode: 'AU',
    baseCurrencyId: id * 10,
    baseCurrencyCode: base,
    quoteCurrencyId: id * 10 + 1,
    quoteCurrencyCode: quote,
    rate: 1,
    rateType: 'MANUAL',
    active: true,
    createdAt: '',
    updatedAt: '',
  }
}

const USD_JPY = pair(3, 'USD', 'JPY')
const USD_EUR = pair(4, 'USD', 'EUR')
const USD_TWD = pair(5, 'USD', 'TWD')

const GROUP_A: SpreadGroup = {
  id: 10,
  brandId: 1,
  brandCode: 'AU',
  name: 'Group A',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  members: [{ currencyPairId: 3, baseCurrencyCode: 'USD', quoteCurrencyCode: 'JPY' }],
  createdAt: '',
  updatedAt: '',
}

const GROUP_B: SpreadGroup = {
  id: 11,
  brandId: 1,
  brandCode: 'AU',
  name: 'Group B',
  depositSpread: 0.3,
  withdrawSpread: 0.4,
  members: [],
  createdAt: '',
  updatedAt: '',
}

describe('SpreadGroupFormModal', () => {
  it('renders create-mode with empty fields and all pairs unassigned', () => {
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByText('新增點差群組')).toBeInTheDocument()
    expect(screen.getByLabelText('名稱')).toHaveValue('')
    expect(screen.getByText('未加入本群組').closest('.pair-assigner-panel')?.textContent).toContain(
      'USD/JPY',
    )
    expect(screen.getByText('已加入本群組').closest('.pair-assigner-panel')?.textContent).toContain(
      '尚未加入任何幣種對',
    )
  })

  it('pre-fills edit-mode fields and members from initial', () => {
    render(
      <SpreadGroupFormModal
        mode="edit"
        initial={GROUP_A}
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[GROUP_A]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByLabelText('名稱')).toHaveValue('Group A')
    expect(screen.getByLabelText('入金點差')).toHaveValue(0.1)
    expect(screen.getByText('已加入本群組').closest('.pair-assigner-panel')?.textContent).toContain(
      'USD/JPY',
    )
  })

  it('shows a hint and moves a pair between panels', async () => {
    render(
      <SpreadGroupFormModal
        mode="edit"
        initial={GROUP_B}
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR]}
        groups={[GROUP_A, GROUP_B]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    // USD/JPY belongs to Group A (a different group), so the left panel shows
    // the move hint.
    expect(screen.getByText('目前屬於：Group A，核准後將自動移出')).toBeInTheDocument()

    const unassignedPanel = screen.getByText('未加入本群組').closest('.pair-assigner-panel') as HTMLElement
    await userEvent.click(within(unassignedPanel).getAllByText('加入 →')[0])

    const assignedPanel = screen.getByText('已加入本群組').closest('.pair-assigner-panel') as HTMLElement
    expect(assignedPanel.textContent).toContain('USD/JPY')

    await userEvent.click(within(assignedPanel).getByText('← 移除'))
    expect(assignedPanel.textContent).toContain('尚未加入任何幣種對')
  })

  it('validates required name and non-negative spreads', async () => {
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[]}
        groups={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('名稱為必填')).toBeInTheDocument()
    expect(screen.getByText('入金點差為必填，且不可小於 0')).toBeInTheDocument()
    expect(screen.getByText('出金點差為必填，且不可小於 0')).toBeInTheDocument()
  })

  it('submits the selected pair ids alongside the form fields', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[USD_JPY, USD_EUR, USD_TWD]}
        groups={[]}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.type(screen.getByLabelText('名稱'), 'New Group')
    await userEvent.type(screen.getByLabelText('入金點差'), '0.1')
    await userEvent.type(screen.getByLabelText('出金點差'), '0.2')

    const unassignedPanel = screen.getByText('未加入本群組').closest('.pair-assigner-panel') as HTMLElement
    await userEvent.click(within(unassignedPanel).getAllByText('加入 →')[0])

    await userEvent.click(screen.getByText('送出'))

    expect(onSubmit).toHaveBeenCalledWith({
      brandId: 1,
      name: 'New Group',
      depositSpread: 0.1,
      withdrawSpread: 0.2,
      currencyPairIds: [3],
    })
  })

  it('shows an inline 此名稱已被使用 error on a live-duplicate 409 without closing', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, { error: 'Spread group name already exists for this brand' }))
    render(
      <SpreadGroupFormModal
        mode="create"
        brandId={1}
        availablePairs={[]}
        groups={[]}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await userEvent.type(screen.getByLabelText('名稱'), 'Dup')
    await userEvent.type(screen.getByLabelText('入金點差'), '0.1')
    await userEvent.type(screen.getByLabelText('出金點差'), '0.2')
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('此名稱已被使用')).toBeInTheDocument()
    expect(screen.getByText('新增點差群組')).toBeInTheDocument()
  })
})
