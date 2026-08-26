import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BrandCurrencyPairPage from './BrandCurrencyPairPage'
import type { AuditRequestSummary } from '../api/audit'
import { fetchAuditRequests } from '../api/audit'
import type { Brand } from '../api/brands'
import { fetchBrands } from '../api/brands'
import type {
  CurrencyPair,
  CurrencyPairAuditSubmission,
} from '../api/currencyPairDefinitions'
import {
  deleteCurrencyPair,
  fetchCurrencyPairsByBrand,
  updateCurrencyPair,
} from '../api/currencyPairDefinitions'
import { ApiError } from '../api/http'

vi.mock('../api/brands', () => ({
  fetchBrands: vi.fn(),
}))

vi.mock('../api/audit', () => ({
  fetchAuditRequests: vi.fn(),
}))

vi.mock('../api/currencyPairDefinitions', () => ({
  fetchCurrencyPairsByBrand: vi.fn(),
  updateCurrencyPair: vi.fn(),
  deleteCurrencyPair: vi.fn(),
}))

const mockedFetchBrands = vi.mocked(fetchBrands)
const mockedFetchAuditRequests = vi.mocked(fetchAuditRequests)
const mockedFetchPairsByBrand = vi.mocked(fetchCurrencyPairsByBrand)
const mockedUpdateCurrencyPair = vi.mocked(updateCurrencyPair)
const mockedDeleteCurrencyPair = vi.mocked(deleteCurrencyPair)

function makeAuditSubmission(
  entityId: number,
  actionType: 'UPDATE' | 'DELETE' = 'UPDATE',
): CurrencyPairAuditSubmission {
  return {
    auditRequestId: 9001,
    status: 'PENDING',
    entityType: 'CURRENCY_PAIR',
    actionType,
    entityId,
    summary: 'summary',
  }
}

function makeAuditRequestSummary(entityId: number): AuditRequestSummary {
  return {
    id: 9001,
    entityType: 'CURRENCY_PAIR',
    actionType: 'UPDATE',
    entityId,
    brandId: 1,
    summary: 'summary',
    status: 'PENDING',
    requestedBy: 'system',
    requestedAt: '2026-08-23T00:00:00',
    reviewedBy: null,
    reviewedAt: null,
    reviewComment: null,
    applyError: null,
  }
}

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

