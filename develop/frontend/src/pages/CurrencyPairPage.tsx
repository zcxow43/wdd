import { useCallback, useEffect, useState } from 'react'
import { currencyPairApi } from '../api/currencyPairApi'
import { brandApi } from '../api/brandApi'
import { currencyApi } from '../api/currencyApi'
import { ApiError } from '../api/client'
import { CurrencyPairTable } from '../components/CurrencyPairTable'
import { StatusFilter } from '../components/StatusFilter'
import { BrandFilter } from '../components/BrandFilter'
import { CurrencyPairFormModal } from '../components/CurrencyPairFormModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'
import type { StatusFilter as StatusFilterValue } from '../types/currency'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'
import './CurrencyPairPage.css'

type FormModalState = { mode: 'create' } | { mode: 'edit'; pair: CurrencyPair } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const PAIR_NOT_FOUND_MESSAGE = '幣種對不存在，請重新整理頁面'
const BRAND_NOT_FOUND_MESSAGE = '品牌不存在，請重新整理頁面'
const CURRENCY_NOT_FOUND_MESSAGE = '幣種不存在，請重新整理頁面'

function toActiveParam(filter: StatusFilterValue): boolean | undefined {
  if (filter === 'ACTIVE') return true
  if (filter === 'INACTIVE') return false
  return undefined
}

function notFoundMessage(error: ApiError): string {
  const message = error.body?.error
  if (message === 'Currency pair not found') return PAIR_NOT_FOUND_MESSAGE
  if (message === 'Brand not found') return BRAND_NOT_FOUND_MESSAGE
  return CURRENCY_NOT_FOUND_MESSAGE
}

export function CurrencyPairPage() {
  const { showToast } = useToast()
  const [pairs, setPairs] = useState<CurrencyPair[]>([])
  const [brands, setBrands] = useState<Brand[]>([])
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [brandFilter, setBrandFilter] = useState<number | 'ALL'>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('ALL')
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<CurrencyPair | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)

  const fetchPairs = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const data = await currencyPairApi.list({
        brandId: brandFilter === 'ALL' ? undefined : brandFilter,
        active: toActiveParam(statusFilter),
      })
      setPairs(data)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [brandFilter, statusFilter, showToast])

  useEffect(() => {
    fetchPairs()
  }, [fetchPairs])

  useEffect(() => {
    brandApi.list().then(setBrands).catch(() => showToast(NETWORK_ERROR_MESSAGE))
    currencyApi.list().then(setCurrencies).catch(() => showToast(NETWORK_ERROR_MESSAGE))
  }, [showToast])

  async function handleCreateSubmit(input: CurrencyPairInput) {
    try {
      await currencyPairApi.create(input)
      setFormModal(null)
      await fetchPairs()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(notFoundMessage(error))
        setFormModal(null)
        await fetchPairs()
        return
      }
      throw error
    }
  }

  async function handleEditSubmit(id: number, input: CurrencyPairInput) {
    try {
      await currencyPairApi.update(id, input)
      setFormModal(null)
      await fetchPairs()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(notFoundMessage(error))
        setFormModal(null)
        await fetchPairs()
        return
      }
      throw error
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) return
    setDeleteBusy(true)
    try {
      await currencyPairApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      await fetchPairs()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(PAIR_NOT_FOUND_MESSAGE)
      } else {
        showToast(NETWORK_ERROR_MESSAGE)
      }
      setDeleteTarget(null)
      await fetchPairs()
    } finally {
      setDeleteBusy(false)
    }
  }

  return (
    <div className="currency-pair-page">
      <div className="page-title">
        <h1>Currency Pair Management</h1>
      </div>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label">Brand</label>
            <BrandFilter brands={brands} value={brandFilter} onChange={setBrandFilter} />
          </div>
          <div className="filter-group">
            <label className="filter-label">Status</label>
            <StatusFilter value={statusFilter} onChange={setStatusFilter} />
          </div>
          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={() => setFormModal({ mode: 'create' })}>
              + Add
            </button>
          </div>
        </div>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">Currency Pairs</div>
        </div>

        <div className="currency-pair-table-wrapper">
          {loading && <div className="table-empty">載入中…</div>}
          {!loading && loadError && (
            <div className="table-empty currency-pair-table-status--error">
              資料載入失敗
              <button type="button" className="btn btn-link" onClick={fetchPairs}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <CurrencyPairTable
              pairs={pairs}
              onEdit={(pair) => setFormModal({ mode: 'edit', pair })}
              onDelete={(pair) => setDeleteTarget(pair)}
            />
          )}
        </div>

        <div className="table-footer">
          <div className="total-count">Total {pairs.length} items</div>
        </div>
      </div>

      {formModal?.mode === 'create' && (
        <CurrencyPairFormModal
          mode="create"
          brands={brands}
          currencies={currencies}
          onSubmit={handleCreateSubmit}
          onClose={() => setFormModal(null)}
        />
      )}

      {formModal?.mode === 'edit' && (
        <CurrencyPairFormModal
          mode="edit"
          initial={formModal.pair}
          brands={brands}
          currencies={currencies}
          onSubmit={(input) => handleEditSubmit(formModal.pair.id, input)}
          onClose={() => setFormModal(null)}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="刪除幣種對"
          message={`確定要刪除 ${deleteTarget.brandCode} 品牌的幣種對 ${deleteTarget.baseCurrencyCode}/${deleteTarget.quoteCurrencyCode} 嗎？`}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      )}
    </div>
  )
}
