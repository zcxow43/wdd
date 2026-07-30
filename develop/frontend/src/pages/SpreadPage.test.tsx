import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadPage } from './SpreadPage'
import { ToastProvider } from '../components/ToastProvider'
import { spreadDefaultApi, spreadGroupApi } from '../api/spreadApi'
import { brandApi } from '../api/brandApi'
import { currencyPairApi } from '../api/currencyPairApi'
import { auditApi } from '../audit/auditApi'
import { ApiError, NetworkError } from '../api/client'
import type { Brand } from '../types/brand'
import type { CurrencyPair } from '../types/currencyPair'
import type { SpreadDefault, SpreadGroup } from '../types/spread'
import type { AuditRequest } from '../audit/types'

vi.mock('../api/spreadApi', () => ({
  spreadDefaultApi: {
    list: vi.fn(),
    update: vi.fn(),
  },
  spreadGroupApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    resolve: vi.fn(),
  },
}))

vi.mock('../api/brandApi', () => ({
  brandApi: {
    list: vi.fn(),
    updateActive: vi.fn(),
  },
}))

vi.mock('../api/currencyPairApi', () => ({
  currencyPairApi: {
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

const mockedSpreadDefaultApi = vi.mocked(spreadDefaultApi)
const mockedSpreadGroupApi = vi.mocked(spreadGroupApi)
const mockedBrandApi = vi.mocked(brandApi)
const mockedCurrencyPairApi = vi.mocked(currencyPairApi)
const mockedAuditApi = vi.mocked(auditApi)

const AU: Brand = { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00' }

const DEFAULT_SPREAD: SpreadDefault = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

const GROUP: SpreadGroup = {
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

function pendingAuditRequest(entityType: string, entityId: number | null): AuditRequest {
  return {
    id: 99,
    entityType,
    actionType: 'UPDATE',
    entityId,
    status: 'PENDING',
    summary: null,
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
      <SpreadPage />
    </ToastProvider>,
  )
}

describe('SpreadPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  function stubAncillary() {
    mockedBrandApi.list.mockResolvedValue([AU])
    mockedCurrencyPairApi.list.mockResolvedValue([USD_JPY])
    mockedAuditApi.list.mockResolvedValue([])
  }

  it('loads and renders the default spread and groups for the auto-selected brand', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([GROUP])
    renderPage()

    expect(await screen.findByText('Group A')).toBeInTheDocument()
    expect(screen.getAllByText('0.1')).not.toHaveLength(0)
    expect(screen.getAllByText('0.2')).not.toHaveLength(0)
    expect(mockedSpreadDefaultApi.list).toHaveBeenCalledWith(1)
    expect(mockedSpreadGroupApi.list).toHaveBeenCalledWith(1)
  })

  it('shows the empty state when the brand has no groups yet', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前沒有點差群組')).toBeInTheDocument()
  })

  it('shows a network error toast and retry button when the initial load fails', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    mockedSpreadGroupApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findAllByText('資料載入失敗')).not.toHaveLength(0)
  })

  it('submits a default spread edit and shows the pending-approval toast', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([])
    mockedSpreadDefaultApi.update.mockResolvedValue(pendingAuditRequest('SPREAD_DEFAULT', 1))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前沒有點差群組')

    await user.click(screen.getByRole('button', { name: '編輯' }))
    const depositInput = screen.getByLabelText('入金點差')
    await user.clear(depositInput)
    await user.type(depositInput, '0.5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(mockedSpreadDefaultApi.update).toHaveBeenCalledWith(1, { depositSpread: 0.5, withdrawSpread: 0.2 }),
    )
    expect(await screen.findByText('已送出預設點差修改申請，待審核')).toBeInTheDocument()
    // The displayed value is unchanged since nothing has been approved yet.
    expect(screen.getByText('0.1')).toBeInTheDocument()
  })

  it('shows the pending-duplicate toast when the default spread already has a pending request', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([])
    mockedSpreadDefaultApi.update.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前沒有點差群組')

    await user.click(screen.getByRole('button', { name: '編輯' }))
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此項目已有待審核的異動申請')).toBeInTheDocument()
  })

  it('marks the default spread card as pending and disables the 編輯 button', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([])
    mockedAuditApi.list.mockImplementation((params) => {
      if (params?.entityType === 'SPREAD_DEFAULT') return Promise.resolve([pendingAuditRequest('SPREAD_DEFAULT', 1)])
      return Promise.resolve([])
    })
    renderPage()

    expect(await screen.findByText('審核中')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '編輯' })).toBeDisabled()
  })

  it('submits a create-group request and shows the pending-approval toast', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([])
    mockedSpreadGroupApi.create.mockResolvedValue(pendingAuditRequest('SPREAD_GROUP', null))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前沒有點差群組')

    await user.click(screen.getByRole('button', { name: '+ 新增群組' }))
    await user.type(screen.getByLabelText('名稱'), 'Group A')
    await user.type(screen.getByLabelText('入金點差'), '0.1')
    await user.type(screen.getByLabelText('出金點差'), '0.2')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() => expect(mockedSpreadGroupApi.create).toHaveBeenCalled())
    expect(await screen.findByText('已送出新增點差群組申請，待審核')).toBeInTheDocument()
  })

  it('submits a delete-group request after confirmation and shows the pending-approval toast', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([GROUP])
    mockedSpreadGroupApi.remove.mockResolvedValue(pendingAuditRequest('SPREAD_GROUP', 10))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Group A')

    await user.click(screen.getByText('刪除'))
    expect(
      await screen.findByText('確定要送出刪除點差群組「Group A」的申請嗎？核准後，其幣種對將回復為預設點差。'),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '確定' }))

    await waitFor(() => expect(mockedSpreadGroupApi.remove).toHaveBeenCalledWith(10))
    expect(await screen.findByText('已送出點差群組刪除申請，待審核')).toBeInTheDocument()
  })

  it('marks a group row as pending and disables its action buttons', async () => {
    stubAncillary()
    mockedSpreadDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
    mockedSpreadGroupApi.list.mockResolvedValue([GROUP])
    mockedAuditApi.list.mockImplementation((params) => {
      if (params?.entityType === 'SPREAD_GROUP') return Promise.resolve([pendingAuditRequest('SPREAD_GROUP', 10)])
      return Promise.resolve([])
    })
    renderPage()

    const row = await screen.findByText('Group A')
    const tr = row.closest('tr')!
    expect(within(tr).getByText('審核中')).toBeInTheDocument()
    expect(within(tr).getByText('編輯')).toBeDisabled()
    expect(within(tr).getByText('刪除')).toBeDisabled()
  })
})
