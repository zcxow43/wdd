import { useCallback, useEffect, useMemo, useState } from 'react'
import { CurrencyTable } from '../components/CurrencyTable'
import { CurrencyFormModal } from '../components/CurrencyFormModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import { currencyApi } from '../api/currencyApi'
import { ApiError } from '../api/client'
import type { Currency, CurrencyInput } from '../types/currency'
import './CurrencyPage.css'

type FormModalState = { currency: Currency | null } | null

export function CurrencyPage() {
  const { showToast } = useToast()
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<Currency | null>(null)

  const fetchCurrencies = useCallback(async () => {
    setLoading(true)
    try {
      const data = await currencyApi.list()
      setCurrencies(data)
    } catch {
      showToast('網路錯誤，請稍後再試')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    fetchCurrencies()
  }, [fetchCurrencies])

  const filteredCurrencies = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) {
      return currencies
    }
    return currencies.filter(
      (currency) =>
        currency.code.toLowerCase().includes(term) ||
        currency.name.toLowerCase().includes(term) ||
        (currency.nameZh ?? '').toLowerCase().includes(term),
    )
  }, [currencies, search])

  const handleAdd = () => setFormModal({ currency: null })
  const handleEdit = (currency: Currency) => setFormModal({ currency })
  const closeFormModal = () => setFormModal(null)

  const handleFormSubmit = async (input: CurrencyInput) => {
    try {
      if (formModal?.currency) {
        await currencyApi.update(formModal.currency.id, {
          name: input.name,
          nameZh: input.nameZh,
          symbol: input.symbol,
          decimalPlaces: input.decimalPlaces,
        })
      } else {
        await currencyApi.create(input)
      }
      closeFormModal()
      await fetchCurrencies()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // Let the form modal show the inline "幣種代碼已存在" error and stay open.
        throw error
      }
      if (error instanceof ApiError && error.status === 404) {
        showToast('幣種不存在，請重新整理頁面')
        closeFormModal()
        await fetchCurrencies()
        return
      }
      showToast('網路錯誤，請稍後再試')
    }
  }

  const handleDelete = (currency: Currency) => setDeleteTarget(currency)
  const cancelDelete = () => setDeleteTarget(null)

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await currencyApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      await fetchCurrencies()
    } catch (error) {
      setDeleteTarget(null)
      if (error instanceof ApiError && error.status === 409) {
        // The currency is referenced by a currency pair — leave the row in place,
        // no refetch, since nothing changed server-side (specs/backend/currency-pair.md).
        showToast('此幣種已配置於幣種對，無法刪除')
        return
      }
      if (error instanceof ApiError && error.status === 404) {
        showToast('幣種不存在，請重新整理頁面')
        await fetchCurrencies()
        return
      }
      showToast('網路錯誤，請稍後再試')
    }
  }

  return (
    <div className="currency-page">
      <h1 className="page-title">幣種管理</h1>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label" htmlFor="currency-search">
              搜尋
            </label>
            <input
              id="currency-search"
              type="search"
              className="filter-input"
              placeholder="Search..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div className="filter-actions">
            <button type="button" className="btn btn-primary" onClick={handleAdd}>
              + Add
            </button>
          </div>
        </div>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">
            <span>幣種列表</span>
          </div>
        </div>

        <CurrencyTable
          currencies={filteredCurrencies}
          loading={loading}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />

        <div className="table-footer">
          <div className="total-count">Total {filteredCurrencies.length} items</div>
        </div>
      </div>

      {formModal && (
        <CurrencyFormModal
          initial={formModal.currency}
          onClose={closeFormModal}
          onSubmit={handleFormSubmit}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          message={`確定要刪除幣種 ${deleteTarget.code} 嗎？`}
          onConfirm={confirmDelete}
          onCancel={cancelDelete}
        />
      )}
    </div>
  )
}
