import { afterEach, describe, expect, it, vi } from 'vitest'
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

describe('BrandPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('loads and renders brands on mount', async () => {
    mockedApi.list.mockResolvedValue([AU, PUG])
    renderPage()

    expect(await screen.findByLabelText('AU 狀態')).toBeInTheDocument()
    expect(screen.getByLabelText('PUG 狀態')).toBeInTheDocument()
    expect(mockedApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows the empty state when there are no brands', async () => {
    mockedApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前沒有品牌資料')).toBeInTheDocument()
  })

  it('shows a network error toast when the initial load fails', async () => {
    mockedApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('toggles a brand active state and updates the row on success', async () => {
    mockedApi.list.mockResolvedValue([PUG])
    mockedApi.updateActive.mockResolvedValue({ ...PUG, active: true })
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('PUG 狀態')

    await user.click(screen.getByLabelText('PUG 狀態'))

    await waitFor(() => expect(mockedApi.updateActive).toHaveBeenCalledWith(2, true))
    expect(await screen.findByLabelText('PUG 狀態')).toBeChecked()
  })

  it('reverts the toggle and shows a not-found toast on 404', async () => {
    mockedApi.list.mockResolvedValueOnce([AU]).mockResolvedValueOnce([AU])
    mockedApi.updateActive.mockRejectedValue(new ApiError(404, { error: 'Brand not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('AU 狀態')

    await user.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('品牌不存在，請重新整理頁面')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('AU 狀態')).toBeChecked())
  })

  it('reverts the toggle and shows a network error toast on network failure', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockRejectedValue(new NetworkError(new TypeError('fail')))
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('AU 狀態')

    await user.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('AU 狀態')).toBeChecked())
  })

  it('shows an update-failed toast on 400', async () => {
    mockedApi.list.mockResolvedValue([AU])
    mockedApi.updateActive.mockRejectedValue(new ApiError(400, { error: 'active is invalid' }, 'Bad Request'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByLabelText('AU 狀態')

    await user.click(screen.getByLabelText('AU 狀態'))

    expect(await screen.findByText('更新失敗，請稍後再試')).toBeInTheDocument()
  })
})
