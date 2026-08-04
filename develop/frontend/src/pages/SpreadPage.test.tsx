import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SpreadPage } from './SpreadPage'
import { ToastProvider } from '../components/ToastProvider'
import { spreadDefaultApi, spreadGroupApi } from '../api/spreadApi'
import { brandApi } from '../api/brandApi'
import { currencyPairApi } from '../api/currencyPairApi'
import { auditApi } from '../audit/auditApi'
import { ApiError, NetworkError } from '../api/client'
import type { SpreadDefault, SpreadGroup } from '../types/spread'
import type { Brand } from '../types/brand'
import type { CurrencyPair } from '../types/currencyPair'
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

const mockedDefaultApi = vi.mocked(spreadDefaultApi)
const mockedGroupApi = vi.mocked(spreadGroupApi)
const mockedBrandApi = vi.mocked(brandApi)
const mockedPairApi = vi.mocked(currencyPairApi)
const mockedAuditApi = vi.mocked(auditApi)

const AU: Brand = { id: 1, code: 'AU', name: 'AU', active: true, createdAt: '', updatedAt: '' }
const PUG: Brand = { id: 3, code: 'PUG', name: 'PUG', active: true, createdAt: '', updatedAt: '' }

const DEFAULT_SPREAD: SpreadDefault = {
  id: 1,
  brandId: 1,
  brandCode: 'AU',
  depositSpread: 0.1,
  withdrawSpread: 0.2,
  createdAt: '',
  updatedAt: '',
}

const USD_JPY: CurrencyPair = {
  id: 3,
  brandId: 1,
  brandCode: 'AU',
  baseCurrencyId: 20,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 21,
  quoteCurrencyCode: 'JPY',
  rate: 1,
  rateType: 'MANUAL',
  active: true,
  createdAt: '',
  updatedAt: '',
}

const USD_EUR: CurrencyPair = {
  ...USD_JPY,
  id: 4,
  quoteCurrencyId: 22,
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
  createdAt: '',
  updatedAt: '',
}

function auditRequest(overrides: Partial<AuditRequest>): AuditRequest {
  return {
    id: 100,
    entityType: 'SPREAD_DEFAULT',
    actionType: 'UPDATE',
    entityId: 1,
    status: 'PENDING',
    summary: null,
    before: {},
    after: {},
    requestedBy: null,
    requestedAt: '2026-08-01T00:00:00',
    reviewedBy: null,
    reviewedAt: null,
    rejectReason: null,
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-01T00:00:00',
    ...overrides,
  }
}

function renderPage() {
  return render(
    <ToastProvider>
      <SpreadPage />
    </ToastProvider>,
  )
}

/** The 預設點差 card — scoped queries avoid colliding with the group table's own 編輯/審核中. */
function defaultCard(): HTMLElement {
  return screen.getByText('預設點差').closest('.search-table-card') as HTMLElement
}

beforeEach(() => {
  vi.resetAllMocks()
  mockedBrandApi.list.mockResolvedValue([AU, PUG])
  mockedDefaultApi.list.mockResolvedValue([DEFAULT_SPREAD])
  mockedGroupApi.list.mockResolvedValue([GROUP_A])
  mockedPairApi.list.mockResolvedValue([USD_JPY, USD_EUR])
  mockedAuditApi.list.mockResolvedValue([])
})

