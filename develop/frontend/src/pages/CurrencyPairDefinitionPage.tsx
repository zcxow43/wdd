import { useCallback, useEffect, useState } from 'react'
import { currencyPairDefinitionApi } from '../api/currencyPairDefinitionApi'
import { currencyApi } from '../api/currencyApi'
import { ApiError } from '../api/client'
import { CurrencyPairDefinitionTable } from '../components/CurrencyPairDefinitionTable'
import { CurrencyPairDefinitionFormModal } from '../components/CurrencyPairDefinitionFormModal'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
  CurrencyPairDefinitionUpdateInput,
} from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'
import './CurrencyPairDefinitionPage.css'

type FormModalState = { mode: 'create' } | { mode: 'edit'; definition: CurrencyPairDefinition } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const CREATE_SUCCESS_MESSAGE = '已建立幣種對，所有品牌已自動套用'
const UPDATE_SUCCESS_MESSAGE = '已更新精度設定'
const DELETE_SUCCESS_MESSAGE = '已刪除幣種對主檔'
const ACTIVE_BRANDS_FALLBACK_MESSAGE = '尚有品牌啟用此幣種對，請先停用'

function activeBrandsBlockedMessage(error: ApiError): string {
  const activeBrandCodes = error.body?.activeBrandCodes
  if (Array.isArray(activeBrandCodes) && activeBrandCodes.length > 0) {
    return `以下品牌仍啟用此幣種對，請先停用：${activeBrandCodes.join(', ')}`
  }
  return ACTIVE_BRANDS_FALLBACK_MESSAGE
}

export function CurrencyPairDefinitionPage() {
  const { showToast } = useToast()
  const [definitions, setDefinitions] = useState<CurrencyPairDefinition[]>([])
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [formModal, setFormModal] = useState<FormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<CurrencyPairDefinition | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)

  const fetchDefinitions = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const data = await currencyPairDefinitionApi.list()
      setDefinitions(data)
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
    currencyApi.list().then(setCurrencies).catch(() => showToast(NETWORK_ERROR_MESSAGE))
  }, [showToast])

  async function handleCreateSubmit(input: CurrencyPairDefinitionCreateInput) {
    try {
      await currencyPairDefinitionApi.create(input)
      setFormModal(null)
      showToast(CREATE_SUCCESS_MESSAGE, 'success')
      await fetchDefinitions()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(NETWORK_ERROR_MESSAGE)
        setFormModal(null)
        await fetchDefinitions()
        return
      }
      throw error
    }
  }

  async function handleEditSubmit(id: number, input: CurrencyPairDefinitionUpdateInput) {
    try {
      await currencyPairDefinitionApi.update(id, input)
      setFormModal(null)
      showToast(UPDATE_SUCCESS_MESSAGE, 'success')
      await fetchDefinitions()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(NETWORK_ERROR_MESSAGE)
        setFormModal(null)
        await fetchDefinitions()
        return
      }
      throw error
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) return
    setDeleteBusy(true)
    try {
      await currencyPairDefinitionApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      showToast(DELETE_SUCCESS_MESSAGE, 'success')
      await fetchDefinitions()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        showToast(activeBrandsBlockedMessage(error))
        setDeleteTarget(null)
        return
      }
      showToast(NETWORK_ERROR_MESSAGE)
      setDeleteTarget(null)
      await fetchDefinitions()
    } finally {
      setDeleteBusy(false)
    }
  }

  return (
    <div className="currency-pair-definition-page">
      <div className="page-title">
        <h1>幣種對主檔</h1>
        <button type="button" className="btn btn-primary" onClick={() => setFormModal({ mode: 'create' })}>
          +新增幣種對
        </button>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">幣種對主檔列表</div>
        </div>

        <div className="currency-pair-definition-table-wrapper">
          {loading && <div className="table-empty">載入中…</div>}
          {!loading && loadError && (
            <div className="table-empty currency-pair-definition-table-status--error">
              資料載入失敗
              <button type="button" className="btn btn-link" onClick={fetchDefinitions}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <CurrencyPairDefinitionTable
              definitions={definitions}
              onEdit={(definition) => setFormModal({ mode: 'edit', definition })}
              onDelete={(definition) => setDeleteTarget(definition)}
            />
          )}
        </div>

        <div className="table-footer">
          <div className="total-count">Total {definitions.length} items</div>
        </div>
      </div>

      {formModal?.mode === 'create' && (
        <CurrencyPairDefinitionFormModal
          mode="create"
          currencies={currencies}
          onSubmit={(input) => handleCreateSubmit(input as CurrencyPairDefinitionCreateInput)}
          onClose={() => setFormModal(null)}
        />
      )}

      {formModal?.mode === 'edit' && (
        <CurrencyPairDefinitionFormModal
          mode="edit"
          initial={formModal.definition}
          currencies={currencies}
          onSubmit={(input) => handleEditSubmit(formModal.definition.id, input as CurrencyPairDefinitionUpdateInput)}
          onClose={() => setFormModal(null)}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="刪除幣種對主檔"
          message={`確定要刪除幣種對主檔「${deleteTarget.baseCurrencyCode}/${deleteTarget.quoteCurrencyCode}」嗎？已套用至各品牌的幣種對不會被移除，但刪除後可重新建立其反向幣種對。若仍有品牌啟用此幣種對，將無法刪除。`}
          onConfirm={handleConfirmDelete}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      )}
    </div>
  )
}
