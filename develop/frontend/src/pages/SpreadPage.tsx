import { useCallback, useEffect, useRef, useState } from 'react'
import { spreadDefaultApi, spreadGroupApi } from '../api/spreadApi'
import { brandApi } from '../api/brandApi'
import { currencyPairApi } from '../api/currencyPairApi'
import { auditApi } from '../audit/auditApi'
import { registerDiffRenderer } from '../audit/diffRegistry'
import { renderSpreadDefaultDiff } from '../components/SpreadDefaultDiff'
import { renderSpreadGroupDiff } from '../components/SpreadGroupDiff'
import { ApiError } from '../api/client'
import { BrandFilter } from '../components/BrandFilter'
import { SpreadDefaultFormModal } from '../components/SpreadDefaultFormModal'
import { SpreadGroupFormModal } from '../components/SpreadGroupFormModal'
import { SpreadGroupTable } from '../components/SpreadGroupTable'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/ToastProvider'
import type { Brand } from '../types/brand'
import type { CurrencyPair } from '../types/currencyPair'
import type { SpreadDefault, SpreadDefaultInput, SpreadGroup, SpreadGroupInput } from '../types/spread'
import './SpreadPage.css'

// Registers this feature's diff renderers with the generic audit module, so
// the Audit page (`/audit-requests`) can render SPREAD_DEFAULT/SPREAD_GROUP
// requests with the proper labeled before/after layout instead of the
// generic fallback. Runs once, at module load, as soon as this page module
// is imported (App.tsx imports it eagerly alongside the route
// registration) — well before the Audit page can be visited. See
// specs/frontend/spread.md.
registerDiffRenderer('SPREAD_DEFAULT', renderSpreadDefaultDiff)
registerDiffRenderer('SPREAD_GROUP', renderSpreadGroupDiff)

type GroupFormModalState = { mode: 'create' } | { mode: 'edit'; group: SpreadGroup } | null

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const PENDING_DUPLICATE_MESSAGE = '此項目已有待審核的異動申請'
const GROUP_NOT_FOUND_MESSAGE = '點差群組不存在，請重新整理頁面'
const LIVE_DUPLICATE_ERROR = 'Spread group name already exists for this brand'

function formatSpread(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return Number(value.toFixed(8)).toString()
}

/** A 409 whose message isn't the "live duplicate" case is a pending-request conflict. */
function isPendingDuplicateConflict(error: ApiError): boolean {
  return error.body?.error !== LIVE_DUPLICATE_ERROR
}

