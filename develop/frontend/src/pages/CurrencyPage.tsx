import { useCallback, useEffect, useMemo, useState } from 'react'
import { currencyApi } from '../api/currencyApi'
import { ApiError } from '../api/client'
import { CurrencyTable } from '../components/CurrencyTable'
import { CurrencyFormModal } from '../components/CurrencyFormModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import type { Currency, CurrencyInput } from '../types/currency'
import './CurrencyPage.css'

type FormModalState = { mode: 'create' } | { mode: 'edit'; currency: Currency } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const NOT_FOUND_MESSAGE = '幣種不存在，請重新整理頁面'
const IN_USE_MESSAGE = '此幣種已配置於幣種對，無法刪除'

export function CurrencyPage() {
  const { showToast } = useToast()
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [searchTerm, setSearchTerm] = useState('')
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<Currency | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)

  const fetchCurrencies = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const data = await currencyApi.list()
      setCurrencies(data)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    fetchCurrencies()
  }, [fetchCurrencies])

  const visibleCurrencies = useMemo(() => {
    const term = searchTerm.trim().toLowerCase()
    if (!term) return currencies
    return currencies.filter(
      (currency) =>
        currency.code.toLowerCase().includes(term) ||
        currency.name.toLowerCase().includes(term) ||
        (currency.nameZh ?? '').toLowerCase().includes(term),
    )
  }, [currencies, searchTerm])

  async function handleCreateSubmit(input: CurrencyInput) {
    await currencyApi.create(input)
    setFormModal(null)
    await fetchCurrencies()
  }

  async function handleEditSubmit(id: number, input: CurrencyInput) {
    try {
      await currencyApi.update(id, input)
      setFormModal(null)
      await fetchCurrencies()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(NOT_FOUND_MESSAGE)
        setFormModal(null)
        await fetchCurrencies()
        return
      }
      throw error
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) return
    setDeleteBusy(true)
    try {
      await currencyApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      await fetchCurrencies()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(NOT_FOUND_MESSAGE)
        setDeleteTarget(null)
        await fetchCurrencies()
      } else if (error instanceof ApiError && error.status === 409) {
        // Currency is referenced by a currency pair — leave the row in place, do not refetch/remove it.
        showToast(IN_USE_MESSAGE)
        setDeleteTarget(null)
      } else {
        showToast(NETWORK_ERROR_MESSAGE)
        setDeleteTarget(null)
        await fetchCurrencies()
      }
    } finally {
      setDeleteBusy(false)
    }
  }

  return (
    <div className="currency-page">
      <div className="page-title">
        <h1>幣種管理</h1>
      </div>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group filter-group--grow">
            <label className="filter-label">Search</label>
            <input
              className="filter-input currency-search"
              type="search"
              placeholder="Search..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              aria-label="搜尋幣種"
            />
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
          <div className="search-table-title">Currencies</div>
        </div>

        <div className="currency-table-wrapper">
          {loading && <div className="table-empty">載入中…</div>}
          {!loading && loadError && (
            <div className="table-empty currency-table-status--error">
              資料載入失敗
              <button type="button" className="btn btn-link" onClick={fetchCurrencies}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <CurrencyTable
              currencies={visibleCurrencies}
              onEdit={(currency) => setFormModal({ mode: 'edit', currency })}
              onDelete={(currency) => setDeleteTarget(currency)}
            />
          )}
        </div>

        <div className="table-footer">
          <div className="total-count">Total {visibleCurrencies.length} items</div>
        </div>
      </div>

      {formModal?.mode === 'create' && (
        <CurrencyFormModal
          mode="create"
          onSubmit={handleCreateSubmit}
          onClose={() => setFormModal(null)}
        />
      )}

      {formModal?.mode === 'edit' && (
        <CurrencyFormModal
          mode="edit"
          initial={formModal.currency}
          onSubmit={(input) => handleEditSubmit(formModal.currency.id, input)}
          onClose={() => setFormModal(null)}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="刪除幣種"
          message={`確定要刪除幣種 ${deleteTarget.code} 嗎？`}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      )}
    </div>
  )
}
