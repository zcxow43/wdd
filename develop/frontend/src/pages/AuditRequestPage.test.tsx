import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditRequestPage from './AuditRequestPage'
import type { AuditRequestDetail, AuditRequestSummary } from '../api/audit'
import {
  approveAuditRequest,
  fetchAuditRequest,
  fetchAuditRequests,
  rejectAuditRequest,
} from '../api/audit'
import type { Brand } from '../api/brands'
import { fetchBrands } from '../api/brands'
import { ApiError } from '../api/http'

vi.mock('../api/audit', () => ({
  fetchAuditRequests: vi.fn(),
  fetchAuditRequest: vi.fn(),
  approveAuditRequest: vi.fn(),
  rejectAuditRequest: vi.fn(),
}))

vi.mock('../api/brands', () => ({
  fetchBrands: vi.fn(),
}))

const mockedFetchAuditRequests = vi.mocked(fetchAuditRequests)
const mockedFetchAuditRequest = vi.mocked(fetchAuditRequest)
const mockedApprove = vi.mocked(approveAuditRequest)
const mockedReject = vi.mocked(rejectAuditRequest)
const mockedFetchBrands = vi.mocked(fetchBrands)

function makeBrands(): Brand[] {
  return [
    {
      id: 1,
      code: 'au',
      name: 'AU',
      active: true,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 2,
      code: 'vt',
      name: 'VT',
      active: true,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function makeSummary(
  overrides: Partial<AuditRequestSummary> = {},
): AuditRequestSummary {
  return {
    id: 1,
    entityType: 'BRAND_SPREAD',
    actionType: 'UPDATE',
    entityId: 1,
    brandId: 1,
    summary: '調整 au 預設點差',
    status: 'PENDING',
    requestedBy: 'alice',
    requestedAt: '2026-08-20T10:00:00',
    reviewedBy: null,
    reviewedAt: null,
    reviewComment: null,
    applyError: null,
    ...overrides,
  }
}

function makeDetail(
  overrides: Partial<AuditRequestDetail> = {},
): AuditRequestDetail {
  return {
    ...makeSummary(),
    beforeData: { depositSpread: 0.0005, withdrawalSpread: 0.0005 },
    afterData: { depositSpread: 0.001, withdrawalSpread: 0.0005 },
    ...overrides,
  }
}

function getDataRows() {
  return screen.getAllByRole('row').slice(1)
}

function getToastText() {
  return screen.getByRole('alert').textContent
}

describe('AuditRequestPage', () => {
  beforeEach(() => {
    mockedFetchAuditRequests.mockReset()
    mockedFetchAuditRequest.mockReset()
    mockedApprove.mockReset()
    mockedReject.mockReset()
    mockedFetchBrands.mockReset()
    mockedFetchBrands.mockResolvedValue(makeBrands())
    window.localStorage.clear()
  })

  it('loads pending requests by default and renders the row columns', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])

    render(<AuditRequestPage />)

    await waitFor(() => {
      expect(mockedFetchAuditRequests).toHaveBeenCalledWith({
        status: 'PENDING',
        entityType: undefined,
        brandId: undefined,
      })
    })

    expect(await screen.findByText('調整 au 預設點差')).toBeInTheDocument()
    const row = getDataRows()[0]
    const cells = within(row).getAllByRole('cell')
    expect(cells[1].textContent).toBe('au')
    expect(cells[2].textContent).toBe('預設點差')
    expect(cells[3].textContent).toBe('修改')
    expect(cells[5].textContent).toBe('alice')
    expect(within(row).getByText('待審核')).toBeInTheDocument()
  })

  it('combines status, brand, and type filters into query params', async () => {
    mockedFetchAuditRequests.mockResolvedValue([])
    const user = userEvent.setup()

    render(<AuditRequestPage />)

    await waitFor(() => expect(mockedFetchAuditRequests).toHaveBeenCalledTimes(1))

    await user.click(screen.getByRole('tab', { name: '全部' }))
    await waitFor(() =>
      expect(mockedFetchAuditRequests).toHaveBeenLastCalledWith({
        status: undefined,
        entityType: undefined,
        brandId: undefined,
      }),
    )

    await user.selectOptions(screen.getByLabelText('品牌'), '2')
    await waitFor(() =>
      expect(mockedFetchAuditRequests).toHaveBeenLastCalledWith({
        status: undefined,
        entityType: undefined,
        brandId: 2,
      }),
    )

    await user.selectOptions(screen.getByLabelText('類型'), 'CURRENCY_PAIR')
    await waitFor(() =>
      expect(mockedFetchAuditRequests).toHaveBeenLastCalledWith({
        status: undefined,
        entityType: 'CURRENCY_PAIR',
        brandId: 2,
      }),
    )
  })

  it('shows the empty state when the filter matches nothing', async () => {
    mockedFetchAuditRequests.mockResolvedValue([])

    render(<AuditRequestPage />)

    expect(
      await screen.findByText('目前沒有符合條件的審核申請'),
    ).toBeInTheDocument()
  })

  it('renders only 檢視 for rows that are not PENDING', async () => {
    mockedFetchAuditRequests.mockResolvedValue([
      makeSummary({ status: 'APPROVED' }),
    ])

    render(<AuditRequestPage />)

    await screen.findByText('調整 au 預設點差')
    const row = screen.getAllByRole('row')[1]
    expect(within(row).getByText('檢視')).toBeInTheDocument()
    expect(within(row).queryByText('核准')).not.toBeInTheDocument()
    expect(within(row).queryByText('駁回')).not.toBeInTheDocument()
  })

  it('shows a field-by-field diff built from beforeData/afterData in the detail modal', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedFetchAuditRequest.mockResolvedValue(makeDetail())
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('檢視'))

    await waitFor(() => expect(mockedFetchAuditRequest).toHaveBeenCalledWith(1))
    expect(await screen.findByText('depositSpread')).toBeInTheDocument()
    expect(screen.getByText('0.0005')).toBeInTheDocument()
    expect(screen.getByText('0.001')).toBeInTheDocument()
    // withdrawalSpread is unchanged, so it should not appear as a diff row
    expect(screen.queryByText('withdrawalSpread')).not.toBeInTheDocument()
  })

  it('renders CREATE rows with — for 原值 and DELETE rows with — for 新值', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary({ id: 2 })])
    mockedFetchAuditRequest.mockResolvedValue(
      makeDetail({
        id: 2,
        actionType: 'CREATE',
        beforeData: null,
        afterData: { name: 'VIP 群組' },
      }),
    )
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')
    await user.click(screen.getByText('檢視'))

    expect(await screen.findByText('name')).toBeInTheDocument()
    expect(screen.getByText('VIP 群組')).toBeInTheDocument()
    const diffRow = screen.getByText('name').closest('tr')
    expect(diffRow).not.toBeNull()
    expect(within(diffRow as HTMLElement).getByText('—')).toBeInTheDocument()
  })

  it('approves a request and refreshes the list on success', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedApprove.mockResolvedValue(makeDetail({ status: 'APPROVED' }))
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('核准'))
    await user.click(screen.getByRole('button', { name: '確認' }))

    await waitFor(() =>
      expect(mockedApprove).toHaveBeenCalledWith(1, '', 'system'),
    )
    expect(await screen.findByText('已核准，變更已套用')).toBeInTheDocument()
    await waitFor(() =>
      expect(mockedFetchAuditRequests).toHaveBeenCalledTimes(2),
    )
  })

  it('keeps the request 待審核 and surfaces the server message on a 422', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedApprove.mockRejectedValue(
      new ApiError(422, '資料已變更，請重新整理', {
        error: '資料已變更，請重新整理',
        auditRequestId: 1,
      }),
    )
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('核准'))
    await user.click(screen.getByRole('button', { name: '確認' }))

    expect(
      await screen.findByText('核准失敗：資料已變更，請重新整理'),
    ).toBeInTheDocument()
    // request row is still PENDING/待審核
    const row = getDataRows()[0]
    expect(within(row).getByText('待審核')).toBeInTheDocument()
  })

  it('requires a non-empty 駁回原因 and does not call the API when blank', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('駁回'))
    await user.click(screen.getByRole('button', { name: '確認' }))

    expect(await screen.findByText('請填寫駁回原因')).toBeInTheDocument()
    expect(mockedReject).not.toHaveBeenCalled()
  })

  it('rejects a request with a comment and marks it 已駁回', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedReject.mockResolvedValue(makeDetail({ status: 'REJECTED' }))
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('駁回'))
    await user.type(screen.getByLabelText('駁回原因'), '資料有誤')
    await user.click(screen.getByRole('button', { name: '確認' }))

    await waitFor(() =>
      expect(mockedReject).toHaveBeenCalledWith(1, '資料有誤', 'system'),
    )
    await waitFor(() => expect(getToastText()).toBe('已駁回×'))
  })

  it('shows the already-handled toast and refreshes the list on a 409', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedApprove.mockRejectedValue(
      new ApiError(409, 'Audit request 1 is not PENDING'),
    )
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    await user.click(screen.getByText('核准'))
    await user.click(screen.getByRole('button', { name: '確認' }))

    expect(
      await screen.findByText('此申請已被處理，請重新整理'),
    ).toBeInTheDocument()
    await waitFor(() =>
      expect(mockedFetchAuditRequests).toHaveBeenCalledTimes(2),
    )
  })

  it('sends the X-Actor header sourced from the 審核人員 input and persists it', async () => {
    mockedFetchAuditRequests.mockResolvedValue([makeSummary()])
    mockedApprove.mockResolvedValue(makeDetail({ status: 'APPROVED' }))
    const user = userEvent.setup()

    render(<AuditRequestPage />)
    await screen.findByText('調整 au 預設點差')

    const actorInput = screen.getByLabelText('審核人員')
    await user.clear(actorInput)
    await user.type(actorInput, 'bob')

    expect(window.localStorage.getItem('wdd_audit_actor')).toBe('bob')

    await user.click(screen.getByText('核准'))
    await user.click(screen.getByRole('button', { name: '確認' }))

    await waitFor(() =>
      expect(mockedApprove).toHaveBeenCalledWith(1, '', 'bob'),
    )
  })

  it('restores the persisted actor from localStorage on load', async () => {
    window.localStorage.setItem('wdd_audit_actor', 'carol')
    mockedFetchAuditRequests.mockResolvedValue([])

    render(<AuditRequestPage />)

    await waitFor(() =>
      expect(screen.getByLabelText('審核人員')).toHaveValue('carol'),
    )
  })

  it('shows an inline error with a retry button when the list fails to load', async () => {
    mockedFetchAuditRequests.mockRejectedValueOnce(new Error('boom'))
    mockedFetchAuditRequests.mockResolvedValueOnce([makeSummary()])
    const user = userEvent.setup()

    render(<AuditRequestPage />)

    expect(
      await screen.findByText('載入審核申請清單失敗，請稍後再試。'),
    ).toBeInTheDocument()

    await user.click(screen.getByText('重試'))

    expect(await screen.findByText('調整 au 預設點差')).toBeInTheDocument()
  })
})
