import { useCallback, useEffect, useState } from 'react'
import { type Brand, fetchBrands } from '../api/brands'
import {
  type ExchangeRateLatest,
  fetchLatestExchangeRates,
  syncExchangeRates,
} from '../api/exchangeRates'
import { ApiError } from '../api/http'
import Toast from '../components/Toast'
import './ExchangeRateSyncPage.css'

const COOLDOWN_SECONDS = 60

function formatRate(
  value: number | null,
  precision: number,
): { text: string; unsynced: boolean } {
  if (value === null) {
    return { text: '尚未同步', unsynced: true }
  }
  return { text: value.toFixed(precision), unsynced: false }
}

function formatBrandRate(value: number | null, precision: number): string {
  return value === null ? '-' : value.toFixed(precision)
}

function formatRateMinute(value: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function ExchangeRateSyncPage() {
  const [brands, setBrands] = useState<Brand[] | null>(null)
  const [brandsLoading, setBrandsLoading] = useState(true)
  const [brandsError, setBrandsError] = useState(false)
  const [selectedBrandId, setSelectedBrandId] = useState<number | null>(null)

  const [rates, setRates] = useState<ExchangeRateLatest[] | null>(null)
  const [ratesLoading, setRatesLoading] = useState(false)
  const [ratesError, setRatesError] = useState(false)

  const [searchText, setSearchText] = useState('')

  const [syncing, setSyncing] = useState(false)
  const [cooldownSeconds, setCooldownSeconds] = useState(0)
  const [toastMessage, setToastMessage] = useState<string | null>(null)
  const [toastVariant, setToastVariant] = useState<'error' | undefined>(
    undefined,
  )

  const loadBrands = useCallback(() => {
    setBrandsLoading(true)
    setBrandsError(false)
    fetchBrands()
      .then((data) => {
        setBrands(data)
        setSelectedBrandId((current) => current ?? data[0]?.id ?? null)
      })
      .catch(() => {
        setBrandsError(true)
      })
      .finally(() => {
        setBrandsLoading(false)
      })
  }, [])

  useEffect(() => {
    loadBrands()
  }, [loadBrands])

  const loadRates = useCallback((brandId: number) => {
    setRatesLoading(true)
    setRatesError(false)
    fetchLatestExchangeRates(brandId)
      .then((data) => {
        setRates(data)
      })
      .catch(() => {
        setRatesError(true)
      })
      .finally(() => {
        setRatesLoading(false)
      })
  }, [])

  useEffect(() => {
    if (selectedBrandId !== null) {
      loadRates(selectedBrandId)
    }
  }, [selectedBrandId, loadRates])

  useEffect(() => {
    if (cooldownSeconds <= 0) {
      return
    }
    const timer = setTimeout(() => {
      setCooldownSeconds((current) => Math.max(current - 1, 0))
    }, 1000)
    return () => clearTimeout(timer)
  }, [cooldownSeconds])

  const isCoolingDown = cooldownSeconds > 0
  const isSyncControlsDisabled = syncing || isCoolingDown

  const handleSync = () => {
    setSyncing(true)
    syncExchangeRates()
      .then((response) => {
        if (selectedBrandId !== null) {
          loadRates(selectedBrandId)
        }
        const message =
          response.skipped.length === 0
            ? `已同步 ${response.updated.length} 筆匯率`
            : `已同步 ${response.updated.length} 筆匯率，${response.skipped.length} 筆供應商未提供報價`
        setToastVariant(undefined)
        setToastMessage(message)
        setCooldownSeconds(COOLDOWN_SECONDS)
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 429) {
          const body = error.body as { retryAfterSeconds?: number } | undefined
          setToastVariant('error')
          setToastMessage(error.message)
          setCooldownSeconds(body?.retryAfterSeconds ?? COOLDOWN_SECONDS)
        } else {
          setToastVariant('error')
          setToastMessage('同步失敗，請稍後再試')
          setCooldownSeconds(0)
        }
      })
      .finally(() => {
        setSyncing(false)
      })
  }

  const buttonLabel = syncing
    ? '同步中...'
    : isCoolingDown
      ? `${cooldownSeconds} 秒後可同步`
      : '同步最新匯率'

  const normalizedSearch = searchText.trim().toLowerCase()
  const visibleRates = rates
    ? normalizedSearch === ''
      ? rates
      : rates.filter(
          (rate) =>
            rate.baseCurrencyCode.toLowerCase().includes(normalizedSearch) ||
            rate.quoteCurrencyCode.toLowerCase().includes(normalizedSearch),
        )
    : null

  return (
    <div className="exchange-rate-page">
      <div className="exchange-rate-page__breadcrumb">
        匯率中心 &gt; 匯率同步
      </div>
      <div className="exchange-rate-page__header">
        <h1 className="exchange-rate-page__title">匯率同步</h1>
        <div className="exchange-rate-page__controls">
          <input
            className="exchange-rate-page__search-input"
            type="text"
            placeholder="搜尋基準幣或報價幣"
            aria-label="搜尋基準幣或報價幣"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
          />
          <button
            type="button"
            className="exchange-rate-page__btn exchange-rate-page__btn--primary"
            disabled={isSyncControlsDisabled}
            onClick={handleSync}
          >
            {buttonLabel}
          </button>
        </div>
      </div>

      {brandsLoading && (
        <p className="exchange-rate-page__status">載入中...</p>
      )}

      {!brandsLoading && brandsError && (
        <div className="exchange-rate-page__error">
          <p>載入品牌清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadBrands}>
            重試
          </button>
        </div>
      )}

      {!brandsLoading && !brandsError && brands && (
        <>
          <div
            className="exchange-rate-brand-selector"
            role="tablist"
            aria-label="品牌選擇"
          >
            {brands.map((brand) => (
              <button
                key={brand.id}
                type="button"
                role="tab"
                aria-selected={selectedBrandId === brand.id}
                className={`exchange-rate-brand-selector__item${
                  selectedBrandId === brand.id
                    ? ' exchange-rate-brand-selector__item--selected'
                    : ''
                }`}
                onClick={() => setSelectedBrandId(brand.id)}
              >
                {brand.code}
              </button>
            ))}
          </div>

          {ratesLoading && !rates && (
            <p className="exchange-rate-page__status">載入中...</p>
          )}

          {!ratesLoading && ratesError && !rates && (
            <div className="exchange-rate-page__error">
              <p>載入匯率清單失敗，請稍後再試。</p>
              <button
                type="button"
                onClick={() =>
                  selectedBrandId !== null && loadRates(selectedBrandId)
                }
              >
                重試
              </button>
            </div>
          )}

          {visibleRates && (
            <table className="exchange-rate-table">
              <thead>
                <tr>
                  <th>基準幣</th>
                  <th>報價幣</th>
                  <th>原始匯率</th>
                  <th>入金匯率</th>
                  <th>出金匯率</th>
                  <th>匯率時間</th>
                  <th>資料來源</th>
                </tr>
              </thead>
              <tbody>
                {visibleRates.map((rate) => {
                  const raw = formatRate(rate.rate, rate.precision)
                  const deposit = formatBrandRate(
                    rate.depositRate,
                    rate.precision,
                  )
                  const withdrawal = formatBrandRate(
                    rate.withdrawalRate,
                    rate.precision,
                  )
                  return (
                    <tr key={rate.currencyPairDefinitionId}>
                      <td className="exchange-rate-table__code-cell">
                        {rate.baseCurrencyCode}
                      </td>
                      <td className="exchange-rate-table__code-cell">
                        {rate.quoteCurrencyCode}
                      </td>
                      <td
                        className={
                          raw.unsynced ? 'exchange-rate-table__unsynced' : ''
                        }
                      >
                        {raw.text}
                      </td>
                      <td
                        className={
                          rate.depositRate === null
                            ? 'exchange-rate-table__unsynced'
                            : 'exchange-rate-table__brand-rate'
                        }
                      >
                        {deposit}
                      </td>
                      <td
                        className={
                          rate.withdrawalRate === null
                            ? 'exchange-rate-table__unsynced'
                            : 'exchange-rate-table__brand-rate'
                        }
                      >
                        {withdrawal}
                      </td>
                      <td>{formatRateMinute(rate.rateMinute)}</td>
                      <td>{rate.source ?? '-'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </>
      )}

      {toastMessage && (
        <Toast
          message={toastMessage}
          variant={toastVariant}
          onDismiss={() => setToastMessage(null)}
        />
      )}
    </div>
  )
}

export default ExchangeRateSyncPage
