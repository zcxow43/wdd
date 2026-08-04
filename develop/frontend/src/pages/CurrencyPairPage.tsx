import { useCallback, useEffect, useState } from 'react'
import { CurrencyPairTable } from '../components/CurrencyPairTable'
import { CurrencyPairFormModal } from '../components/CurrencyPairFormModal'
import { renderCurrencyPairDiff } from '../components/CurrencyPairDiff'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import { currencyPairApi } from '../api/currencyPairApi'
import { brandApi } from '../api/brandApi'
import { currencyApi } from '../api/currencyApi'
import { auditApi } from '../audit/auditApi'
import { registerDiffRenderer } from '../audit/diffRegistry'
import { ApiError } from '../api/client'
import type { CurrencyPair, CurrencyPairInput } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'
import './CurrencyPairPage.css'

// This is the currency-pair feature's own entry point: registering the dedicated
// diff renderer here (module scope) means it runs as soon as this module is
// imported — App.tsx imports CurrencyPairPage eagerly, so this executes at app
// startup, before the Audit page can ever be visited, regardless of which route
// the user lands on first.
registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)

type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'
type FormModalState = { pair: CurrencyPair } | null

const BRAND_FILTER_ALL = 'ALL'

const LIVE_DUPLICATE_MESSAGE = 'Currency pair already exists for this brand/base/quote combination'
const PAIR_NOT_FOUND_MESSAGE = 'Currency pair not found'
const BRAND_NOT_FOUND_MESSAGE = 'Brand not found'

/** True for a 409 whose message is anything other than the known live-duplicate case. */
function isPendingDuplicateConflict(error: ApiError): boolean {
  return error.message !== LIVE_DUPLICATE_MESSAGE
}

export function CurrencyPairPage() {
  const { showToast } = useToast()
  const [pairs, setPairs] = useState<CurrencyPair[]>([])
  const [loading, setLoading] = useState(true)
  const [brands, setBrands] = useState<Brand[]>([])
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [brandFilter, setBrandFilter] = useState<string>(BRAND_FILTER_ALL)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<CurrencyPair | null>(null)
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set())

  const fetchPairs = useCallback(async () => {
    setLoading(true)
    try {
      const data = await currencyPairApi.list({
        brandId: brandFilter === BRAND_FILTER_ALL ? undefined : Number(brandFilter),
        active: statusFilter === 'ALL' ? undefined : statusFilter === 'ACTIVE',
      })
      setPairs(data)
    } catch {
      showToast('網路錯誤，請稍後再試')
    } finally {
      setLoading(false)
    }
  }, [brandFilter, statusFilter, showToast])

  const fetchPendingIds = useCallback(async () => {
    try {
      const requests = await auditApi.list({ entityType: 'CURRENCY_PAIR', status: 'PENDING' })
      setPendingIds(
        new Set(
          requests
            .filter((request) => request.entityId !== null)
            .map((request) => request.entityId as number),
        ),
      )
    } catch {
      // Non-critical — leave the previous badge state rather than adding another
      // error toast on top of the main list's own error handling.
    }
  }, [])

  const refresh = useCallback(async () => {
    await Promise.all([fetchPairs(), fetchPendingIds()])
  }, [fetchPairs, fetchPendingIds])

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(() => {
    brandApi.list().then(setBrands).catch(() => {})
    currencyApi.list().then(setCurrencies).catch(() => {})
  }, [])

  const handleEdit = (pair: CurrencyPair) => setFormModal({ pair })
  const closeFormModal = () => setFormModal(null)

  const handleEditSubmit = async (input: CurrencyPairInput) => {
    if (!formModal) {
      return
    }
    try {
      await currencyPairApi.update(formModal.pair.id, input)
      closeFormModal()
      showToast('已送出修改申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        if (isPendingDuplicateConflict(error)) {
          showToast('此幣種對已有待審核的異動申請')
          closeFormModal()
          await refresh()
          return
        }
        // Live-duplicate: let the modal show its own inline field error and stay open.
        throw error
      }
      if (error instanceof ApiError && error.status === 404) {
        if (error.message === PAIR_NOT_FOUND_MESSAGE) {
          showToast('幣種對不存在，請重新整理頁面')
        } else if (error.message === BRAND_NOT_FOUND_MESSAGE) {
          showToast('品牌不存在，請重新整理頁面')
        } else {
          showToast('幣種不存在，請重新整理頁面')
        }
        closeFormModal()
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 400) {
        // Client-side validation already prevents the common cases; let the modal
        // show a generic inline error rather than assuming the change applied.
        throw error
      }
      showToast('網路錯誤，請稍後再試')
    }
  }

  const handleDelete = (pair: CurrencyPair) => setDeleteTarget(pair)
  const cancelDelete = () => setDeleteTarget(null)

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await currencyPairApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      showToast('已送出刪除申請，待審核', 'success')
      await refresh()
    } catch (error) {
      setDeleteTarget(null)
      if (error instanceof ApiError && error.status === 409) {
        showToast('此幣種對已有待審核的異動申請')
        await refresh()
      } else if (error instanceof ApiError && error.status === 404) {
        showToast('幣種對不存在，請重新整理頁面')
        await refresh()
      } else {
        showToast('網路錯誤，請稍後再試')
      }
    }
  }

  return (
    <div className="currency-pair-page">
      <h1 className="page-title">品牌幣種對</h1>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label" htmlFor="currency-pair-filter-brand">
              品牌
            </label>
            <select
              id="currency-pair-filter-brand"
              className="filter-input"
              value={brandFilter}
              onChange={(event) => setBrandFilter(event.target.value)}
            >
              <option value={BRAND_FILTER_ALL}>全部</option>
              {brands.map((brand) => (
                <option key={brand.id} value={String(brand.id)}>
                  {brand.code}
                </option>
              ))}
            </select>
          </div>

          <div className="filter-group">
            <label className="filter-label" htmlFor="currency-pair-filter-status">
              狀態
            </label>
            <select
              id="currency-pair-filter-status"
              className="filter-input"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
            >
              <option value="ALL">全部</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </div>
        </div>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">
            <span>幣種對列表</span>
          </div>
        </div>

        <CurrencyPairTable
          pairs={pairs}
          loading={loading}
          pendingIds={pendingIds}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />

        <div className="table-footer">
          <div className="total-count">Total {pairs.length} items</div>
        </div>
      </div>

      {formModal && (
        <CurrencyPairFormModal
          initial={formModal.pair}
          brands={brands}
          currencies={currencies}
          onClose={closeFormModal}
          onSubmit={handleEditSubmit}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          message={`確定要送出刪除 ${deleteTarget.brandCode} 品牌幣種對 ${deleteTarget.baseCurrencyCode}/${deleteTarget.quoteCurrencyCode} 的申請嗎？`}
          onConfirm={confirmDelete}
          onCancel={cancelDelete}
        />
      )}
    </div>
  )
}
