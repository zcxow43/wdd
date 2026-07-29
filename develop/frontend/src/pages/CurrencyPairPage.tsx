import { useCallback, useEffect, useState } from 'react'
import { currencyPairApi } from '../api/currencyPairApi'
import { brandApi } from '../api/brandApi'
import { currencyApi } from '../api/currencyApi'
import { auditApi } from '../audit/auditApi'
import { registerDiffRenderer } from '../audit/diffRegistry'
import { ApiError } from '../api/client'
import { CurrencyPairTable } from '../components/CurrencyPairTable'
import { renderCurrencyPairDiff } from '../components/CurrencyPairDiff'
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

// Registers this feature's diff renderer with the generic audit module, so
// the Audit page (`/audit-requests`) can render CURRENCY_PAIR requests with
// the proper labeled before/after layout instead of the generic fallback.
// Runs once, at module load, as soon as this page module is imported (App.tsx
// imports it eagerly alongside the route registration) — well before the
// Audit page can be visited. See specs/frontend/currency-pair-approval.md.
registerDiffRenderer('CURRENCY_PAIR', renderCurrencyPairDiff)

type FormModalState = { mode: 'create' } | { mode: 'edit'; pair: CurrencyPair } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const PAIR_NOT_FOUND_MESSAGE = '幣種對不存在，請重新整理頁面'
const BRAND_NOT_FOUND_MESSAGE = '品牌不存在，請重新整理頁面'
const CURRENCY_NOT_FOUND_MESSAGE = '幣種不存在，請重新整理頁面'
const LIVE_DUPLICATE_ERROR = 'Currency pair already exists for this brand'
const PENDING_DUPLICATE_MESSAGE = '此幣種對已有待審核的異動申請'

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

/** A 409 whose message isn't the "live duplicate" case is a pending-request conflict. */
function isPendingDuplicateConflict(error: ApiError): boolean {
  return error.body?.error !== LIVE_DUPLICATE_ERROR
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
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set())

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

  // Rows with a PENDING request against them are marked and their
  // Edit/Delete actions disabled, to avoid the "already has a pending
  // request" 409 in the common case. Fetched independently of the
  // brand/status filters since it must match every pair's id, not just the
  // currently-filtered ones.
  const fetchPendingIds = useCallback(async () => {
    try {
      const requests = await auditApi.list({ entityType: 'CURRENCY_PAIR', status: 'PENDING' })
      setPendingIds(
        new Set(requests.filter((request) => request.entityId !== null).map((request) => request.entityId as number)),
      )
    } catch {
      // Non-critical for the page's core functionality — leave the previous
      // badge state as-is rather than surfacing another error toast.
    }
  }, [])

  const refresh = useCallback(async () => {
    await Promise.all([fetchPairs(), fetchPendingIds()])
  }, [fetchPairs, fetchPendingIds])

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(() => {
    brandApi.list().then(setBrands).catch(() => showToast(NETWORK_ERROR_MESSAGE))
    currencyApi.list().then(setCurrencies).catch(() => showToast(NETWORK_ERROR_MESSAGE))
  }, [showToast])

  async function handleCreateSubmit(input: CurrencyPairInput) {
    try {
      await currencyPairApi.create(input)
      setFormModal(null)
      showToast('已送出新增申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(notFoundMessage(error))
        setFormModal(null)
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 409 && isPendingDuplicateConflict(error)) {
        showToast(PENDING_DUPLICATE_MESSAGE)
        setFormModal(null)
        await refresh()
        return
      }
      throw error
    }
  }

  async function handleEditSubmit(id: number, input: CurrencyPairInput) {
    try {
      await currencyPairApi.update(id, input)
      setFormModal(null)
      showToast('已送出修改申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(notFoundMessage(error))
        setFormModal(null)
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 409 && isPendingDuplicateConflict(error)) {
        showToast(PENDING_DUPLICATE_MESSAGE)
        setFormModal(null)
        await refresh()
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
      showToast('已送出刪除申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(PAIR_NOT_FOUND_MESSAGE)
      } else if (error instanceof ApiError && error.status === 409) {
        showToast(PENDING_DUPLICATE_MESSAGE)
      } else {
        showToast(NETWORK_ERROR_MESSAGE)
      }
      setDeleteTarget(null)
      await refresh()
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
              <button type="button" className="btn btn-link" onClick={refresh}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <CurrencyPairTable
              pairs={pairs}
              pendingIds={pendingIds}
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
          message={`確定要送出刪除 ${deleteTarget.brandCode} 品牌幣種對 ${deleteTarget.baseCurrencyCode}/${deleteTarget.quoteCurrencyCode} 的申請嗎？`}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      )}
    </div>
  )
}
