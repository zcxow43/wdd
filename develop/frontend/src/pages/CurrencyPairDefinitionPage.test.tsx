import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairDefinitionPage } from './CurrencyPairDefinitionPage'
import { ToastProvider } from '../components/ToastProvider'
import { currencyPairDefinitionApi } from '../api/currencyPairDefinitionApi'
import { currencyApi } from '../api/currencyApi'
import { ApiError, NetworkError } from '../api/client'
import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'

vi.mock('../api/currencyPairDefinitionApi', () => ({
  currencyPairDefinitionApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
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

const mockedDefinitionApi = vi.mocked(currencyPairDefinitionApi)
const mockedCurrencyApi = vi.mocked(currencyApi)

const TWD: Currency = {
  id: 1,
  code: 'TWD',
  name: 'New Taiwan Dollar',
  nameZh: '新台幣',
  symbol: 'NT$',
  decimalPlaces: 0,
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}
const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'United States Dollar',
  nameZh: '美元',
  symbol: '$',
  decimalPlaces: 2,
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const DEFINITION: CurrencyPairDefinition = {
  id: 1,
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 1,
  quoteCurrencyCode: 'TWD',
  forwardPrecision: 2,
  reversePrecision: 5,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

function renderPage() {
  return render(
    <ToastProvider>
      <CurrencyPairDefinitionPage />
    </ToastProvider>,
  )
}

describe('CurrencyPairDefinitionPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  function stubAncillary() {
    mockedCurrencyApi.list.mockResolvedValue([TWD, USD])
  }

  it('loads and renders definitions on mount', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([DEFINITION])
    renderPage()

    expect(await screen.findByText('USD')).toBeInTheDocument()
    expect(screen.getByText('TWD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalled()
  })

  it('shows the empty state when there are no definitions', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([])
    renderPage()

    expect(await screen.findByText('目前尚無幣種對主檔')).toBeInTheDocument()
  })

  it('shows a network error toast and retry button when the initial load fails', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockRejectedValue(new NetworkError(new TypeError('fail')))
    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    expect(screen.getByText('資料載入失敗')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重試' })).toBeInTheDocument()
  })

  it('creates a definition through the add modal, shows the confirmation toast, and refreshes the table', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValueOnce([]).mockResolvedValueOnce([DEFINITION])
    mockedDefinitionApi.create.mockResolvedValue(DEFINITION)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前尚無幣種對主檔')

    await user.click(screen.getByRole('button', { name: '+新增幣種對' }))
    await user.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '1')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(mockedDefinitionApi.create).toHaveBeenCalledWith({
        baseCurrencyId: 2,
        quoteCurrencyId: 1,
        forwardPrecision: 2,
        reversePrecision: 5,
      }),
    )
    expect(await screen.findByText('已建立幣種對，所有品牌已自動套用')).toBeInTheDocument()
    expect(await screen.findByText('USD')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '儲存' })).not.toBeInTheDocument()
  })

  it('shows the duplicate-direction inline error and keeps the modal open on a 409', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([])
    mockedDefinitionApi.create.mockRejectedValue(
      new ApiError(409, { error: 'Reverse direction already exists' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('目前尚無幣種對主檔')

    await user.click(screen.getByRole('button', { name: '+新增幣種對' }))
    await user.selectOptions(screen.getByLabelText('基準幣別'), '1')
    await user.selectOptions(screen.getByLabelText('對應幣別'), '2')
    await user.type(screen.getByLabelText('正向精度'), '2')
    await user.type(screen.getByLabelText('反向精度'), '5')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    expect(await screen.findByText('此幣種對（或其反向）已存在')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '儲存' })).toBeInTheDocument()
  })

  it('edits a definition with only precision editable and shows the update toast', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([DEFINITION])
    mockedDefinitionApi.update.mockResolvedValue({ ...DEFINITION, forwardPrecision: 3 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('USD')

    const row = screen.getByText('USD').closest('tr')!
    await user.click(within(row).getByText('編輯'))

    expect(screen.getByLabelText('基準幣別')).toBeDisabled()
    expect(screen.getByLabelText('對應幣別')).toBeDisabled()

    const forwardInput = screen.getByLabelText('正向精度')
    await user.clear(forwardInput)
    await user.type(forwardInput, '3')
    await user.click(screen.getByRole('button', { name: '儲存' }))

    await waitFor(() =>
      expect(mockedDefinitionApi.update).toHaveBeenCalledWith(1, { forwardPrecision: 3, reversePrecision: 5 }),
    )
    expect(await screen.findByText('已更新精度設定')).toBeInTheDocument()
  })

  it('deletes a definition after confirmation and shows the delete toast', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValueOnce([DEFINITION]).mockResolvedValueOnce([])
    mockedDefinitionApi.remove.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('USD')

    const row = screen.getByText('USD').closest('tr')!
    await user.click(within(row).getByText('刪除'))

    expect(
      await screen.findByText(
        '確定要刪除幣種對主檔「USD/TWD」嗎？已套用至各品牌的幣種對不會被移除，但刪除後可重新建立其反向幣種對。若仍有品牌啟用此幣種對，將無法刪除。',
      ),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '確定' }))

    await waitFor(() => expect(mockedDefinitionApi.remove).toHaveBeenCalledWith(1))
    expect(await screen.findByText('已刪除幣種對主檔')).toBeInTheDocument()
  })

  it('shows a network error toast when delete fails', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([DEFINITION])
    mockedDefinitionApi.remove.mockRejectedValue(new ApiError(404, { error: 'Not found' }, 'Not Found'))
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('USD')

    const row = screen.getByText('USD').closest('tr')!
    await user.click(within(row).getByText('刪除'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
  })

  it('shows a toast naming the blocking brands on a 409 and keeps the row in the table', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([DEFINITION])
    mockedDefinitionApi.remove.mockRejectedValue(
      new ApiError(
        409,
        {
          error: 'Active in brands',
          baseCurrencyId: 2,
          quoteCurrencyId: 1,
          activeBrandCodes: ['BR1', 'BR2'],
        },
        'Conflict',
      ),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('USD')

    const row = screen.getByText('USD').closest('tr')!
    await user.click(within(row).getByText('刪除'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('以下品牌仍啟用此幣種對，請先停用：BR1, BR2')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows a generic fallback toast on a 409 missing activeBrandCodes', async () => {
    stubAncillary()
    mockedDefinitionApi.list.mockResolvedValue([DEFINITION])
    mockedDefinitionApi.remove.mockRejectedValue(
      new ApiError(409, { error: 'Active in brands' }, 'Conflict'),
    )
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('USD')

    const row = screen.getByText('USD').closest('tr')!
    await user.click(within(row).getByText('刪除'))
    await user.click(screen.getByRole('button', { name: '確定' }))

    expect(await screen.findByText('尚有品牌啟用此幣種對，請先停用')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(1)
  })
})
