import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuditPage } from './AuditPage'
import { ToastProvider } from '../components/ToastProvider'
import { auditApi } from './auditApi'
import { ApiError, NetworkError } from '../api/client'
import { registerDiffRenderer } from './diffRegistry'
import type { AuditRequest } from './types'

vi.mock('./auditApi', () => ({
  auditApi: {
    list: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  },
}))

const mockedApi = vi.mocked(auditApi)

const PENDING_REQUEST: AuditRequest = {
  id: 1,
  entityType: 'TEST_ENTITY_PAGE',
  actionType: 'UPDATE',
  entityId: 3,
  status: 'PENDING',
  summary: 'PUG · USD/TWD',
  before: { rate: 1 },
  after: { rate: 2 },
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

beforeEach(() => {
  vi.resetAllMocks()
})

describe('AuditPage', () => {
  it('loads requests from GET /api/audit-requests?status=PENDING by default', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])

    renderPage()

    expect(await screen.findByText('PUG · USD/TWD')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledWith({ status: 'PENDING', entityType: undefined })
  })

  it('shows a network-error toast when the initial load fails', async () => {
    mockedApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('refetches with the selected status filter', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])

    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await userEvent.selectOptions(screen.getByLabelText('狀態:'), '全部')

    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith({ status: undefined, entityType: undefined }),
    )
  })

  it('refetches with the selected entity-type filter', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])

    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await userEvent.selectOptions(screen.getByLabelText('類型:'), 'TEST_ENTITY_PAGE')

    await waitFor(() =>
      expect(mockedApi.list).toHaveBeenLastCalledWith({
        status: 'PENDING',
        entityType: 'TEST_ENTITY_PAGE',
      }),
    )
  })

  it('opens the review modal on 查看 and renders the generic fallback for an unregistered entityType', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])

    renderPage()
    await screen.findByText('PUG · USD/TWD')

    await userEvent.click(screen.getByText('查看'))

    expect(screen.getByText('審核異動申請 — 修改')).toBeInTheDocument()
    expect(screen.getByText('rate')).toBeInTheDocument()
  })

  it('renders a registered renderer instead of the generic fallback for its entityType', async () => {
    registerDiffRenderer('TEST_ENTITY_PAGE', () => <div data-testid="dedicated-renderer" />)
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))

    expect(screen.getByTestId('dedicated-renderer')).toBeInTheDocument()
  })

  it('approves a pending request, shows success, closes the modal, and refreshes', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.approve.mockResolvedValue({ ...PENDING_REQUEST, status: 'APPROVED' })

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))

    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(within(await screen.findByRole('status')).getByText('已核准')).toBeInTheDocument()
    expect(screen.queryByText('審核異動申請 — 修改')).not.toBeInTheDocument()
    expect(mockedApi.approve).toHaveBeenCalledWith(1)
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('rejects with a reason, shows success, closes the modal, and refreshes', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.reject.mockResolvedValue({ ...PENDING_REQUEST, status: 'REJECTED' })

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))

    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.type(screen.getByLabelText('拒絕原因'), '匯率過高')
    await userEvent.click(screen.getByText('確認拒絕'))

    expect(within(await screen.findByRole('status')).getByText('已拒絕')).toBeInTheDocument()
    expect(screen.queryByText('審核異動申請 — 修改')).not.toBeInTheDocument()
    expect(mockedApi.reject).toHaveBeenCalledWith(1, '匯率過高')
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('shows a not-found toast, closes the modal, and refreshes on a 404 approve', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.approve.mockRejectedValue(
      new ApiError(404, { error: 'Audit request not found', id: 1 }),
    )

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('審核申請不存在，請重新整理頁面')).toBeInTheDocument()
    expect(screen.queryByText('審核異動申請 — 修改')).not.toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('shows an already-reviewed toast, closes the modal, and refreshes on a 409 approve', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.approve.mockRejectedValue(
      new ApiError(409, { error: 'Audit request has already been reviewed', id: 1, status: 'APPROVED' }),
    )

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('此申請已被其他人審核過')).toBeInTheDocument()
    expect(screen.queryByText('審核異動申請 — 修改')).not.toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('shows the handler re-validation error inline and keeps the modal open on approve', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.approve.mockRejectedValue(
      new ApiError(400, { error: 'Currency pair is inactive' }),
    )

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('Currency pair is inactive')).toBeInTheDocument()
    expect(screen.getByText('審核異動申請 — 修改')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows a network-error toast on approve network failure', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.approve.mockRejectedValue(new NetworkError())

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('核准'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('shows a fixed missing-reason message inline on a 400 reject', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.reject.mockRejectedValue(new ApiError(400, { error: 'rejectReason is required' }))

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.type(screen.getByLabelText('拒絕原因'), '原因')
    await userEvent.click(screen.getByText('確認拒絕'))

    expect(await screen.findByText('請輸入拒絕原因')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows a network-error toast on reject network failure', async () => {
    mockedApi.list.mockResolvedValue([PENDING_REQUEST])
    mockedApi.reject.mockRejectedValue(new NetworkError())

    renderPage()
    await screen.findByText('PUG · USD/TWD')
    await userEvent.click(screen.getByText('查看'))
    await userEvent.click(screen.getByText('拒絕'))
    await userEvent.type(screen.getByLabelText('拒絕原因'), '原因')
    await userEvent.click(screen.getByText('確認拒絕'))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })
})
