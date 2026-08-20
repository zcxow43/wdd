import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BrandManagementPage from './BrandManagementPage'
import type { Brand } from '../api/brands'
import { fetchBrands, updateBrandActive } from '../api/brands'

vi.mock('../api/brands', () => ({
  fetchBrands: vi.fn(),
  updateBrandActive: vi.fn(),
}))

const mockedFetchBrands = vi.mocked(fetchBrands)
const mockedUpdateBrandActive = vi.mocked(updateBrandActive)

const BRAND_CODES = ['au', 'moneta', 'pug', 'star', 'um', 'vjp', 'vt']

function makeBrands(): Brand[] {
  return BRAND_CODES.map((code, index) => ({
    id: index + 1,
    code,
    name: code,
    active: true,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }))
}

function getDataRows() {
  // First row is the header row.
  return screen.getAllByRole('row').slice(1)
}

function findRowByCode(code: string): HTMLElement {
  const row = getDataRows().find(
    (r) => within(r).getAllByRole('cell')[0].textContent === code,
  )
  if (!row) {
    throw new Error(`row for code ${code} not found`)
  }
  return row
}

describe('BrandManagementPage', () => {
  beforeEach(() => {
    mockedFetchBrands.mockReset()
    mockedUpdateBrandActive.mockReset()
  })

  it('loads and displays all 7 brands from GET /api/brands', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())

    render(<BrandManagementPage />)

    expect(await screen.findByText('品牌管理')).toBeInTheDocument()
    await screen.findByRole('table')

    const rows = getDataRows()
    expect(rows).toHaveLength(7)

    for (const code of BRAND_CODES) {
      const row = findRowByCode(code)
      const cells = within(row).getAllByRole('cell')
      expect(cells[0].textContent).toBe(code)
      expect(cells[1].textContent).toBe(code)
      expect(within(row).getByText('啟用')).toBeInTheDocument()
    }

    expect(screen.getAllByRole('switch')).toHaveLength(7)
  })

  it('shows an inline error with a retry button when the list fails to load', async () => {
    mockedFetchBrands.mockRejectedValueOnce(new Error('network error'))

    render(<BrandManagementPage />)

    expect(await screen.findByText(/載入品牌清單失敗/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    mockedFetchBrands.mockResolvedValueOnce(makeBrands())
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('toggles a brand: calls PUT with the new active value, shows 更新中... while pending, and reflects the result', async () => {
    const brands = makeBrands()
    mockedFetchBrands.mockResolvedValue(brands)

    let resolvePut!: (brand: Brand) => void
    const putPromise = new Promise<Brand>((resolve) => {
      resolvePut = resolve
    })
    mockedUpdateBrandActive.mockReturnValue(putPromise)

    render(<BrandManagementPage />)
    await screen.findByRole('table')

    const starRow = findRowByCode('star')
    const starToggle = within(starRow).getByRole('switch')

    await userEvent.click(starToggle)

    expect(mockedUpdateBrandActive).toHaveBeenCalledWith(4, false)
    expect(within(starRow).getByText('更新中...')).toBeInTheDocument()
    expect(starToggle).toBeDisabled()

    resolvePut({
      ...brands[3],
      active: false,
      updatedAt: '2026-01-02T00:00:00',
    })

    await waitFor(() => {
      expect(within(starRow).getByText('停用')).toBeInTheDocument()
    })
    expect(starToggle).not.toBeDisabled()
  })

  it('reverts the toggle and shows an error toast when the update fails', async () => {
    const brands = makeBrands()
    mockedFetchBrands.mockResolvedValue(brands)
    mockedUpdateBrandActive.mockRejectedValue(new Error('failed'))

    render(<BrandManagementPage />)
    await screen.findByRole('table')

    const starRow = findRowByCode('star')
    const starToggle = within(starRow).getByRole('switch')

    await userEvent.click(starToggle)

    await screen.findByText('更新品牌狀態失敗，請稍後再試')
    expect(within(starRow).getByText('啟用')).toBeInTheDocument()
    expect(starToggle).not.toBeDisabled()
  })

  it('has no create/delete controls', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())

    render(<BrandManagementPage />)

    await screen.findByRole('table')

    expect(screen.queryByText('新增')).not.toBeInTheDocument()
    expect(screen.queryByText('刪除')).not.toBeInTheDocument()
  })
})
