import { useCallback, useEffect, useState } from 'react'
import { BrandFilter } from '../components/BrandFilter'
import { SpreadGroupTable } from '../components/SpreadGroupTable'
import { SpreadDefaultFormModal } from '../components/SpreadDefaultFormModal'
import { SpreadGroupFormModal } from '../components/SpreadGroupFormModal'
import { renderSpreadDefaultDiff } from '../components/SpreadDefaultDiff'
import { renderSpreadGroupDiff } from '../components/SpreadGroupDiff'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import { spreadDefaultApi, spreadGroupApi } from '../api/spreadApi'
import { brandApi } from '../api/brandApi'
import { currencyPairApi } from '../api/currencyPairApi'
import { auditApi } from '../audit/auditApi'
import { registerDiffRenderer } from '../audit/diffRegistry'
import { ApiError } from '../api/client'
import type { SpreadDefault, SpreadDefaultInput, SpreadGroup, SpreadGroupInput } from '../types/spread'
import type { Brand } from '../types/brand'
import type { CurrencyPair } from '../types/currencyPair'
import './SpreadPage.css'

// This is the spread feature's own entry point: registering the dedicated diff
// renderers here (module scope) means they run as soon as this module is
// imported — App.tsx imports SpreadPage eagerly, so this executes at app
// startup, before the Audit page can ever be visited, regardless of which route
// the user lands on first (matching CurrencyPairPage's own self-registration).
registerDiffRenderer('SPREAD_DEFAULT', renderSpreadDefaultDiff)
registerDiffRenderer('SPREAD_GROUP', renderSpreadGroupDiff)

type GroupFormModalState = { mode: 'create' | 'edit'; group: SpreadGroup | null } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const PENDING_DUPLICATE_MESSAGE = '此項目已有待審核的異動申請'
const LIVE_DUPLICATE_GROUP_NAME_MESSAGE = 'Spread group name already exists for this brand'

/** True for a 409 whose message is anything other than the known live-duplicate case. */
function isPendingDuplicateConflict(error: ApiError): boolean {
  return error.message !== LIVE_DUPLICATE_GROUP_NAME_MESSAGE
}

