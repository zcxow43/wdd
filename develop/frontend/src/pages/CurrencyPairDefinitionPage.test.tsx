import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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

const USD: Currency = {
  id: 2,
  code: 'USD',
  name: 'US Dollar',
  nameZh: null,
  symbol: null,
  decimalPlaces: 2,
  createdAt: '',
  updatedAt: '',
}
const JPY: Currency = {
  id: 3,
  code: 'JPY',
  name: 'Japanese Yen',
  nameZh: null,
  symbol: null,
  decimalPlaces: 0,
  createdAt: '',
  updatedAt: '',
}

const USD_JPY: CurrencyPairDefinition = {
  id: 1,
  baseCurrencyId: 2,
  baseCurrencyCode: 'USD',
  quoteCurrencyId: 3,
  quoteCurrencyCode: 'JPY',
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

beforeEach(() => {
  vi.resetAllMocks()
  mockedCurrencyApi.list.mockResolvedValue([USD, JPY])
})

describe('CurrencyPairDefinitionPage', () => {
  it('loads definitions from the API on mount and renders the table', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])

    renderPage()

    expect(await screen.findByText('USD')).toBeInTheDocument()
    expect(screen.getByText('JPY')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledWith()
  })

  it('shows an empty state when there are no definitions', async () => {
    mockedDefinitionApi.list.mockResolvedValue([])

    renderPage()

    expect(await screen.findByText('目前沒有幣種對主檔資料')).toBeInTheDocument()
  })

  it('shows a network-error toast and the error/retry state when the initial load fails', async () => {
    mockedDefinitionApi.list.mockRejectedValue(new NetworkError())

    renderPage()

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    expect(screen.getByText('資料載入失敗')).toBeInTheDocument()
  })

  it('retries loading when the 重試 button is clicked', async () => {
    mockedDefinitionApi.list.mockRejectedValueOnce(new NetworkError()).mockResolvedValueOnce([USD_JPY])

    renderPage()
    await screen.findByText('資料載入失敗')

    await userEvent.click(screen.getByText('重試'))

    expect(await screen.findByText('USD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(2)
  })

  it('creates a definition through the add modal, shows the fan-out confirmation toast, and refreshes the table', async () => {
    mockedDefinitionApi.list.mockResolvedValueOnce([]).mockResolvedValueOnce([USD_JPY])
    mockedDefinitionApi.create.mockResolvedValue(USD_JPY)

    renderPage()
    await screen.findByText('目前沒有幣種對主檔資料')

    await userEvent.click(screen.getByText('+新增幣種對'))
    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '2')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '3')
    await userEvent.type(screen.getByLabelText('正向精度'), '2')
    await userEvent.type(screen.getByLabelText('反向精度'), '5')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(mockedDefinitionApi.create).toHaveBeenCalledWith({
        baseCurrencyId: 2,
        quoteCurrencyId: 3,
        forwardPrecision: 2,
        reversePrecision: 5,
      }),
    )
    expect(await screen.findByText('已建立幣種對，所有品牌已自動套用')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
  })

  it('shows an inline error and does not close the modal when creating the reverse of an existing definition (409)', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])
    mockedDefinitionApi.create.mockRejectedValue(
      new ApiError(409, { error: 'Reverse direction already exists' }),
    )

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('+新增幣種對'))
    await userEvent.selectOptions(screen.getByLabelText('基準幣別'), '3')
    await userEvent.selectOptions(screen.getByLabelText('對應幣別'), '2')
    await userEvent.type(screen.getByLabelText('正向精度'), '3')
    await userEvent.type(screen.getByLabelText('反向精度'), '3')
    await userEvent.click(screen.getByText('儲存'))

    expect(await screen.findByText('此幣種對（或其反向）已存在')).toBeInTheDocument()
    expect(screen.getByText('新增幣種對主檔')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(1)
  })

  it('edits a definition through the edit modal with base/quote disabled, and shows the update toast', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])
    mockedDefinitionApi.update.mockResolvedValue({ ...USD_JPY, forwardPrecision: 4 })

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('編輯'))
    expect(screen.getByLabelText('基準幣別')).toBeDisabled()
    expect(screen.getByLabelText('對應幣別')).toBeDisabled()

    await userEvent.clear(screen.getByLabelText('正向精度'))
    await userEvent.type(screen.getByLabelText('正向精度'), '4')
    await userEvent.click(screen.getByText('儲存'))

    await waitFor(() =>
      expect(mockedDefinitionApi.update).toHaveBeenCalledWith(1, {
        forwardPrecision: 4,
        reversePrecision: 5,
      }),
    )
    expect(await screen.findByText('已更新精度設定')).toBeInTheDocument()
  })

  it('shows a confirmation dialog with the delete-guard copy and deletes on confirm', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])
    mockedDefinitionApi.remove.mockResolvedValue(undefined)

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('刪除'))
    expect(
      screen.getByText(
        '確定要刪除幣種對主檔「USD/JPY」嗎？已套用至各品牌的幣種對不會被移除，但刪除後可重新建立其反向幣種對。若仍有品牌啟用此幣種對，將無法刪除。',
      ),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(mockedDefinitionApi.remove).toHaveBeenCalledWith(1))
    expect(await screen.findByText('已刪除幣種對主檔')).toBeInTheDocument()
  })

  it('shows a toast naming the still-active brands on a 409, keeps the row, and does not refetch', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])
    mockedDefinitionApi.remove.mockRejectedValue(
      new ApiError(409, { error: 'One or more brands still have this pair active', activeBrandCodes: ['BR1', 'BR2'] }),
    )

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(
      await screen.findByText('以下品牌仍啟用此幣種對，請先停用：BR1, BR2'),
    ).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(1)
  })

  it('shows the generic fallback toast on a 409 missing activeBrandCodes, keeps the row, and does not refetch', async () => {
    mockedDefinitionApi.list.mockResolvedValue([USD_JPY])
    mockedDefinitionApi.remove.mockRejectedValue(
      new ApiError(409, { error: 'One or more brands still have this pair active' }),
    )

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('尚有品牌啟用此幣種對，請先停用')).toBeInTheDocument()
    expect(screen.getByText('USD')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(1)
  })

  it('deletes normally once every brand pair is inactive (204, no activeBrandCodes conflict)', async () => {
    mockedDefinitionApi.list.mockResolvedValueOnce([USD_JPY]).mockResolvedValueOnce([])
    mockedDefinitionApi.remove.mockResolvedValue(undefined)

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    await waitFor(() => expect(mockedDefinitionApi.remove).toHaveBeenCalledWith(1))
    expect(await screen.findByText('目前沒有幣種對主檔資料')).toBeInTheDocument()
  })

  it('shows a network-error toast and refreshes on a 404 delete (definition already gone)', async () => {
    mockedDefinitionApi.list.mockResolvedValueOnce([USD_JPY]).mockResolvedValueOnce([])
    mockedDefinitionApi.remove.mockRejectedValue(new ApiError(404, { error: 'Definition not found' }))

    renderPage()
    await screen.findByText('USD')

    await userEvent.click(screen.getByText('刪除'))
    await userEvent.click(screen.getByText('確定'))

    expect(await screen.findByText('網路錯誤，請稍後再試')).toBeInTheDocument()
    expect(mockedDefinitionApi.list).toHaveBeenCalledTimes(2)
  })
})
