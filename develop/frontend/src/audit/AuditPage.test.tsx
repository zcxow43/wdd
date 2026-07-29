import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditPage } from './AuditPage'
import { ToastProvider } from '../components/ToastProvider'
import { auditApi } from './auditApi'
import { ApiError, NetworkError } from '../api/client'
import type { AuditRequest } from './types'

vi.mock('./auditApi', () => ({
  auditApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}))

const mockedAuditApi = vi.mocked(auditApi)

const PENDING_REQUEST: AuditRequest = {
  id: 1,
  entityType: 'CURRENCY_PAIR',
  actionType: 'CREATE',
  entityId: null,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: null,
  after: { rate: 32.5 },
  requestedBy: 'Alice',
  requestedAt: '2026-07-29T10:00:00',
  reviewedBy: null,
  reviewedAt: null,
  rejectReason: null,
  createdAt: '2026-07-29T10:00:00',
  updatedAt: '2026-07-29T10:00:00',
}

function renderPage() {
  return render(
    <ToastProvider>
      <AuditPage />
    </ToastProvider>,
  )
}

describe('AuditPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('loads pending requests by default', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    renderPage()

    expect(await screen.findByText('PUG · USD/TWD')).toBeInTheDocument()
    expect(mockedAuditApi.list).toHaveBeenCalledWith({ status: 'PENDING', entityType: undefined })
  })

  it('shows the empty state when no requests match', async () => {
    mockedAuditApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前沒有符合條件的審核申請')).toBeInTheDocument()
  })

  it('shows a network error toast when the initial load fails', async () => {
    mockedAuditApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('refetches with the status filter when it changes', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.selectOptions(screen.getByLabelText('審核狀態篩選'), '全部')
    await waitFor(() =>
      expect(mockedAuditApi.list).toHaveBeenLastCalledWith({ status: undefined, entityType: undefined }),
    )
  })

  it('populates the entity type filter from the currently-loaded results and refetches on change', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    const select = screen.getByLabelText('實體類型篩選')
    expect(within(select).getByText('CURRENCY_PAIR')).toBeInTheDocument()

    await user.selectOptions(select, 'CURRENCY_PAIR')
    await waitFor(() =>
      expect(mockedAuditApi.list).toHaveBeenLastCalledWith({ status: 'PENDING', entityType: 'CURRENCY_PAIR' }),
    )
  })

  it('opens the review modal when 查看 is clicked', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))

    expect(screen.getByText('審核異動申請 — 新增')).toBeInTheDocument()
    expect(screen.getByText('（新增，無先前資料）')).toBeInTheDocument()
  })

  it('approves a request, shows success, closes the modal and refreshes the list', async () => {
    mockedAuditApi.list
      .mockResolvedValueOnce([PENDING_REQUEST])
      .mockResolvedValueOnce([{ ...PENDING_REQUEST, status: 'APPROVED' }])
    mockedAuditApi.approve.mockResolvedValue({ ...PENDING_REQUEST, status: 'APPROVED' })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    await waitFor(() => expect(mockedAuditApi.approve).toHaveBeenCalledWith(1))
    expect(screen.queryByText('審核異動申請 — 新增')).not.toBeInTheDocument()
    expect(await screen.findByText('已核准此異動申請')).toBeInTheDocument()
    await waitFor(() => expect(mockedAuditApi.list).toHaveBeenCalledTimes(2))
  })

  it('rejects a request with a reason, shows success, closes the modal and refreshes the list', async () => {
    mockedAuditApi.list
      .mockResolvedValueOnce([PENDING_REQUEST])
      .mockResolvedValueOnce([{ ...PENDING_REQUEST, status: 'REJECTED' }])
    mockedAuditApi.reject.mockResolvedValue({ ...PENDING_REQUEST, status: 'REJECTED' })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '拒絕' }))
    await user.type(screen.getByLabelText('拒絕原因'), '匯率過高')
    await user.click(screen.getByRole('button', { name: '確認拒絕' }))

    await waitFor(() => expect(mockedAuditApi.reject).toHaveBeenCalledWith(1, '匯率過高'))
    expect(screen.queryByText('審核異動申請 — 新增')).not.toBeInTheDocument()
    expect(await screen.findByText('已拒絕此異動申請')).toBeInTheDocument()
  })

  it('shows a not-found toast, closes the modal and refreshes when approving a request that no longer exists', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedAuditApi.approve.mockRejectedValue(
      new ApiError(404, { error: 'Audit request not found', id: 1 }, 'Not Found'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('審核申請不存在，請重新整理頁面')).toBeInTheDocument()
    expect(screen.queryByText('審核異動申請 — 新增')).not.toBeInTheDocument()
  })

  it('shows an already-reviewed toast when approving a request someone else already reviewed', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedAuditApi.approve.mockRejectedValue(
      new ApiError(409, { error: 'Audit request has already been reviewed', id: 1, status: 'APPROVED' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('此申請已被其他人審核過')).toBeInTheDocument()
  })

  it('shows a network error toast when approving fails due to a network error', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedAuditApi.approve.mockRejectedValue(new NetworkError(new TypeError('fail')))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    expect(screen.getByText('審核異動申請 — 新增')).toBeInTheDocument()
  })

  it('shows an inline re-validation error and keeps the modal open when approve fails with a domain error', async () => {
    mockedAuditApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedAuditApi.approve.mockRejectedValue(
      new ApiError(409, { error: '此品牌已存在相同的幣種對' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await user.click(screen.getByText('查看'))
    await user.click(screen.getByRole('button', { name: '核准' }))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('此品牌已存在相同的幣種對')).toBeInTheDocument()
    expect(screen.getByText('審核異動申請 — 新增')).toBeInTheDocument()
  })
})
