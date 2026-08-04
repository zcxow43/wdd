import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrandPage } from './BrandPage'
import { ToastProvider } from '../components/ToastProvider'
import { brandApi } from '../api/brandApi'
import { ApiError, NetworkError } from '../api/client'
import type { Brand } from '../types/brand'

vi.mock('../api/brandApi', () => ({
  brandApi: {
    list: vi.fn(),
    updateActive: vi.fn(),
  },
}))

const mockedApi = vi.mocked(brandApi)

const AU: Brand = {
  id: 1,
  code: 'AU',
  name: 'AU',
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const PUG: Brand = {
  id: 2,
  code: 'PUG',
  name: 'PUG',
  active: false,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

function renderPage() {
  return render(
    <ToastProvider>
      <BrandPage />
    </ToastProvider>,
  )
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('BrandPage', () => {
  it('loads brands from the API on mount', async () => {
    mockedApi.list.mockResolvedValue([AU, PUG])

    renderPage()

    expect(await screen.findByLabelText('AU 狀態')).toBeInTheDocument()
    expect(screen.getByLabelText('PUG 狀態')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows an empty state when there are no brands', async () => {
    mockedApi.list.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('目前沒有品牌資料')).toBeInTheDocument()
  })

  it('shows a network-error toast when the initial load fails', async () => {
    mockedApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('toggles a brand and updates the row on success', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockResolvedValue({ ...AU, active: false })

    renderPage()
    await screen.findByLabelText('AU 狀態')
    expect(screen.getByText('啟用')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('AU 狀態'))

    await waitFor(() => expect(mockedApi.updateActive).toHaveBeenCalledWith(1, false))
    expect(await screen.findByText('停用')).toBeInTheDocument()
  })

  it('reverts the toggle and shows a toast + refreshes on a 404', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockRejectedValue(
      new ApiError(404, { error: 'Brand not found', id: 1 }),
    )

    renderPage()
    await screen.findByLabelText('AU 狀態')

    await userEvent.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('品牌不存在，請重新整理頁面')).toBeInTheDocument()
    expect(screen.getByText('啟用')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(2)
  })

  it('reverts the toggle and shows a network-error toast on network failure', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockRejectedValue(new NetworkError())

    renderPage()
    await screen.findByLabelText('AU 狀態')

    await userEvent.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    expect(screen.getByText('啟用')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows the generic-failure toast on a 400 without reverting-and-refreshing extra list calls', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockRejectedValue(
      new ApiError(400, { error: 'Validation failed', fields: { active: 'active is required' } }),
    )

    renderPage()
    await screen.findByLabelText('AU 狀態')

    await userEvent.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('更新失敗，請稍後再試')).toBeInTheDocument()
    expect(screen.getByText('啟用')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })
})