export function SpreadPage() {
  const { showToast } = useToast()
  const [brands, setBrands] = useState<Brand[]>([])
  // `null` means "not yet auto-selected" — distinct from 'ALL' (an explicit,
  // if unusual, user re-selection of BrandFilter's built-in "All" option).
  // Keeping these separate avoids a race on mount where the initial 'ALL'
  // guard in fetchData could otherwise clear out data that a
  // near-simultaneous brand-specific fetch had just set.
  const [brandId, setBrandId] = useState<number | 'ALL' | null>(null)
  const autoSelectDone = useRef(false)

  const [defaultSpread, setDefaultSpread] = useState<SpreadDefault | null>(null)
  const [groups, setGroups] = useState<SpreadGroup[]>([])
  const [pairs, setPairs] = useState<CurrencyPair[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  const [pendingDefaultIds, setPendingDefaultIds] = useState<Set<number>>(new Set())
  const [pendingGroupIds, setPendingGroupIds] = useState<Set<number>>(new Set())

  const [defaultFormOpen, setDefaultFormOpen] = useState(false)
  const [groupFormModal, setGroupFormModal] = useState<GroupFormModalState>(null)
  const [deleteTarget, setDeleteTarget] = useState<SpreadGroup | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)

  useEffect(() => {
    brandApi.list().then(setBrands).catch(() => showToast(NETWORK_ERROR_MESSAGE))
  }, [showToast])

  // Defaults to the first active brand (falling back to the first brand at
  // all) rather than 'ALL', since this page always operates on exactly one
  // brand — every spread value and group is brand-specific. Runs once, the
  // first time brands load, so a deliberate later re-selection of 'ALL' (via
  // BrandFilter's built-in "All" option) isn't overridden.
  useEffect(() => {
    if (!autoSelectDone.current && brands.length > 0) {
      autoSelectDone.current = true
      const firstActive = brands.find((brand) => brand.active) ?? brands[0]
      setBrandId(firstActive.id)
    }
  }, [brands])

  const fetchData = useCallback(async () => {
    if (brandId === null || brandId === 'ALL') {
      setDefaultSpread(null)
      setGroups([])
      setPairs([])
      setLoadError(false)
      // Leave `loading` as-is: while `null` (still auto-selecting on mount)
      // the initial `true` keeps the 載入中… state showing; once the user
      // has explicitly chosen 'ALL', the dedicated "請選擇品牌" placeholder
      // is shown instead of the loading/table area, so `loading` no longer
      // matters for that branch.
      setLoading(brandId === null)
      return
    }
    setLoading(true)
    setLoadError(false)
    try {
      const [defaults, groupList, pairList] = await Promise.all([
        spreadDefaultApi.list(brandId),
        spreadGroupApi.list(brandId),
        currencyPairApi.list({ brandId, active: true }),
      ])
      setDefaultSpread(defaults[0] ?? null)
      setGroups(groupList)
      setPairs(pairList)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [brandId, showToast])

  // Rows/sections with a PENDING request against them are marked and their
  // mutating actions disabled, to avoid the "already has a pending request"
  // 409 in the common case — same pattern as CurrencyPairPage's
  // fetchPendingIds. Fetched independently of the brand filter's data so it
  // always reflects the full set of pending requests for both entity types.
  const fetchPendingIds = useCallback(async () => {
    try {
      const [defaultRequests, groupRequests] = await Promise.all([
        auditApi.list({ entityType: 'SPREAD_DEFAULT', status: 'PENDING' }),
        auditApi.list({ entityType: 'SPREAD_GROUP', status: 'PENDING' }),
      ])
      setPendingDefaultIds(
        new Set(
          defaultRequests.filter((request) => request.entityId !== null).map((request) => request.entityId as number),
        ),
      )
      setPendingGroupIds(
        new Set(
          groupRequests.filter((request) => request.entityId !== null).map((request) => request.entityId as number),
        ),
      )
    } catch {
      // Non-critical for the page's core functionality — leave the previous
      // badge state as-is rather than surfacing another error toast.
    }
  }, [])

  const refresh = useCallback(async () => {
    await Promise.all([fetchData(), fetchPendingIds()])
  }, [fetchData, fetchPendingIds])

  useEffect(() => {
    refresh()
  }, [refresh])

  async function handleDefaultSubmit(input: SpreadDefaultInput) {
    if (!defaultSpread) return
    try {
      await spreadDefaultApi.update(defaultSpread.id, input)
      setDefaultFormOpen(false)
      showToast('已送出預設點差修改申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        showToast(PENDING_DUPLICATE_MESSAGE)
        setDefaultFormOpen(false)
        await refresh()
        return
      }
      throw error
    }
  }

  async function handleCreateGroupSubmit(input: SpreadGroupInput) {
    try {
      await spreadGroupApi.create(input)
      setGroupFormModal(null)
      showToast('已送出新增點差群組申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
        // Should not normally occur since the panel only offers valid pairs
        // for the selected brand — defensive fallback, mirroring
        // CurrencyPairPage's own 404-handling pattern.
        showToast(NETWORK_ERROR_MESSAGE)
        setGroupFormModal(null)
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 409 && isPendingDuplicateConflict(error)) {
        showToast(PENDING_DUPLICATE_MESSAGE)
        setGroupFormModal(null)
        await refresh()
        return
      }
      throw error
    }
  }

  async function handleEditGroupSubmit(id: number, input: SpreadGroupInput) {
    try {
      await spreadGroupApi.update(id, input)
      setGroupFormModal(null)
      showToast('已送出點差群組修改申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && (error.status === 400 || error.status === 404)) {
        showToast(NETWORK_ERROR_MESSAGE)
        setGroupFormModal(null)
        await refresh()
        return
      }
      if (error instanceof ApiError && error.status === 409 && isPendingDuplicateConflict(error)) {
        showToast(PENDING_DUPLICATE_MESSAGE)
        setGroupFormModal(null)
        await refresh()
        return
      }
      throw error
    }
  }

  async function handleConfirmDeleteGroup() {
    if (!deleteTarget) return
    setDeleteBusy(true)
    try {
      await spreadGroupApi.remove(deleteTarget.id)
      setDeleteTarget(null)
      showToast('已送出點差群組刪除申請，待審核', 'success')
      await refresh()
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        showToast(GROUP_NOT_FOUND_MESSAGE)
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

  const isDefaultPending = defaultSpread !== null && pendingDefaultIds.has(defaultSpread.id)

  return (
    <div className="spread-page">
      <div className="page-title">
        <h1>點差管理</h1>
      </div>

      <div className="filter-card">
        <div className="filter-row">
          <div className="filter-group">
            <label className="filter-label">品牌</label>
            <BrandFilter brands={brands} value={brandId ?? 'ALL'} onChange={setBrandId} />
          </div>
        </div>
      </div>

      {brandId === 'ALL' && <div className="table-empty">請選擇品牌</div>}

      {brandId !== 'ALL' && (
        <>
          <div className="spread-default-card">
            <div className="spread-default-card-header">
              <div className="search-table-title">預設點差</div>
            </div>
            <div className="spread-default-card-body">
              {loading && <div className="table-empty">載入中…</div>}
              {!loading && loadError && (
                <div className="table-empty spread-page-status--error">
                  資料載入失敗
                  <button type="button" className="btn btn-link" onClick={refresh}>
                    重試
                  </button>
                </div>
              )}
              {!loading && !loadError && defaultSpread && (
                <div className="spread-default-values">
                  <div className="spread-default-value">
                    <span className="spread-default-value-label">入金</span>
                    <span className="spread-default-value-number">{formatSpread(defaultSpread.depositSpread)}</span>
                  </div>
                  <div className="spread-default-value">
                    <span className="spread-default-value-label">出金</span>
                    <span className="spread-default-value-number">{formatSpread(defaultSpread.withdrawSpread)}</span>
                  </div>
                  {isDefaultPending && <span className="pending-badge">審核中</span>}
                  <button
                    type="button"
                    className="btn btn-secondary"
                    onClick={() => setDefaultFormOpen(true)}
                    disabled={isDefaultPending}
                  >
                    編輯
                  </button>
                </div>
              )}
              {!loading && !loadError && !defaultSpread && (
                <div className="table-empty">此品牌尚未設定預設點差</div>
              )}
            </div>
          </div>

          <div className="search-table-card">
            <div className="search-table-header">
              <div className="search-table-title">客制點差群組</div>
              <button type="button" className="btn btn-primary" onClick={() => setGroupFormModal({ mode: 'create' })}>
                + 新增群組
              </button>
            </div>

            <div className="spread-group-table-wrapper">
              {loading && <div className="table-empty">載入中…</div>}
              {!loading && loadError && (
                <div className="table-empty spread-page-status--error">
                  資料載入失敗
                  <button type="button" className="btn btn-link" onClick={refresh}>
                    重試
                  </button>
                </div>
              )}
              {!loading && !loadError && (
                <SpreadGroupTable
                  groups={groups}
                  pendingIds={pendingGroupIds}
                  onEdit={(group) => setGroupFormModal({ mode: 'edit', group })}
                  onDelete={(group) => setDeleteTarget(group)}
                />
              )}
            </div>

            <div className="table-footer">
              <div className="total-count">Total {groups.length} items</div>
            </div>
          </div>
        </>
      )}

      {defaultFormOpen && defaultSpread && (
        <SpreadDefaultFormModal
          spreadDefault={defaultSpread}
          onSubmit={handleDefaultSubmit}
          onClose={() => setDefaultFormOpen(false)}
        />
      )}

      {groupFormModal?.mode === 'create' && typeof brandId === 'number' && (
        <SpreadGroupFormModal
          mode="create"
          brandId={brandId}
          availablePairs={pairs}
          groups={groups}
          onSubmit={handleCreateGroupSubmit}
          onClose={() => setGroupFormModal(null)}
        />
      )}

      {groupFormModal?.mode === 'edit' && typeof brandId === 'number' && (
        <SpreadGroupFormModal
          mode="edit"
          initial={groupFormModal.group}
          brandId={brandId}
          availablePairs={pairs}
          groups={groups}
          onSubmit={(input) => handleEditGroupSubmit(groupFormModal.group.id, input)}
          onClose={() => setGroupFormModal(null)}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          title="刪除點差群組"
          message={`確定要送出刪除點差群組「${deleteTarget.name}」的申請嗎？核准後，其幣種對將回復為預設點差。`}
          onConfirm={handleConfirmDeleteGroup}
          onCancel={() => setDeleteTarget(null)}
          busy={deleteBusy}
        />
      )}
    </div>
  )
}
