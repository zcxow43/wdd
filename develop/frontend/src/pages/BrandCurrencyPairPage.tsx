import { useCallback, useEffect, useState } from 'react'
import { fetchAuditRequests } from '../api/audit'
import { type Brand, fetchBrands } from '../api/brands'
import {
  type CurrencyPair,
  deleteCurrencyPair,
  fetchCurrencyPairsByBrand,
  updateCurrencyPair,
} from '../api/currencyPairDefinitions'
import { ApiError } from '../api/http'
import ToggleSwitch from '../components/ToggleSwitch'
import Toast from '../components/Toast'
import './BrandCurrencyPairPage.css'

const PENDING_TOOLTIP = '此列有待審核的變更，需先完成審核'

type RateType = 'AUTO' | 'MANUAL'

interface RowEditState {
  rateType: RateType
  rateInput: string
  error?: string
}

type RowBusy = 'save' | 'toggle' | undefined

function toRowEditState(pair: CurrencyPair): RowEditState {
  const rateType = pair.rateType === 'MANUAL' ? 'MANUAL' : 'AUTO'
  return {
    rateType,
    rateInput: pair.rate === null || pair.rate === undefined ? '' : String(pair.rate),
  }
}

function BrandCurrencyPairPage() {
  const [brands, setBrands] = useState<Brand[] | null>(null)
  const [brandsLoading, setBrandsLoading] = useState(true)
  const [brandsError, setBrandsError] = useState(false)
  const [selectedBrandId, setSelectedBrandId] = useState<number | null>(null)

  const [pairs, setPairs] = useState<CurrencyPair[] | null>(null)
  const [pairsLoading, setPairsLoading] = useState(false)
  const [pairsError, setPairsError] = useState(false)

  const [rowEdits, setRowEdits] = useState<Record<number, RowEditState>>({})
  const [rowBusy, setRowBusy] = useState<Record<number, RowBusy>>({})
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set())

  const [deletingPair, setDeletingPair] = useState<CurrencyPair | null>(null)
  const [deleting, setDeleting] = useState(false)

  const [toastMessage, setToastMessage] = useState<string | null>(null)

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

  const loadPendingMarkers = useCallback((brandId: number) => {
    fetchAuditRequests({
      status: 'PENDING',
      entityType: 'CURRENCY_PAIR',
      brandId,
    })
      .then((requests) => {
        setPendingIds(new Set(requests.map((request) => request.entityId)))
      })
      .catch(() => {
        // Non-critical: leave the previous pending markers in place rather
        // than blocking the page on this secondary lookup.
      })
  }, [])

  const loadPairs = useCallback(
    (brandId: number) => {
      setPairsLoading(true)
      setPairsError(false)
      fetchCurrencyPairsByBrand(brandId)
        .then((data) => {
          setPairs(data)
          const edits: Record<number, RowEditState> = {}
          data.forEach((pair) => {
            edits[pair.id] = toRowEditState(pair)
          })
          setRowEdits(edits)
          setRowBusy({})
          loadPendingMarkers(brandId)
        })
        .catch(() => {
          setPairsError(true)
        })
        .finally(() => {
          setPairsLoading(false)
        })
    },
    [loadPendingMarkers],
  )

  useEffect(() => {
    if (selectedBrandId !== null) {
      loadPairs(selectedBrandId)
    }
  }, [selectedBrandId, loadPairs])

  const handleRateTypeChange = (pair: CurrencyPair, rateType: RateType) => {
    setRowEdits((current) => ({
      ...current,
      [pair.id]: {
        rateType,
        rateInput: rateType === 'AUTO' ? '' : current[pair.id]?.rateInput ?? '',
        error: undefined,
      },
    }))
  }

  const handleRateInputChange = (pair: CurrencyPair, value: string) => {
    setRowEdits((current) => ({
      ...current,
      [pair.id]: {
        ...(current[pair.id] ?? toRowEditState(pair)),
        rateInput: value,
        error: undefined,
      },
    }))
  }

  const revertRow = (pair: CurrencyPair) => {
    setRowEdits((current) => ({
      ...current,
      [pair.id]: toRowEditState(pair),
    }))
  }

  const handleSaveRow = (pair: CurrencyPair) => {
    const edit = rowEdits[pair.id] ?? toRowEditState(pair)

    if (edit.rateType === 'MANUAL') {
      const trimmed = edit.rateInput.trim()
      const numeric = Number(trimmed)
      if (trimmed === '' || Number.isNaN(numeric) || numeric <= 0) {
        setRowEdits((current) => ({
          ...current,
          [pair.id]: { ...edit, error: '請輸入有效匯率' },
        }))
        return
      }
    }

    setRowBusy((current) => ({ ...current, [pair.id]: 'save' }))

    updateCurrencyPair(pair.id, {
      rateType: edit.rateType,
      rate: edit.rateType === 'MANUAL' ? Number(edit.rateInput.trim()) : null,
    })
      .then(() => {
        // 202: the change is only a pending request — the row's committed
        // values are unchanged, so discard the draft edit and show it as
        // awaiting review instead.
        revertRow(pair)
        setPendingIds((current) => new Set(current).add(pair.id))
        setToastMessage('已送出審核，核准後才會生效')
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 400) {
          setRowEdits((current) => ({
            ...current,
            [pair.id]: { ...edit, error: '請輸入有效匯率' },
          }))
        } else if (error instanceof ApiError && error.status === 409) {
          revertRow(pair)
          setToastMessage('此列已有待審核的變更')
          if (selectedBrandId !== null) {
            loadPendingMarkers(selectedBrandId)
          }
        } else {
          revertRow(pair)
          setToastMessage('更新失敗，請稍後再試')
        }
      })
      .finally(() => {
        setRowBusy((current) => ({ ...current, [pair.id]: undefined }))
      })
  }

  const handleToggleActive = (pair: CurrencyPair) => {
    // The change only takes effect after approval, so the switch must not
    // flip optimistically — it stays at its currently-effective position
    // (a disabled "送審中..." label communicates the in-flight request)
    // and only gains the 審核中 badge once the request is accepted.
    const nextActive = !pair.active

    setRowBusy((current) => ({ ...current, [pair.id]: 'toggle' }))

    updateCurrencyPair(pair.id, { active: nextActive })
      .then(() => {
        setPendingIds((current) => new Set(current).add(pair.id))
        setToastMessage('已送出審核，核准後才會生效')
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 409) {
          setToastMessage('此列已有待審核的變更')
          if (selectedBrandId !== null) {
            loadPendingMarkers(selectedBrandId)
          }
        } else {
          setToastMessage('更新失敗，請稍後再試')
        }
      })
      .finally(() => {
        setRowBusy((current) => ({ ...current, [pair.id]: undefined }))
      })
  }

  const confirmDelete = () => {
    if (!deletingPair) {
      return
    }
    const target = deletingPair
    setDeleting(true)
    deleteCurrencyPair(target.id)
      .then(() => {
        // 202: the deletion is only a pending request — the row stays on
        // screen until it is approved, marked as awaiting review.
        setPendingIds((current) => new Set(current).add(target.id))
        setDeletingPair(null)
        setToastMessage('已送出審核，核准後才會生效')
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 409) {
          setToastMessage('此列已有待審核的變更')
          if (selectedBrandId !== null) {
            loadPendingMarkers(selectedBrandId)
          }
        } else {
          setToastMessage('刪除失敗，請稍後再試')
        }
        setDeletingPair(null)
      })
      .finally(() => {
        setDeleting(false)
      })
  }

  return (
    <div className="bcp-page">
      <div className="bcp-page__breadcrumb">匯率中心 &gt; 品牌幣種對</div>
      <h1 className="bcp-page__title">品牌幣種對</h1>

      {brandsLoading && <p className="bcp-page__status">載入中...</p>}

      {!brandsLoading && brandsError && (
        <div className="bcp-page__error">
          <p>載入品牌清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadBrands}>
            重試
          </button>
        </div>
      )}

      {!brandsLoading && !brandsError && brands && (
        <>
          <div className="bcp-brand-selector" role="tablist" aria-label="品牌選擇">
            {brands.map((brand) => (
              <button
                key={brand.id}
                type="button"
                role="tab"
                aria-selected={selectedBrandId === brand.id}
                className={`bcp-brand-selector__item${
                  selectedBrandId === brand.id
                    ? ' bcp-brand-selector__item--selected'
                    : ''
                }`}
                onClick={() => setSelectedBrandId(brand.id)}
              >
                {brand.code}
              </button>
            ))}
          </div>

          {pairsLoading && <p className="bcp-page__status">載入中...</p>}

          {!pairsLoading && pairsError && (
            <div className="bcp-page__error">
              <p>載入幣種對清單失敗，請稍後再試。</p>
              <button
                type="button"
                onClick={() =>
                  selectedBrandId !== null && loadPairs(selectedBrandId)
                }
              >
                重試
              </button>
            </div>
          )}

          {!pairsLoading && !pairsError && pairs && pairs.length === 0 && (
            <p className="bcp-empty-state">
              此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義
            </p>
          )}

          {!pairsLoading && !pairsError && pairs && pairs.length > 0 && (
            <table className="bcp-table">
              <thead>
                <tr>
                  <th>幣種對</th>
                  <th>匯率類型</th>
                  <th>匯率</th>
                  <th>狀態</th>
                  <th>審核</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {pairs.map((pair) => {
                  const edit = rowEdits[pair.id] ?? toRowEditState(pair)
                  const busy = rowBusy[pair.id]
                  const isPendingReview = pendingIds.has(pair.id)
                  const rowDisabled = busy !== undefined || isPendingReview

                  return (
                    <tr key={pair.id}>
                      <td className="bcp-table__code-cell">
                        {pair.baseCurrencyCode}/{pair.quoteCurrencyCode}
                      </td>
                      <td>
                        <div className="bcp-radio-group">
                          <label className="bcp-radio">
                            <input
                              type="radio"
                              name={`rateType-${pair.id}`}
                              value="AUTO"
                              checked={edit.rateType === 'AUTO'}
                              disabled={rowDisabled}
                              onChange={() =>
                                handleRateTypeChange(pair, 'AUTO')
                              }
                            />
                            自動
                          </label>
                          <label className="bcp-radio">
                            <input
                              type="radio"
                              name={`rateType-${pair.id}`}
                              value="MANUAL"
                              checked={edit.rateType === 'MANUAL'}
                              disabled={rowDisabled}
                              onChange={() =>
                                handleRateTypeChange(pair, 'MANUAL')
                              }
                            />
                            手動
                          </label>
                        </div>
                      </td>
                      <td>
                        <input
                          type="number"
                          className="bcp-form__input"
                          aria-label={`${pair.baseCurrencyCode}/${pair.quoteCurrencyCode} 匯率`}
                          value={edit.rateInput}
                          disabled={rowDisabled || edit.rateType === 'AUTO'}
                          onChange={(e) =>
                            handleRateInputChange(pair, e.target.value)
                          }
                        />
                        {edit.error && (
                          <p className="bcp-form__error">{edit.error}</p>
                        )}
                      </td>
                      <td>
                        <ToggleSwitch
                          checked={pair.active}
                          disabled={rowDisabled}
                          isPending={busy === 'toggle'}
                          onChange={() => handleToggleActive(pair)}
                          label={pair.active ? '啟用' : '停用'}
                          pendingLabel="送審中..."
                          ariaLabel={`切換 ${pair.baseCurrencyCode}/${pair.quoteCurrencyCode} 啟用狀態`}
                        />
                      </td>
                      <td>
                        {isPendingReview && (
                          <span
                            className="bcp-badge bcp-badge--pending"
                            title={PENDING_TOOLTIP}
                          >
                            審核中
                          </span>
                        )}
                      </td>
                      <td>
                        <div className="bcp-table__actions">
                          <button
                            type="button"
                            className="bcp-page__btn bcp-page__btn--secondary"
                            disabled={rowDisabled}
                            onClick={() => handleSaveRow(pair)}
                          >
                            儲存
                          </button>
                          <button
                            type="button"
                            className="bcp-page__btn bcp-page__btn--danger"
                            disabled={rowDisabled}
                            onClick={() => setDeletingPair(pair)}
                          >
                            刪除
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </>
      )}

      {deletingPair && (
        <div className="bcp-modal__overlay">
          <div className="bcp-modal__card">
            <p className="bcp-modal__confirm-text">
              確定要送出刪除「{deletingPair.baseCurrencyCode}/
              {deletingPair.quoteCurrencyCode}」的申請嗎？核准後才會真正刪除。
            </p>
            <div className="bcp-modal__actions">
              <button
                type="button"
                className="bcp-page__btn bcp-page__btn--secondary"
                onClick={() => setDeletingPair(null)}
                disabled={deleting}
              >
                取消
              </button>
              <button
                type="button"
                className="bcp-page__btn bcp-page__btn--danger"
                onClick={confirmDelete}
                disabled={deleting}
              >
                刪除
              </button>
            </div>
          </div>
        </div>
      )}

      {toastMessage && (
        <Toast
          message={toastMessage}
          onDismiss={() => setToastMessage(null)}
        />
      )}
    </div>
  )
}

export default BrandCurrencyPairPage