describe('SpreadPage', () => {
  it('auto-selects the first active brand and loads its default spread and groups', async () => {
    renderPage()

    expect(await screen.findByText('入金：0.1')).toBeInTheDocument()
    expect(screen.getByText('出金：0.2')).toBeInTheDocument()
    expect(screen.getByText('Group A')).toBeInTheDocument()
    expect(mockedDefaultApi.list).toHaveBeenCalledWith(1)
    expect(mockedGroupApi.list).toHaveBeenCalledWith(1)
    expect(mockedPairApi.list).toHaveBeenCalledWith({ brandId: 1, active: true })
  })

  it('shows a placeholder when the brand filter is set to All', async () => {
    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.selectOptions(screen.getByLabelText('品牌'), '全部')

    expect(screen.getByText('請選擇品牌')).toBeInTheDocument()
  })

  it('shows a network-error state with a retry button when the load fails', async () => {
    mockedDefaultApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('資料載入失敗')).toBeInTheDocument()
    expect(screen.getByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('refetches for the newly selected brand when the brand filter changes', async () => {
    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.selectOptions(screen.getByLabelText('品牌'), 'PUG')

    await waitFor(() => expect(mockedDefaultApi.list).toHaveBeenLastCalledWith(3))
    expect(mockedGroupApi.list).toHaveBeenLastCalledWith(3)
  })

  it('shows a 審核中 badge and disables 編輯 when the default spread has a pending request', async () => {
    mockedAuditApi.list.mockImplementation(async ({ entityType }: { entityType?: string } = {}) => {
      if (entityType === 'SPREAD_DEFAULT') {
        return [auditRequest({ entityType: 'SPREAD_DEFAULT', entityId: 1 })]
      }
      return []
    })

    renderPage()
    await screen.findByText('入金：0.1')

    expect(within(defaultCard()).getByText('審核中')).toBeInTheDocument()
    expect(within(defaultCard()).getByText('編輯')).toBeDisabled()
  })

  it('edits the default spread and shows the submitted-for-approval toast without changing the displayed values', async () => {
    mockedDefaultApi.update.mockResolvedValue(auditRequest({ entityType: 'SPREAD_DEFAULT', id: 200 }))

    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.click(within(defaultCard()).getByText('編輯'))
    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.type(screen.getByLabelText('入金點差'), '0.5')
    await userEvent.click(screen.getByText('送出'))

    await waitFor(() =>
      expect(mockedDefaultApi.update).toHaveBeenCalledWith(1, { depositSpread: 0.5, withdrawSpread: 0.2 }),
    )
    expect(await screen.findByText('已送出預設點差修改申請，待審核')).toBeInTheDocument()
    expect(screen.getByText('入金：0.1')).toBeInTheDocument()
  })

  it('shows the pending-duplicate toast, closes the modal, and refreshes on a default-spread 409', async () => {
    mockedDefaultApi.update.mockRejectedValue(
      new ApiError(409, { error: 'A pending audit request already exists for this entity' }),
    )

    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.click(within(defaultCard()).getByText('編輯'))
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('此項目已有待審核的異動申請')).toBeInTheDocument()
    expect(screen.queryByText('編輯預設點差')).not.toBeInTheDocument()
  })

  it('creates a group with two pairs and shows the submitted-for-approval toast', async () => {
    // No pre-existing groups for this test, so neither pair carries a "belongs to
    // another group" hint and both can be freely added to the new group.
    mockedGroupApi.list.mockResolvedValue([])
    mockedGroupApi.create.mockResolvedValue(
      auditRequest({ entityType: 'SPREAD_GROUP', actionType: 'CREATE', entityId: null, id: 201 }),
    )

    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.click(screen.getByText('+新增群組'))
    await userEvent.type(screen.getByLabelText('名稱'), 'New Group')
    await userEvent.type(screen.getByLabelText('入金點差'), '0.1')
    await userEvent.type(screen.getByLabelText('出金點差'), '0.2')

    // Add both unassigned pairs (USD/JPY, USD/EUR) one at a time — re-querying
    // after each click since the row moves to the other panel and the DOM updates.
    await userEvent.click(screen.getAllByText('加入 →')[0])
    await userEvent.click(screen.getAllByText('加入 →')[0])

    await userEvent.click(screen.getByText('送出'))

    await waitFor(() =>
      expect(mockedGroupApi.create).toHaveBeenCalledWith({
        brandId: 1,
        name: 'New Group',
        depositSpread: 0.1,
        withdrawSpread: 0.2,
        currencyPairIds: [3, 4],
      }),
    )
    expect(await screen.findByText('已送出新增點差群組申請，待審核')).toBeInTheDocument()
    // Not yet approved — still only the original group in the table.
    expect(screen.queryByText('New Group')).not.toBeInTheDocument()
  })

  it('shows an updated confirm-dialog message and deletes a group on confirm', async () => {
    mockedGroupApi.remove.mockResolvedValue(
      auditRequest({ entityType: 'SPREAD_GROUP', actionType: 'DELETE', entityId: 10, id: 202 }),
    )

    renderPage()
    await screen.findByText('Group A')

    await userEvent.click(screen.getByText('刪除'))
    expect(
      screen.getByText('確定要送出刪除點差群組「Group A」的申請嗎？核准後，其幣種對將回復為預設點差。'),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(mockedGroupApi.remove).toHaveBeenCalledWith(10))
    expect(await screen.findByText('已送出點差群組刪除申請，待審核')).toBeInTheDocument()
    expect(screen.getByText('Group A')).toBeInTheDocument()
  })

  it('shows a 審核中 badge and disables actions for a pending group row', async () => {
    mockedAuditApi.list.mockImplementation(async ({ entityType }: { entityType?: string } = {}) => {
      if (entityType === 'SPREAD_GROUP') {
        return [auditRequest({ entityType: 'SPREAD_GROUP', actionType: 'UPDATE', entityId: 10 })]
      }
      return []
    })

    renderPage()
    await screen.findByText('Group A')

    const row = (await screen.findByText('審核中')).closest('tr') as HTMLElement
    expect(within(row).getByText('編輯')).toBeDisabled()
    expect(within(row).getByText('刪除')).toBeDisabled()
  })

  it('shows the inline 此名稱已被使用 error for a live-duplicate 409 on group create', async () => {
    mockedGroupApi.create.mockRejectedValue(
      new ApiError(409, { error: 'Spread group name already exists for this brand' }),
    )

    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.click(screen.getByText('+新增群組'))
    await userEvent.type(screen.getByLabelText('名稱'), 'Group A')
    await userEvent.type(screen.getByLabelText('入金點差'), '0.1')
    await userEvent.type(screen.getByLabelText('出金點差'), '0.2')
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('此名稱已被使用')).toBeInTheDocument()
    expect(screen.getByText('新增點差群組')).toBeInTheDocument()
  })

  it('shows the pending-duplicate toast and closes the modal for a pending-duplicate 409 on group create', async () => {
    mockedGroupApi.create.mockRejectedValue(
      new ApiError(409, { error: 'A pending create request already exists for this brand/name combination' }),
    )

    renderPage()
    await screen.findByText('入金：0.1')

    await userEvent.click(screen.getByText('+新增群組'))
    await userEvent.type(screen.getByLabelText('名稱'), 'Group A')
    await userEvent.type(screen.getByLabelText('入金點差'), '0.1')
    await userEvent.type(screen.getByLabelText('出金點差'), '0.2')
    await userEvent.click(screen.getByText('送出'))

    expect(await screen.findByText('此項目已有待審核的異動申請')).toBeInTheDocument()
    expect(screen.queryByText('新增點差群組')).not.toBeInTheDocument()
  })
})