function makePairs(): CurrencyPair[] {
  return [
    {
      id: 101,
      currencyPairDefinitionId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'JPY',
      brandId: 1,
      brandCode: 'au',
      rateType: 'AUTO',
      rate: null,
      active: true,
      depositRate: null,
      withdrawalRate: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 102,
      currencyPairDefinitionId: 2,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'EUR',
      brandId: 1,
      brandCode: 'au',
      rateType: 'MANUAL',
      rate: 1.2345,
      active: false,
      depositRate: 1.2445,
      withdrawalRate: 1.2545,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function getDataRows() {
  return screen.getAllByRole('row').slice(1)
}

function findRowByCodes(base: string, quote: string): HTMLElement {
  const row = getDataRows().find(
    (r) => within(r).getAllByRole('cell')[0].textContent === `${base}/${quote}`,
  )
  if (!row) {
    throw new Error(`row for ${base}/${quote} not found`)
  }
  return row
}

describe('BrandCurrencyPairPage', () => {
  beforeEach(() => {
    mockedFetchBrands.mockReset()
    mockedFetchAuditRequests.mockReset()
    mockedFetchAuditRequests.mockResolvedValue([])
    mockedFetchPairsByBrand.mockReset()
    mockedUpdateCurrencyPair.mockReset()
    mockedDeleteCurrencyPair.mockReset()
  })

  it('loads brands, selects the first by default, and loads its currency pairs', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    render(<BrandCurrencyPairPage />)

    expect(await screen.findByText('品牌幣種對')).toBeInTheDocument()
    const tabs = await screen.findAllByRole('tab')
    expect(tabs).toHaveLength(7)
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true')

    expect(mockedFetchPairsByBrand).toHaveBeenCalledWith(1)

    await screen.findByRole('table')
    const rows = getDataRows()
    expect(rows).toHaveLength(2)

    await waitFor(() => {
      expect(mockedFetchAuditRequests).toHaveBeenCalledWith({
        status: 'PENDING',
        entityType: 'CURRENCY_PAIR',
        brandId: 1,
      })
    })

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    expect(
      within(usdJpyRow).getByRole('radio', { name: '自動' }),
    ).toBeChecked()
    expect(within(usdJpyRow).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'true',
    )

    const usdEurRow = findRowByCodes('USD', 'EUR')
    expect(
      within(usdEurRow).getByRole('radio', { name: '手動' }),
    ).toBeChecked()
    expect(
      (within(usdEurRow).getByLabelText('USD/EUR 匯率') as HTMLInputElement)
        .value,
    ).toBe('1.2345')
  })

  it('shows 入金加點完成/出金加點完成 as "-" when null and formatted when present, as pure display cells', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    // AUTO pair whose definition has never been synced: both null -> "-".
    const usdJpyRow = findRowByCodes('USD', 'JPY')
    const usdJpyCells = within(usdJpyRow).getAllByRole('cell')
    expect(usdJpyCells[3].textContent).toBe('-')
    expect(usdJpyCells[4].textContent).toBe('-')
    expect(usdJpyCells[3].querySelector('input, button')).toBeNull()
    expect(usdJpyCells[4].querySelector('input, button')).toBeNull()

    // MANUAL pair with computed values -> formatted values, not "-".
    const usdEurRow = findRowByCodes('USD', 'EUR')
    const usdEurCells = within(usdEurRow).getAllByRole('cell')
    expect(usdEurCells[3].textContent).toBe('1.2445')
    expect(usdEurCells[4].textContent).toBe('1.2545')
  })

  it('does not preview the local 匯率類型/匯率 draft in 入金加點完成/出金加點完成 before 儲存', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdEurRow = findRowByCodes('USD', 'EUR')
    const rateInput = within(usdEurRow).getByLabelText(
      'USD/EUR 匯率',
    ) as HTMLInputElement

    await userEvent.clear(rateInput)
    await userEvent.type(rateInput, '9.9999')

    const cellsAfterEdit = within(usdEurRow).getAllByRole('cell')
    // Still the last-loaded committed values, unaffected by the unsaved draft.
    expect(cellsAfterEdit[3].textContent).toBe('1.2445')
    expect(cellsAfterEdit[4].textContent).toBe('1.2545')

    await userEvent.click(within(usdEurRow).getByRole('radio', { name: '自動' }))

    const cellsAfterTypeSwitch = within(
      findRowByCodes('USD', 'EUR'),
    ).getAllByRole('cell')
    expect(cellsAfterTypeSwitch[3].textContent).toBe('1.2445')
    expect(cellsAfterTypeSwitch[4].textContent).toBe('1.2545')
  })

  it('switching brands loads that brand pairs', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    await userEvent.click(screen.getByRole('tab', { name: 'moneta' }))

    await waitFor(() => {
      expect(mockedFetchPairsByBrand).toHaveBeenCalledWith(2)
    })
  })

  it('shows an inline error with a retry button when the brand list fails to load', async () => {
    mockedFetchBrands.mockRejectedValueOnce(new Error('network error'))

    render(<BrandCurrencyPairPage />)

    expect(await screen.findByText(/載入品牌清單失敗/)).toBeInTheDocument()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()

    mockedFetchBrands.mockResolvedValueOnce(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    expect(await screen.findByRole('tablist')).toBeInTheDocument()
  })

  it('shows an inline error scoped to the table area when the pair list fails to load', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockRejectedValueOnce(new Error('network error'))

    render(<BrandCurrencyPairPage />)

    expect(await screen.findByText(/載入幣種對清單失敗/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    mockedFetchPairsByBrand.mockResolvedValueOnce(makePairs())
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('shows the empty-state message when the selected brand has no currency pairs', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue([])

    render(<BrandCurrencyPairPage />)

    expect(
      await screen.findByText(
        '此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義',
      ),
    ).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('requires a rate value when switching to 手動, and clears it when switching back to 自動', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    const rateInput = within(usdJpyRow).getByLabelText(
      'USD/JPY 匯率',
    ) as HTMLInputElement
    expect(rateInput).toBeDisabled()

    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '手動' }))
    expect(rateInput).not.toBeDisabled()

    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '儲存' }))
    expect(mockedUpdateCurrencyPair).not.toHaveBeenCalled()
    expect(
      within(usdJpyRow).getByText('請輸入有效匯率'),
    ).toBeInTheDocument()

    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '自動' }))
    expect(rateInput).toBeDisabled()
    expect(rateInput.value).toBe('')
  })

  it('saves rateType/rate via PUT: on 202 leaves displayed values unchanged, shows the 審核中 badge and submission toast', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    let resolvePut!: (result: CurrencyPairAuditSubmission) => void
    const putPromise = new Promise<CurrencyPairAuditSubmission>((resolve) => {
      resolvePut = resolve
    })
    mockedUpdateCurrencyPair.mockReturnValue(putPromise)

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '手動' }))
    const rateInput = within(usdJpyRow).getByLabelText('USD/JPY 匯率')
    await userEvent.type(rateInput, '150.25')

    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '儲存' }))

    expect(mockedUpdateCurrencyPair).toHaveBeenCalledWith(101, {
      rateType: 'MANUAL',
      rate: 150.25,
    })
    expect(within(usdJpyRow).getByRole('button', { name: '儲存' })).toBeDisabled()

    resolvePut(makeAuditSubmission(101))

    await screen.findByText('已送出審核，核准後才會生效')

    const updatedRow = findRowByCodes('USD', 'JPY')
    // Displayed values revert to the last committed (still-AUTO) state —
    // the submitted change has not taken effect.
    expect(
      within(updatedRow).getByRole('radio', { name: '自動' }),
    ).toBeChecked()
    expect(within(updatedRow).getByText('審核中')).toBeInTheDocument()
    expect(
      within(updatedRow).getByRole('button', { name: '儲存' }),
    ).toBeDisabled()
    expect(
      within(updatedRow).getByRole('button', { name: '刪除' }),
    ).toBeDisabled()
  })

  it('shows the already-pending toast and refreshes the marker on a 409 save conflict', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedUpdateCurrencyPair.mockRejectedValue(new ApiError(409, 'conflict'))
    mockedFetchAuditRequests.mockResolvedValue([])

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    mockedFetchAuditRequests.mockResolvedValue([makeAuditRequestSummary(101)])

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '手動' }))
    const rateInput = within(usdJpyRow).getByLabelText('USD/JPY 匯率')
    await userEvent.type(rateInput, '150.25')
    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '儲存' }))

    await screen.findByText('此列已有待審核的變更')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'JPY')).getByText('審核中'),
      ).toBeInTheDocument()
    })
  })

  it('shows inline error on 400 without changing committed state', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedUpdateCurrencyPair.mockRejectedValue(new ApiError(400, 'bad request'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '手動' }))
    const rateInput = within(usdJpyRow).getByLabelText('USD/JPY 匯率')
    await userEvent.type(rateInput, '999999')

    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '儲存' }))

    await screen.findByText('請輸入有效匯率')
  })

  it('reverts row fields and shows an error toast on other save failures', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedUpdateCurrencyPair.mockRejectedValue(new Error('network error'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(usdJpyRow).getByRole('radio', { name: '手動' }))
    const rateInput = within(usdJpyRow).getByLabelText(
      'USD/JPY 匯率',
    ) as HTMLInputElement
    await userEvent.type(rateInput, '150')

    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '儲存' }))

    await screen.findByText('更新失敗，請稍後再試')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'JPY')).getByRole('radio', {
          name: '自動',
        }),
      ).toBeChecked()
    })
  })

  it('toggles 狀態: calls PUT immediately with a disabled 送審中... switch, and does not stay flipped on success', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())

    let resolvePut!: (result: CurrencyPairAuditSubmission) => void
    const putPromise = new Promise<CurrencyPairAuditSubmission>((resolve) => {
      resolvePut = resolve
    })
    mockedUpdateCurrencyPair.mockReturnValue(putPromise)

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    const toggle = within(usdJpyRow).getByRole('switch')

    await userEvent.click(toggle)

    expect(mockedUpdateCurrencyPair).toHaveBeenCalledWith(101, {
      active: false,
    })
    // Not flipped optimistically — still shows the currently-effective 啟用.
    expect(within(usdJpyRow).getByText('送審中...')).toBeInTheDocument()
    expect(toggle).toBeDisabled()

    resolvePut(makeAuditSubmission(101))

    await screen.findByText('已送出審核，核准後才會生效')

    const updatedRow = findRowByCodes('USD', 'JPY')
    // Stays at its currently-effective position (still 啟用/checked) —
    // the change has not been applied, only submitted for review.
    expect(within(updatedRow).getByText('啟用')).toBeInTheDocument()
    expect(within(updatedRow).getByRole('switch')).toHaveAttribute(
      'aria-checked',
      'true',
    )
    expect(within(updatedRow).getByText('審核中')).toBeInTheDocument()
  })

  it('toggle failure shows an error toast without ever having flipped the switch', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedUpdateCurrencyPair.mockRejectedValue(new Error('failed'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    const toggle = within(usdJpyRow).getByRole('switch')

    await userEvent.click(toggle)

    expect(mockedUpdateCurrencyPair).toHaveBeenCalledWith(101, {
      active: false,
    })

    await screen.findByText('更新失敗，請稍後再試')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'JPY')).getByText('啟用'),
      ).toBeInTheDocument()
    })
  })

  it('shows the already-pending toast and refreshes the marker on a 409 toggle conflict', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedUpdateCurrencyPair.mockRejectedValue(new ApiError(409, 'conflict'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    mockedFetchAuditRequests.mockResolvedValue([makeAuditRequestSummary(101)])

    const usdJpyRow = findRowByCodes('USD', 'JPY')
    await userEvent.click(within(usdJpyRow).getByRole('switch'))

    await screen.findByText('此列已有待審核的變更')
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'JPY')).getByText('審核中'),
      ).toBeInTheDocument()
    })
  })

  it('刪除 submits a deletion request: the row stays with a 審核中 badge instead of being removed', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedDeleteCurrencyPair.mockResolvedValue(makeAuditSubmission(102, 'DELETE'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdEurRow = findRowByCodes('USD', 'EUR')
    expect(within(usdEurRow).getByText('停用')).toBeInTheDocument()
    await userEvent.click(within(usdEurRow).getByRole('button', { name: '刪除' }))

    await screen.findByText(
      '確定要送出刪除「USD/EUR」的申請嗎？核准後才會真正刪除。',
    )
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    expect(mockedDeleteCurrencyPair).toHaveBeenCalledWith(102)
    await screen.findByText('已送出審核，核准後才會生效')
    expect(getDataRows()).toHaveLength(2)
    expect(
      within(findRowByCodes('USD', 'EUR')).getByText('審核中'),
    ).toBeInTheDocument()
  })

  it('shows the already-pending toast and refreshes the marker on a 409 delete conflict', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedDeleteCurrencyPair.mockRejectedValue(new ApiError(409, 'conflict'))

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    mockedFetchAuditRequests.mockResolvedValue([makeAuditRequestSummary(102)])

    const usdEurRow = findRowByCodes('USD', 'EUR')
    await userEvent.click(within(usdEurRow).getByRole('button', { name: '刪除' }))
    await userEvent.click(
      screen.getAllByRole('button', { name: '刪除' }).at(-1)!,
    )

    await screen.findByText('此列已有待審核的變更')
    expect(getDataRows()).toHaveLength(2)
    await waitFor(() => {
      expect(
        within(findRowByCodes('USD', 'EUR')).getByText('審核中'),
      ).toBeInTheDocument()
    })
  })

  it('loads rows with a pending request already marked: controls and 刪除 disabled with the explanatory tooltip', async () => {
    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchPairsByBrand.mockResolvedValue(makePairs())
    mockedFetchAuditRequests.mockResolvedValue([makeAuditRequestSummary(102)])

    render(<BrandCurrencyPairPage />)
    await screen.findByRole('table')

    const usdEurRow = await waitFor(() => {
      const row = findRowByCodes('USD', 'EUR')
      expect(within(row).getByText('審核中')).toBeInTheDocument()
      return row
    })

    expect(within(usdEurRow).getByText('審核中')).toHaveAttribute(
      'title',
      '此列有待審核的變更，需先完成審核',
    )
    expect(
      within(usdEurRow).getByRole('radio', { name: '自動' }),
    ).toBeDisabled()
    expect(
      within(usdEurRow).getByRole('radio', { name: '手動' }),
    ).toBeDisabled()
    expect(within(usdEurRow).getByRole('switch')).toBeDisabled()
    expect(
      within(usdEurRow).getByRole('button', { name: '儲存' }),
    ).toBeDisabled()
    expect(
      within(usdEurRow).getByRole('button', { name: '刪除' }),
    ).toBeDisabled()

    // The other row, with no pending request, stays fully interactive.
    const usdJpyRow = findRowByCodes('USD', 'JPY')
    expect(within(usdJpyRow).queryByText('審核中')).not.toBeInTheDocument()
    expect(
      within(usdJpyRow).getByRole('button', { name: '刪除' }),
    ).not.toBeDisabled()
  })
})
