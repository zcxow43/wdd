import { useCallback, useEffect, useState } from 'react'
import { CurrencyPairDefinitionTable } from '../components/CurrencyPairDefinitionTable'
import { CurrencyPairDefinitionFormModal } from '../components/CurrencyPairDefinitionFormModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import { currencyPairDefinitionApi } from '../api/currencyPairDefinitionApi'
import { currencyApi } from '../api/currencyApi'
import { ApiError } from '../api/client'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
} from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'
import './CurrencyPairDefinitionPage.css'

type FormModalState = { mode: 'create' | 'edit'; definition: CurrencyPairDefinition | null } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const GENERIC_STILL_ACTIVE_MESSAGE = '尚有品牌啟用此幣種對，請先停用'

export function CurrencyPairDefinitionPage() {
  const { showToast } = useToast()
  const [definitions, setDefinitions] = useState<CurrencyPairDefinition[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<CurrencyPairDefinition | null>(null)

  const fetchDefinitions = useCallback(async () => {
    setLoading(true)
    try {
      const data = await currencyPairDefinitionApi.list()
      setDefinitions(data)
      setLoadError(false)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    fetchDefinitions()
  }, [fetchDefinitions])

  useEffect(() => {
    currencyApi.list().then(setCurrencies).catch(() => {})
  }, [])

  const handleAdd = () => setFormModal({ mode: 'create', definition: null })
  const handleEdit = (definition: CurrencyPairDefinition) =>
    setFormModal({ mode: 'edit', definition })
  const closeFormModal = () => setFormModal(null)

  const handleFormSubmit = async (input: CurrencyPairDefinitionCreateInput) => {
    if (!formModal) {
      return
    }
    try {
      if (formModal.mode === 'edit' && formModal.definition) {
        await currencyPairDefinitionApi.update(formModal.definition.id, {
          forwardPrecision: input.forwardPrecision,
          reversePrecision: input.reversePrecision,
        })
        closeFormModal()
        showToast('已更新精度設定', 'success')
      } else {
        await currencyPairDefinitionApi.create(input)
        closeFormModal()
        showToast('已建立幣種對，所有品牌已自動套用', 'success')
      }
      await fetchDefinitions()
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 400)) {
        // Let the modal show its own inline error(s) and stay open.
        throw error
      }
      if (error instanceof ApiError && error.status === 404) {
        // A selected currency no longer exists server-side.
        showToast(NETWORK_ERROR_MESSAGE)
        closeFormModal()
        await fetchDefinitions()
        return
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  const handleDelete = (definition: CurrencyPairDefinition) => setDeleteTarget(definition)
  const cancelDelete = () => setDeleteTarget(null)

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await currencyPairDefinitionApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      showToast('已刪除幣種對主檔', 'success')
      await fetchDefinitions()
    } catch (error) {
      setDeleteTarget(null)
      if (error instanceof ApiError && error.status === 409) {
        // One or more brands still have this pair active — nothing changed
        // server-side, so leave the row in place and don't refetch.
        const activeBrandCodes = (error.body as { activeBrandCodes?: string[] } | null | undefined)
          ?.activeBrandCodes
        if (activeBrandCodes && activeBrandCodes.length > 0) {
          showToast(`以下品牌仍啟用此幣種對，請先停用：${activeBrandCodes.join(', ')}`)
        } else {
          showToast(GENERIC_STILL_ACTIVE_MESSAGE)
        }
        return
      }
      if (error instanceof ApiError && error.status === 404) {
        showToast(NETWORK_ERROR_MESSAGE)
        await fetchDefinitions()
        return
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  return (
    <div className="currency-pair-definition-page">
      <h1 className="page-title">幣種對主檔</h1>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">
            <span>幣種對主檔列表</span>
          </div>
          <button type="button" className="btn btn-primary" onClick={handleAdd}>
            +新增幣種對
          </button>
        </div>

        <CurrencyPairDefinitionTable
          definitions={definitions}
          loading={loading}
          error={loadError}
          onRetry={fetchDefinitions}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />

        <div className="table-footer">
          <div className="total-count">Total {definitions.length} items</div>
        </div>
      </div>

      {formModal && (
        <CurrencyPairDefinitionFormModal
          mode={formModal.mode}
          initial={formModal.definition ?? undefined}
          currencies={currencies}
          onClose={closeFormModal}
          onSubmit={handleFormSubmit}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          message={`確定要刪除幣種對主檔「${deleteTarget.baseCurrencyCode}/${deleteTarget.quoteCurrencyCode}」嗎？已套用至各品牌的幣種對不會被移除，但刪除後可重新建立其反向幣種對。若仍有品牌啟用此幣種對，將無法刪除。`}
          onConfirm={confirmDelete}
          onCancel={cancelDelete}
        />
      )}
    </div>
  )
}
