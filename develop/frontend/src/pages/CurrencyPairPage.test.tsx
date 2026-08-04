import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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

const AU: Brand = { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '', updatedAt: '' }
const PUG: Brand = { id: 3, code: 'PUG', name: 'PUG', active: true, createdAt: '', updatedAt: '' }

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'US Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 2,
  createdAt: '',
  updatedAt: '',
}
const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 0,
  createdAt: '',
  updatedAt: '',
}

const PAIR: CurrencyPair = {
  id: 10,
  brandId: 3,
  brandCode: 'PUG',
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

const AUDIT_REQUEST: AuditRequest = {
  id: 100,
  entityType: 'CURRENCY_PAIR',
  actionType: 'UPDATE',
  entityId: 10,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: {},
  after: {},
  requestedBy: null,
  requestedAt: '2026-08-01T00:00:00',
  reviewedBy: null,
  reviewedAt: null,
  rejectReason: null,
  createdAt: '2026-08-01T00:00:00',
  updatedAt: '2026-08-01T00:00:00',
}

function renderPage() {
  return render(
    <ToastProvider>
      <CurrencyPairPage />
    </ToastProvider>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  mockedBrandApi.list.mockResolvedValue([AU, PUG])
  mockedCurrencyApi.list.mockResolvedValue([USD, TWD])
  mockedAuditApi.list.mockResolvedValue([])
})

describe('CurrencyPairPage', () => {
  it('loads pairs from the API on mount and renders the table', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])

    renderPage()

    expect((await screen.findAllByText('PUG')).length).toBeGreaterThan(0)
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(mockedPairApi.list).toHaveBeenCalledWith({ brandId: undefined, active: undefined })
  })

  it('does not render an Add button', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])

    renderPage()
    await screen.findByText('32.5')

    expect(screen.queryByText('+ Add')).not.toBeInTheDocument()
  })

  it('shows an empty state when there are no pairs', async () => {
    mockedPairApi.list.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('目前沒有幣種對資料')).toBeInTheDocument()
  })

  it('shows a network-error toast when the initial load fails', async () => {
    mockedPairApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('refetches with brandId when the brand filter changes', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])

    renderPage()
    await screen.findByText('32.5')

    await userEvent.selectOptions(screen.getByLabelText('品牌'), 'PUG')

    await waitFor(() =>
      expect(mockedPairApi.list).toHaveBeenLastCalledWith({ brandId: 3, active: undefined }),
    )
  })

  it('refetches with active when the status filter changes', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])

    renderPage()
    await screen.findByText('32.5')

    await userEvent.selectOptions(screen.getByLabelText('狀態'), 'Active')

    await waitFor(() =>
      expect(mockedPairApi.list).toHaveBeenLastCalledWith({ brandId: undefined, active: true }),
    )
  })

  it('fetches pending audit requests and badges/disables the matching row', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedAuditApi.list.mockResolvedValue([AUDIT_REQUEST])

    renderPage()
    await screen.findByText('32.5')

    expect(await screen.findByText('審核中')).toBeInTheDocument()
    expect(screen.getByText('編輯')).toBeDisabled()
    expect(screen.getByText('刪除')).toBeDisabled()
    expect(mockedAuditApi.list).toHaveBeenCalledWith({ entityType: 'CURRENCY_PAIR', status: 'PENDING' })
  })

  it('does not badge a row when the pending request is for a different pair id', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedAuditApi.list.mockResolvedValue([{ ...AUDIT_REQUEST, entityId: 999 }])

    renderPage()
    await screen.findByText('32.5')

    expect(screen.queryByText('審核中')).not.toBeInTheDocument()
    expect(screen.getByText('編輯')).not.toBeDisabled()
  })

  it('edits a pair through the edit modal and shows the submitted-for-approval toast without applying the change', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockResolvedValue({ ...AUDIT_REQUEST, id: 200 })

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('編輯'))
    await userEvent.clear(screen.getByLabelText('匯率'))
    await userEvent.type(screen.getByLabelText('匯率'), '40')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(mockedPairApi.update).toHaveBeenCalledWith(10, {
        brandId: 3,
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        rateType: 'MANUAL',
        rate: 40,
        active: true,
      }),
    )
    expect(await screen.findByText('已送出修改申請，待審核')).toBeInTheDocument()
    // The table still shows the pre-change value — no optimistic update.
    expect(screen.getByText('32.5')).toBeInTheDocument()
  })

  it('shows a confirmation dialog reflecting a submitted request, and deletes on confirm', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockResolvedValue({ ...AUDIT_REQUEST, actionType: 'DELETE', id: 201 })

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('刪除'))
    expect(
      screen.getByText('確定要送出刪除 PUG 品牌幣種對 USD/TWD 的申請嗎？'),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(mockedPairApi.remove).toHaveBeenCalledWith(10))
    expect(await screen.findByText('已送出刪除申請，待審核')).toBeInTheDocument()
  })

  it('shows the pending-duplicate 409 toast on edit, closes the modal, and refreshes', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }),
    )

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('編輯'))
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
    expect(screen.queryByText('編輯幣種對')).not.toBeInTheDocument()
  })

  it('shows the live-duplicate inline error in the modal (not a toast) on a live-duplicate 409', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(
      new ApiError(409, { error: 'Currency pair already exists for this brand/base/quote combination' }),
    )

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('編輯'))
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此品牌已存在相同的幣種對')).toBeInTheDocument()
    expect(screen.getByText('編輯幣種對')).toBeInTheDocument()
  })

  it('shows the pending-duplicate 409 toast on delete', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }),
    )

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('此幣種對已有待審核的異動申請')).toBeInTheDocument()
  })

  it('shows a 404 toast and refreshes when deleting an already-deleted pair', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.remove.mockRejectedValue(new ApiError(404, { error: 'Currency pair not found', id: 10 }))

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('幣種對不存在，請重新整理頁面')).toBeInTheDocument()
    expect(mockedPairApi.list).toHaveBeenCalledTimes(2)
  })

  it('shows the brand-not-found 404 toast on edit', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(new ApiError(404, { error: 'Brand not found', id: 3 }))

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('編輯'))
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('品牌不存在，請重新整理頁面')).toBeInTheDocument()
  })

  it('shows the currency-not-found 404 toast on edit for any other 404 message', async () => {
    mockedPairApi.list.mockResolvedValue([PAIR])
    mockedPairApi.update.mockRejectedValue(new ApiError(404, { error: 'Currency not found', id: 2 }))

    renderPage()
    await screen.findByText('32.5')

    await userEvent.click(screen.getByText('編輯'))
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('幣種不存在，請重新整理頁面')).toBeInTheDocument()
  })
})
