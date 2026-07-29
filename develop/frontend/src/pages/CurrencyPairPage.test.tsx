import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairPage } from './CurrencyPairPage'
import { ToastProvider } from '../components/ToastProvider'
import { currencyPairApi } from '../api/currencyPairApi'
import { brandApi } from '../api/brandApi'
import { currencyApi } from '../api/currencyApi'
import { auditApi } from '../audit/auditApi'
import { ApiError, NetworkError } from '../api/client'
import type { CurrencyPair } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'
import type { AuditRequest } from '../audit/types'

vi.mock('../api/currencyPairApi', () => ({
  currencyPairApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('../api/brandApi', () => ({
  brandApi: {
    list: vi.fn(),
    updateActive: vi.fn(),
  },
}))

vi.mock('../api/currencyApi', () => ({
  currencyApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('../audit/auditApi', () => ({
  auditApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}))

const mockedPairApi = vi.mocked(currencyPairApi)
const mockedBrandApi = vi.mocked(brandApi)
const mockedCurrencyApi = vi.mocked(currencyApi)
const mockedAuditApi = vi.mocked(auditApi)

const AU: Brand = { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '2025-01-01T00:00:00', updatedAt: '2025-01-01T00:00:00' }
const MONETA: Brand = {
  id: 2,
  code: 'MONETA',
  name: 'MONETA',
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: '新台幣',
  symbol: 'NT$',
  decimalPlaces: 0,
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}
const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'United States Dollar',
  nameZh: '美元',
  symbol: '$',
  decimalPlaces: 2,
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const PAIR: CurrencyPair = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 1,
  quoteCurrencyCode: 'TWD',
  rate: 32.5,
  rateType: 'MANUAL',
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

function pendingAuditRequest(entityId: number | null, actionType: AuditRequest['actionType'] = 'UPDATE'): AuditRequest {
  return {
    id: 99,
    entityType: 'CURRENCY_PAIR',
    actionType,
    entityId,
    status: 'PENDING',
    summary: 'AU · USD/TWD',
    before: null,
    after: null,
    requestedBy: null,
    requestedAt: '2026-07-29T10:00:00',
    reviewedBy: null,
    reviewedAt: null,
    rejectReason: null,
    createdAt: '2026-07-29T10:00:00',
    updatedAt: '2026-07-29T10:00:00',
  }
}

function renderPage() {
  return render(
    <ToastProvider>
      <CurrencyPairPage />
    </ToastProvider>,
  )
}

describe('CurrencyPairPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  function stubAncillary() {
    mockedBrandApi.list.mockResolvedValue([AU, MONETA])
    mockedCurrencyApi.list.mockResolvedValue([TWD, USD])
    mockedAuditApi.list.mockResolvedValue([])
  }

  it('loads and renders currency pairs on mount', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    renderPage()

    expect(await screen.findByRole('cell', { name: 'AU' })).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(mockedPairApi.list).toHaveBeenCalledWith({ brandId: undefined, active: undefined })
    expect(mockedAuditApi.list).toHaveBeenCalledWith({ entityType: 'CURRENCY_PAIR', status: 'PENDING' })
  })

  it('shows the empty state when no pairs match', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前沒有符合條件的幣種對')).toBeInTheDocument()
  })

  it('shows a network error toast when the initial load fails', async () => {
    stubAncillary()
    mockedPairApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('refetches with the brand filter when it changes', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    await user.selectOptions(screen.getByLabelText('品牌篩選'), '2')
    await waitFor(() =>
      expect(mockedPairApi.list).toHaveBeenLastCalledWith({ brandId: 2, active: undefined }),
    )
  })

  it('refetches with the status filter when it changes', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    await user.selectOptions(screen.getByLabelText('狀態篩選'), 'ACTIVE')
    await waitFor(() =>
      expect(mockedPairApi.list).toHaveBeenLastCalledWith({ brandId: undefined, active: true }),
    )
  })

  it('submits a create request through the add modal, closes it, and shows the pending-approval toast', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([])
    mockedPairApi.create.mockResolvedValue(pendingAuditRequest(null, 'CREATE'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前沒有符合條件的幣種對')

    await user.click(screen.getByRole('button', { name: '+ Add' }))
    await user.selectOptions(screen.getByLabelText('品牌'), '1')
    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('匯率'), '32.5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(mockedPairApi.create).toHaveBeenCalled())
    expect(await screen.findByText('已送出新增申請，待審核')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '儲存' })).not.toBeInTheDocument()
  })

  it('submits an edit request and shows the pending-approval toast, leaving the row unchanged', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockResolvedValue(pendingAuditRequest(1, 'UPDATE'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Edit'))

    const rateInput = screen.getByLabelText('匯率')
    await user.clear(rateInput)
    await user.type(rateInput, '33')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(mockedPairApi.update).toHaveBeenCalledWith(1, expect.objectContaining({ rate: 33 })))
    expect(await screen.findByText('已送出修改申請，待審核')).toBeInTheDocument()
    // The row keeps displaying the pre-change rate since nothing was approved yet.
    expect(screen.getByText('32.5')).toBeInTheDocument()
  })

  it('shows a not-found toast and refreshes when editing a pair that was already deleted', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(new ApiError(404, { error: 'Currency pair not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Edit'))
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('幣種對不存在，請重新整理頁面')).toBeInTheDocument()
  })

  it('shows the pending-duplicate conflict toast when editing a pair that already has a pending request', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Edit'))
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
  })

  it('submits a delete request after confirmation and shows the pending-approval toast', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockResolvedValue(pendingAuditRequest(1, 'DELETE'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Delete'))

    expect(
      await screen.findByText('確定要送出刪除 AU 品牌幣種對 USD/TWD 的申請嗎？'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '確定' }))

    await waitFor(() => expect(mockedPairApi.remove).toHaveBeenCalledWith(1))
    expect(await screen.findByText('已送出刪除申請，待審核')).toBeInTheDocument()
  })

  it('shows a not-found toast when deleting a pair that no longer exists', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockRejectedValue(new ApiError(404, { error: 'Currency pair not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Delete'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('幣種對不存在，請重新整理頁面')).toBeInTheDocument()
  })

  it('shows the pending-duplicate conflict toast when deleting a pair that already has a pending request', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('cell', { name: 'AU' })

    const row = screen.getByRole('cell', { name: 'AU' }).closest('tr')!
    await user.click(within(row).getByText('Delete'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
  })

  it('marks a row with a pending request as 審核中 and disables its Edit/Delete buttons', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedAuditApi.list.mockResolvedValue([pendingAuditRequest(1, 'UPDATE')])
    renderPage()

    const row = await screen.findByRole('cell', { name: 'AU' })
    const tr = row.closest('tr')!
    expect(within(tr).getByText('審核中')).toBeInTheDocument()
    expect(within(tr).getByText('Edit')).toBeDisabled()
    expect(within(tr).getByText('Delete')).toBeDisabled()
  })

  it('does not mark a row as pending when the pending request belongs to a different pair', async () => {
    stubAncillary()
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedAuditApi.list.mockResolvedValue([pendingAuditRequest(999, 'UPDATE')])
    renderPage()

    const row = await screen.findByRole('cell', { name: 'AU' })
    const tr = row.closest('tr')!
    expect(within(tr).queryByText('審核中')).not.toBeInTheDocument()
    expect(within(tr).getByText('Edit')).not.toBeDisabled()
  })
})