export function SpreadPage() {
  const { showToast } = useToast()
  const [brands, setBrands] = useState<Brand[]>([])
  // null: not yet decided (auto-selects the first active brand once brands load).
  // 'ALL': the user explicitly chose "全部" from the brand filter — this page is
  // brand-scoped, so that shows a placeholder rather than fetching anything.
  const [brandId, setBrandId] = useState<number | 'ALL' | null>(null)
  const [defaultSpread, setDefaultSpread] = useState<SpreadDefault | null>(null)
  const [groups, setGroups] = useState<SpreadGroup[]>([])
  const [pairs, setPairs] = useState<CurrencyPair[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [defaultPendingIds, setDefaultPendingIds] = useState<Set<number>>(new Set())
  const [groupPendingIds, setGroupPendingIds] = useState<Set<number>>(new Set())
  const [defaultFormOpen, setDefaultFormOpen] = useState(false)
  const [groupFormModal, setGroupFormModal] = useState<GroupFormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<SpreadGroup | null>(null)

  const fetchData = useCallback(async () => {
    if (typeof brandId !== 'number') {
      setDefaultSpread(null)
      setGroups([])
      setPairs([])
      setLoadError(false)
      return
    }
    setLoading(true)
    try {
      const [defaults, groupsData, pairsData] = await Promise.all([
        spreadDefaultApi.list(brandId),
        spreadGroupApi.list(brandId),
        currencyPairApi.list({ brandId, active: true }),
      ])
      setDefaultSpread(defaults[0] ?? null)
      setGroups(groupsData)
      setPairs(pairsData)
      setLoadError(false)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [brandId, showToast])

  const fetchPendingIds = useCallback(async () => {
    try {
      const [defaultRequests, groupRequests] = await Promise.all([
        auditApi.list({ entityType: 'SPREAD_DEFAULT', status: 'PENDING' }),
        auditApi.list({ entityType: 'SPREAD_GROUP', status: 'PENDING' }),
      ])
      setDefaultPendingIds(
        new Set(
          defaultRequests.filter((request) => request.entityId !== null).map((request) => request.entityId as number),
        ),
      )
      setGroupPendingIds(
        new Set(
          groupRequests.filter((request) => request.entityId !== null).map((request) => request.entityId as number),
        ),
      )
    } catch {
      // Non-critical — leave the previous badge state rather than adding another
      // error toast on top of the main data's own error handling.
    }
  }, [])

  const refresh = useCallback(async () => {
    await Promise.all([fetchData(), fetchPendingIds()])
  }, [fetchData, fetchPendingIds])

  useEffect(() => {
    refresh()
  }, [refresh])

  useEffect(() => {
    brandApi.list().then(setBrands).catch(() => {})
  }, [])

  useEffect(() => {
    if (brandId === null && brands.length > 0) {
      const first = brands.find((brand) => brand.active) ?? brands[0]
      setBrandId(first.id)
    }
  }, [brands, brandId])

  const handleBrandChange = (value: number | 'ALL') => setBrandId(value)

  const isDefaultPending = defaultSpread !== null && defaultPendingIds.has(defaultSpread.id)

  const handleEditDefault = () => setDefaultFormOpen(true)
  const closeDefaultForm = () => setDefaultFormOpen(false)

  const handleDefaultSubmit = async (input: SpreadDefaultInput) => {
    if (!defaultSpread) {
      return
    }
    try {
      await spreadDefaultApi.update(defaultSpread.id, input)
      closeDefaultForm()
      showToast('已送出預設點差修改申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        closeDefaultForm()
        showToast(PENDING_DUPLICATE_MESSAGE)
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 400) {
        // Let the modal show its own inline error and stay open.
        throw error
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  const handleAddGroup = () => setGroupFormModal({ mode: 'create', group: null })
  const handleEditGroup = (group: SpreadGroup) => setGroupFormModal({ mode: 'edit', group })
  const closeGroupFormModal = () => setGroupFormModal(null)

  const handleGroupSubmit = async (input: SpreadGroupInput) => {
    if (!groupFormModal) {
      return
    }
    try {
      if (groupFormModal.mode === 'edit' && groupFormModal.group) {
        await spreadGroupApi.update(groupFormModal.group.id, input)
        closeGroupFormModal()
        showToast('已送出點差群組修改申請，待審核', 'success')
      } else {
        await spreadGroupApi.create(input)
        closeGroupFormModal()
        showToast('已送出新增點差群組申請，待審核', 'success')
      }
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        if (isPendingDuplicateConflict(error)) {
          closeGroupFormModal()
          showToast(PENDING_DUPLICATE_MESSAGE)
          await refresh()
          return
        }
        // Live-duplicate name: let the modal show its own inline field error and stay open.
        throw error
      }
      if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
        // Should not normally occur — the assigner only offers valid pairs for the
        // brand — but defensively close/refetch rather than assuming success.
        closeGroupFormModal()
        showToast(NETWORK_ERROR_MESSAGE)
        await refresh()
        return
      }
      showToast(NETWORK_ERROR_MESSAGE)
    }
  }

  const handleDeleteGroup = (group: SpreadGroup) => setDeleteTarget(group)
  const cancelDeleteGroup = () => setDeleteTarget(null)

  const confirmDeleteGroup = async () => {
    if (!deleteTarget) {
      return
    }
    try {
      await spreadGroupApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      showToast('已送出點差群組刪除申請，待審核', 'success')
      await refresh()
    } catch (error) {
      setDeleteTarget(null)
      if (error instanceof ApiError && error.status === 409) {
        showToast(PENDING_DUPLICATE_MESSAGE)
      } else {
        showToast(NETWORK_ERROR_MESSAGE)
      }
      await refresh()
    }
  }

  const brandNotSelected = typeof brandId !== 'number'

  return (
    <div className="spread-page">
      <h1 className="page-title">點差管理</h1>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label" htmlFor="spread-brand-filter">
              品牌
            </label>
            <BrandFilter
              id="spread-brand-filter"
              brands={brands}
              value={brandId ?? 'ALL'}
              onChange={handleBrandChange}
            />
          </div>
        </div>
      </div>

      {brandNotSelected ? (
        <div className="table-empty">請選擇品牌</div>
      ) : loading ? (
        <div className="table-empty" role="status">
          載入中...
        </div>
      ) : loadError ? (
        <div className="table-empty table-error">
          <p>資料載入失敗</p>
          <button type="button" className="btn btn-secondary" onClick={refresh}>
            重試
          </button>
        </div>
      ) : (
        <>
          <div className="search-table-card spread-default-card">
            <div className="search-table-header">
              <div className="search-table-title">
                <span>預設點差</span>
              </div>
            </div>
            <div className="spread-default-body">
              {defaultSpread ? (
                <>
                  <div className="spread-default-values">
                    <span>入金：{defaultSpread.depositSpread}</span>
                    <span>出金：{defaultSpread.withdrawSpread}</span>
                    {isDefaultPending && (
                      <span className="status-badge status-badge--pending">
                        <span className="status-dot" />
                        審核中
                      </span>
                    )}
                  </div>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={handleEditDefault}
                    disabled={isDefaultPending}
                  >
                    編輯
                  </button>
                </>
              ) : (
                <span className="spread-default-empty">此品牌尚未設定預設點差</span>
              )}
            </div>
          </div>

          <div className="search-table-card">
            <div className="search-table-header">
              <div className="search-table-title">
                <span>客制點差群組</span>
              </div>
              <button type="button" className="btn btn-primary" onClick={handleAddGroup}>
                +新增群組
              </button>
            </div>

            <SpreadGroupTable
              groups={groups}
              pendingIds={groupPendingIds}
              onEdit={handleEditGroup}
              onDelete={handleDeleteGroup}
            />

            <div className="table-footer">
              <div className="total-count">Total {groups.length} items</div>
            </div>
          </div>
        </>
      )}

      {defaultFormOpen && defaultSpread && (
        <SpreadDefaultFormModal
          initial={defaultSpread}
          onClose={closeDefaultForm}
          onSubmit={handleDefaultSubmit}
        />
      )}

      {groupFormModal && typeof brandId === 'number' && (
        <SpreadGroupFormModal
          mode={groupFormModal.mode}
          initial={groupFormModal.group ?? undefined}
          brandId={brandId}
          availablePairs={pairs}
          groups={groups}
          onClose={closeGroupFormModal}
          onSubmit={handleGroupSubmit}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          message={`確定要送出刪除點差群組「${deleteTarget.name}」的申請嗎？核准後，其幣種對將回復為預設點差。`}
          onConfirm={confirmDeleteGroup}
          onCancel={cancelDeleteGroup}
        />
      )}
    </div>
  )
}
