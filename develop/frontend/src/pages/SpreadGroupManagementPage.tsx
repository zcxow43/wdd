import { useCallback, useEffect, useState } from 'react'
import { fetchAuditRequests } from '../api/audit'
import { type Brand, fetchBrands } from '../api/brands'
import {
  type CurrencyPair,
  fetchCurrencyPairsByBrand,
} from '../api/currencyPairDefinitions'
import { ApiError } from '../api/http'
import {
  type BrandSpread,
  type EffectiveSpread,
  type SpreadGroup,
  type SpreadGroupDetail,
  addSpreadGroupMembers,
  createSpreadGroup,
  deleteSpreadGroup,
  fetchBrandSpread,
  fetchEffectiveSpreads,
  fetchSpreadGroup,
  fetchSpreadGroups,
  removeSpreadGroupMember,
  updateBrandSpread,
  updateSpreadGroup,
} from '../api/spreads'
import Toast from '../components/Toast'
import './SpreadGroupManagementPage.css'

const SPREAD_ERROR_MESSAGE = '請輸入 0 或以上的數值，小數點後最多 8 位'
const SPREAD_NUMBER_PATTERN = /^\d+(\.\d+)?$/
const SUBMITTED_TOAST_MESSAGE = '已送出審核，核准後才會生效'

interface DefaultFormState {
  depositSpread: string
  withdrawalSpread: string
}

interface DefaultFormErrors {
  depositSpread?: string
  withdrawalSpread?: string
}

interface GroupFormState {
  name: string
  depositSpread: string
  withdrawalSpread: string
}

interface GroupFormErrors {
  name?: string
  depositSpread?: string
  withdrawalSpread?: string
}

const EMPTY_GROUP_FORM: GroupFormState = {
  name: '',
  depositSpread: '0',
  withdrawalSpread: '0',
}

function validateSpreadValue(raw: string): string | undefined {
  const trimmed = raw.trim()
  if (trimmed === '' || !SPREAD_NUMBER_PATTERN.test(trimmed)) {
    return SPREAD_ERROR_MESSAGE
  }
  const dotIndex = trimmed.indexOf('.')
  if (dotIndex !== -1 && trimmed.length - dotIndex - 1 > 8) {
    return SPREAD_ERROR_MESSAGE
  }
  return undefined
}

function validateDefaultForm(form: DefaultFormState): DefaultFormErrors {
  const errors: DefaultFormErrors = {}
  const depositError = validateSpreadValue(form.depositSpread)
  if (depositError) {
    errors.depositSpread = depositError
  }
  const withdrawalError = validateSpreadValue(form.withdrawalSpread)
  if (withdrawalError) {
    errors.withdrawalSpread = withdrawalError
  }
  return errors
}

function validateGroupForm(form: GroupFormState): GroupFormErrors {
  const errors: GroupFormErrors = {}
  const trimmedName = form.name.trim()
  if (trimmedName === '' || trimmedName.length > 50) {
    errors.name = '群組名稱為必填，長度需在 1–50 字元之間'
  }
  const depositError = validateSpreadValue(form.depositSpread)
  if (depositError) {
    errors.depositSpread = depositError
  }
  const withdrawalError = validateSpreadValue(form.withdrawalSpread)
  if (withdrawalError) {
    errors.withdrawalSpread = withdrawalError
  }
  return errors
}

function toDefaultForm(spread: BrandSpread): DefaultFormState {
  return {
    depositSpread: String(spread.depositSpread),
    withdrawalSpread: String(spread.withdrawalSpread),
  }
}

function memberBadgeClass(memberCount: number): string {
  return memberCount > 0
    ? 'sgm-badge sgm-badge--member-some'
    : 'sgm-badge sgm-badge--member-zero'
}

function SpreadGroupManagementPage() {
  const [brands, setBrands] = useState<Brand[] | null>(null)
  const [brandsLoading, setBrandsLoading] = useState(true)
  const [brandsError, setBrandsError] = useState(false)
  const [selectedBrandId, setSelectedBrandId] = useState<number | null>(null)

  // Section 1: 預設點差
  const [defaultSpread, setDefaultSpread] = useState<BrandSpread | null>(null)
  const [defaultLoading, setDefaultLoading] = useState(false)
  const [defaultError, setDefaultError] = useState(false)
  const [defaultForm, setDefaultForm] = useState<DefaultFormState>({
    depositSpread: '',
    withdrawalSpread: '',
  })
  const [defaultFormErrors, setDefaultFormErrors] =
    useState<DefaultFormErrors>({})
  const [defaultSaving, setDefaultSaving] = useState(false)
  const [defaultPending, setDefaultPending] = useState(false)

  // Pending-review markers, from GET /api/audit-requests?status=PENDING&brandId=
  // — reloaded whenever the selected brand changes, and updated locally the
  // moment a write on this page itself gets accepted (202).
  const [pendingGroupIds, setPendingGroupIds] = useState<Set<number>>(
    new Set(),
  )

  // Section 2: 點差群組
  const [groups, setGroups] = useState<SpreadGroup[] | null>(null)
  const [groupsLoading, setGroupsLoading] = useState(false)
  const [groupsError, setGroupsError] = useState(false)

  const [groupModalMode, setGroupModalMode] = useState<
    'create' | 'edit' | null
  >(null)
  const [editingGroup, setEditingGroup] = useState<SpreadGroup | null>(null)
  const [groupForm, setGroupForm] = useState<GroupFormState>(EMPTY_GROUP_FORM)
  const [groupFormErrors, setGroupFormErrors] = useState<GroupFormErrors>({})
  const [groupSubmitting, setGroupSubmitting] = useState(false)

  const [deletingGroup, setDeletingGroup] = useState<SpreadGroup | null>(null)
  const [groupDeleting, setGroupDeleting] = useState(false)

  // Section 3: 管理成員
  const [managingGroup, setManagingGroup] = useState<SpreadGroup | null>(null)
  const [memberDetail, setMemberDetail] = useState<SpreadGroupDetail | null>(
    null,
  )
  const [memberLoading, setMemberLoading] = useState(false)
  const [memberError, setMemberError] = useState(false)
  const [removingMemberId, setRemovingMemberId] = useState<number | null>(
    null,
  )

  const [brandPairsForPicker, setBrandPairsForPicker] = useState<
    CurrencyPair[] | null
  >(null)
  const [pickerLoading, setPickerLoading] = useState(false)
  const [pickerError, setPickerError] = useState(false)
  const [selectedPairIds, setSelectedPairIds] = useState<Set<number>>(
    new Set(),
  )
  const [joining, setJoining] = useState(false)
  // Local, modal-scoped marker for a member row whose 移除 was just
  // submitted this session (member list/成員數 themselves don't change
  // until approval, so this is the only signal that row has a pending
  // request in flight).
  const [pendingMemberIds, setPendingMemberIds] = useState<Set<number>>(
    new Set(),
  )

  // Section 4: 生效點差總覽
  const [effectiveSpreads, setEffectiveSpreads] = useState<
    EffectiveSpread[] | null
  >(null)
  const [effectiveLoading, setEffectiveLoading] = useState(false)
  const [effectiveError, setEffectiveError] = useState(false)

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

  const loadDefaultSpread = useCallback((brandId: number) => {
    setDefaultLoading(true)
    setDefaultError(false)
    fetchBrandSpread(brandId)
      .then((data) => {
        setDefaultSpread(data)
        setDefaultForm(toDefaultForm(data))
        setDefaultFormErrors({})
      })
      .catch(() => {
        setDefaultError(true)
      })
      .finally(() => {
        setDefaultLoading(false)
      })
  }, [])

  const loadGroups = useCallback((brandId: number) => {
    setGroupsLoading(true)
    setGroupsError(false)
    fetchSpreadGroups(brandId)
      .then((data) => {
        setGroups(data)
      })
      .catch(() => {
        setGroupsError(true)
      })
      .finally(() => {
        setGroupsLoading(false)
      })
  }, [])

  const loadEffective = useCallback((brandId: number) => {
    setEffectiveLoading(true)
    setEffectiveError(false)
    fetchEffectiveSpreads(brandId)
      .then((data) => {
        setEffectiveSpreads(data)
      })
      .catch(() => {
        setEffectiveError(true)
      })
      .finally(() => {
        setEffectiveLoading(false)
      })
  }, [])

  // Marks anything with a change already awaiting review: the brand's own
  // 預設點差 (BRAND_SPREAD, entityId === brandId) and any group with either
  // a group-level (SPREAD_GROUP) or membership (SPREAD_GROUP_MEMBER) change
  // pending, both keyed by the group's id.
  const loadPendingRequests = useCallback((brandId: number) => {
    fetchAuditRequests({ status: 'PENDING', brandId })
      .then((requests) => {
        let brandSpreadPending = false
        const groupIds = new Set<number>()
        requests.forEach((request) => {
          if (
            request.entityType === 'BRAND_SPREAD' &&
            request.entityId === brandId
          ) {
            brandSpreadPending = true
          } else if (
            (request.entityType === 'SPREAD_GROUP' ||
              request.entityType === 'SPREAD_GROUP_MEMBER') &&
            request.entityId !== null
          ) {
            groupIds.add(request.entityId)
          }
        })
        setDefaultPending(brandSpreadPending)
        setPendingGroupIds(groupIds)
      })
      .catch(() => {
        // Non-critical secondary lookup — leave previous markers in place
        // rather than blocking the page on it.
      })
  }, [])

  useEffect(() => {
    if (selectedBrandId !== null) {
      loadDefaultSpread(selectedBrandId)
      loadGroups(selectedBrandId)
      loadEffective(selectedBrandId)
      loadPendingRequests(selectedBrandId)
    }
  }, [
    selectedBrandId,
    loadDefaultSpread,
    loadGroups,
    loadEffective,
    loadPendingRequests,
  ])

  // ---- Section 1: 預設點差 ----
  const handleDefaultFieldChange = (
    field: keyof DefaultFormState,
    value: string,
  ) => {
    setDefaultForm((current) => ({ ...current, [field]: value }))
  }

  const handleDefaultSave = () => {
    const errors = validateDefaultForm(defaultForm)
    setDefaultFormErrors(errors)
    if (Object.keys(errors).length > 0 || selectedBrandId === null) {
      return
    }

    setDefaultSaving(true)
    updateBrandSpread(selectedBrandId, {
      depositSpread: Number(defaultForm.depositSpread.trim()),
      withdrawalSpread: Number(defaultForm.withdrawalSpread.trim()),
    })
      .then(() => {
        // 202: nothing is applied yet — the card reverts to the
        // currently-effective values (still held in `defaultSpread`,
        // unchanged by this submission) and is marked awaiting review.
        if (defaultSpread) {
          setDefaultForm(toDefaultForm(defaultSpread))
        }
        setDefaultFormErrors({})
        setDefaultPending(true)
        setToastMessage(SUBMITTED_TOAST_MESSAGE)
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 400) {
          setDefaultFormErrors({
            depositSpread: SPREAD_ERROR_MESSAGE,
            withdrawalSpread: SPREAD_ERROR_MESSAGE,
          })
        } else if (error instanceof ApiError && error.status === 409) {
          if (defaultSpread) {
            setDefaultForm(toDefaultForm(defaultSpread))
          }
          setToastMessage('此品牌的預設點差已有待審核的變更')
        } else {
          if (defaultSpread) {
            setDefaultForm(toDefaultForm(defaultSpread))
          }
          setToastMessage('更新失敗，請稍後再試')
        }
      })
      .finally(() => {
        setDefaultSaving(false)
      })
  }

  // ---- Section 2: 點差群組 ----
  const openCreateGroupModal = () => {
    setGroupModalMode('create')
    setEditingGroup(null)
    setGroupForm(EMPTY_GROUP_FORM)
    setGroupFormErrors({})
  }

  const openEditGroupModal = (group: SpreadGroup) => {
    setGroupModalMode('edit')
    setEditingGroup(group)
    setGroupForm({
      name: group.name,
      depositSpread: String(group.depositSpread),
      withdrawalSpread: String(group.withdrawalSpread),
    })
    setGroupFormErrors({})
  }

  const closeGroupModal = () => {
    setGroupModalMode(null)
    setEditingGroup(null)
    setGroupForm(EMPTY_GROUP_FORM)
    setGroupFormErrors({})
  }

  const handleGroupFieldChange = (
    field: keyof GroupFormState,
    value: string,
  ) => {
    setGroupForm((current) => ({ ...current, [field]: value }))
  }

  const isGroupEdit = groupModalMode === 'edit'

  const handleGroupSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const errors = validateGroupForm(groupForm)
    setGroupFormErrors(errors)
    if (Object.keys(errors).length > 0 || selectedBrandId === null) {
      return
    }

    setGroupSubmitting(true)
    const trimmedName = groupForm.name.trim()
    const depositSpread = Number(groupForm.depositSpread.trim())
    const withdrawalSpread = Number(groupForm.withdrawalSpread.trim())

    if (isGroupEdit && editingGroup) {
      const groupId = editingGroup.id
      updateSpreadGroup(groupId, {
        name: trimmedName,
        depositSpread,
        withdrawalSpread,
      })
        .then(() => {
          // 202: the row's values are left exactly as they were — only a
          // pending-review marker is added — until the request is approved.
          setPendingGroupIds((current) => new Set(current).add(groupId))
          closeGroupModal()
          setToastMessage(SUBMITTED_TOAST_MESSAGE)
        })
        .catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 409) {
            const body = error.body as { error?: string } | undefined
            if (body?.error) {
              // Name conflict: SpreadGroupNameConflictException (`error` key).
              setGroupFormErrors({ name: '此品牌已有相同名稱的群組' })
            } else {
              // Already-pending conflict: AuditRequestConflictException
              // (`message` key, no `error` key).
              setPendingGroupIds((current) => new Set(current).add(groupId))
              closeGroupModal()
              setToastMessage('此群組已有待審核的變更')
            }
          } else {
            setToastMessage('儲存失敗，請稍後再試')
          }
        })
        .finally(() => {
          setGroupSubmitting(false)
        })
    } else {
      createSpreadGroup({
        brandId: selectedBrandId,
        name: trimmedName,
        depositSpread,
        withdrawalSpread,
      })
        .then(() => {
          // 202: the group does not exist until the request is approved —
          // no row is added.
          closeGroupModal()
          setToastMessage(SUBMITTED_TOAST_MESSAGE)
        })
        .catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 409) {
            setGroupFormErrors({ name: '此品牌已有相同名稱的群組' })
          } else {
            setToastMessage('儲存失敗，請稍後再試')
          }
        })
        .finally(() => {
          setGroupSubmitting(false)
        })
    }
  }

  const confirmDeleteGroup = () => {
    if (!deletingGroup) {
      return
    }
    const target = deletingGroup
    setGroupDeleting(true)
    deleteSpreadGroup(target.id)
      .then(() => {
        // 202: the row stays on screen, marked awaiting review, rather
        // than disappearing — the group is only actually removed once the
        // request is approved.
        setPendingGroupIds((current) => new Set(current).add(target.id))
        setDeletingGroup(null)
        setToastMessage(SUBMITTED_TOAST_MESSAGE)
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 409) {
          setPendingGroupIds((current) => new Set(current).add(target.id))
          setToastMessage('此群組已有待審核的變更')
        } else {
          setToastMessage('刪除失敗，請稍後再試')
        }
        setDeletingGroup(null)
      })
      .finally(() => {
        setGroupDeleting(false)
      })
  }

  // ---- Section 3: 管理成員 ----
  const loadMemberDetail = useCallback((groupId: number) => {
    setMemberLoading(true)
    setMemberError(false)
    fetchSpreadGroup(groupId)
      .then((data) => {
        setMemberDetail(data)
      })
      .catch(() => {
        setMemberError(true)
      })
      .finally(() => {
        setMemberLoading(false)
      })
  }, [])

  const loadPickerPairs = useCallback((brandId: number) => {
    setPickerLoading(true)
    setPickerError(false)
    fetchCurrencyPairsByBrand(brandId)
      .then((data) => {
        setBrandPairsForPicker(data)
      })
      .catch(() => {
        setPickerError(true)
      })
      .finally(() => {
        setPickerLoading(false)
      })
  }, [])

  const openMemberModal = (group: SpreadGroup) => {
    setManagingGroup(group)
    setMemberDetail(null)
    setSelectedPairIds(new Set())
    setPendingMemberIds(new Set())
    loadMemberDetail(group.id)
    if (selectedBrandId !== null) {
      loadPickerPairs(selectedBrandId)
    }
  }

  const closeMemberModal = () => {
    setManagingGroup(null)
    setMemberDetail(null)
    setMemberError(false)
    setBrandPairsForPicker(null)
    setPickerError(false)
    setSelectedPairIds(new Set())
    setPendingMemberIds(new Set())
  }

  const handleRemoveMember = (currencyPairId: number) => {
    if (!managingGroup || selectedBrandId === null) {
      return
    }
    const groupId = managingGroup.id
    setRemovingMemberId(currencyPairId)
    removeSpreadGroupMember(groupId, currencyPairId)
      .then(() => {
        // 202: the member stays listed and 成員數 is unchanged — the
        // removal only happens on approval. Only a local, modal-scoped
        // 審核中 marker is added to that member row.
        setPendingMemberIds((current) => new Set(current).add(currencyPairId))
        setPendingGroupIds((current) => new Set(current).add(groupId))
        setToastMessage(SUBMITTED_TOAST_MESSAGE)
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 409) {
          setPendingGroupIds((current) => new Set(current).add(groupId))
          setToastMessage('此群組已有待審核的變更')
        } else {
          setToastMessage('移除失敗，請稍後再試')
        }
      })
      .finally(() => {
        setRemovingMemberId(null)
      })
  }

  const togglePairSelection = (pairId: number) => {
    setSelectedPairIds((current) => {
      const next = new Set(current)
      if (next.has(pairId)) {
        next.delete(pairId)
      } else {
        next.add(pairId)
      }
      return next
    })
  }

  const handleJoin = () => {
    if (!managingGroup || selectedBrandId === null || selectedPairIds.size === 0) {
      return
    }
    const groupId = managingGroup.id
    setJoining(true)
    addSpreadGroupMembers(groupId, Array.from(selectedPairIds))
      .then(() => {
        // 202: the batch joins the group only once approved — the member
        // list and 成員數 are intentionally left untouched here.
        setSelectedPairIds(new Set())
        setPendingGroupIds((current) => new Set(current).add(groupId))
        setToastMessage(SUBMITTED_TOAST_MESSAGE)
      })
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 409) {
          const body = error.body as { error?: string } | undefined
          if (body?.error) {
            // Business conflict: a picked pair was assigned to another
            // group between load and submit (SpreadGroupMemberConflictException).
            setToastMessage('部分幣種對已屬於其他群組，請重新整理')
            setSelectedPairIds(new Set())
            loadMemberDetail(groupId)
            loadPickerPairs(selectedBrandId)
          } else {
            // Already-pending conflict: this group already has a pending
            // membership change (AuditRequestConflictException).
            setPendingGroupIds((current) => new Set(current).add(groupId))
            setToastMessage('此群組已有待審核的變更')
          }
        } else {
          setToastMessage('加入失敗，請稍後再試')
        }
      })
      .finally(() => {
        setJoining(false)
      })
  }

  const unassignedPairs =
    brandPairsForPicker?.filter((p) => p.spreadGroupId === null) ?? null

  return (
    <div className="sgm-page">
      <div className="sgm-page__breadcrumb">匯率中心 &gt; 價差群組管理</div>
      <h1 className="sgm-page__title">價差群組管理</h1>

      {brandsLoading && <p className="sgm-page__status">載入中...</p>}

      {!brandsLoading && brandsError && (
        <div className="sgm-page__error">
          <p>載入品牌清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadBrands}>
            重試
          </button>
        </div>
      )}

      {!brandsLoading && !brandsError && brands && (
        <>
          <div className="sgm-brand-selector" role="tablist" aria-label="品牌選擇">
            {brands.map((brand) => (
              <button
                key={brand.id}
                type="button"
                role="tab"
                aria-selected={selectedBrandId === brand.id}
                className={`sgm-brand-selector__item${
                  selectedBrandId === brand.id
                    ? ' sgm-brand-selector__item--selected'
                    : ''
                }`}
                onClick={() => setSelectedBrandId(brand.id)}
              >
                {brand.code}
              </button>
            ))}
          </div>

          {/* Section 1: 預設點差 */}
          <div className="sgm-card">
            <div className="sgm-card__header">
              <h2 className="sgm-card__heading">預設點差</h2>
              {defaultPending && (
                <span className="sgm-badge sgm-badge--pending" title="此項目有待審核的變更，需先完成審核">
                  審核中
                </span>
              )}
            </div>
            {defaultLoading && <p className="sgm-page__status">載入中...</p>}
            {!defaultLoading && defaultError && (
              <div className="sgm-page__error">
                <p>載入預設點差失敗，請稍後再試。</p>
                <button
                  type="button"
                  onClick={() =>
                    selectedBrandId !== null && loadDefaultSpread(selectedBrandId)
                  }
                >
                  重試
                </button>
              </div>
            )}
            {!defaultLoading && !defaultError && defaultSpread && (
              <>
                <div className="sgm-default-form">
                  <div className="sgm-form__field">
                    <label className="sgm-form__label" htmlFor="defaultDeposit">
                      入金點差
                    </label>
                    <input
                      id="defaultDeposit"
                      type="number"
                      className="sgm-form__input"
                      value={defaultForm.depositSpread}
                      disabled={defaultSaving || defaultPending}
                      onChange={(e) =>
                        handleDefaultFieldChange('depositSpread', e.target.value)
                      }
                    />
                    {defaultFormErrors.depositSpread && (
                      <p className="sgm-form__error">
                        {defaultFormErrors.depositSpread}
                      </p>
                    )}
                  </div>
                  <div className="sgm-form__field">
                    <label
                      className="sgm-form__label"
                      htmlFor="defaultWithdrawal"
                    >
                      出金點差
                    </label>
                    <input
                      id="defaultWithdrawal"
                      type="number"
                      className="sgm-form__input"
                      value={defaultForm.withdrawalSpread}
                      disabled={defaultSaving || defaultPending}
                      onChange={(e) =>
                        handleDefaultFieldChange(
                          'withdrawalSpread',
                          e.target.value,
                        )
                      }
                    />
                    {defaultFormErrors.withdrawalSpread && (
                      <p className="sgm-form__error">
                        {defaultFormErrors.withdrawalSpread}
                      </p>
                    )}
                  </div>
                  <button
                    type="button"
                    className="sgm-page__btn sgm-page__btn--primary sgm-form__save-btn"
                    disabled={defaultSaving || defaultPending}
                    onClick={handleDefaultSave}
                  >
                    儲存
                  </button>
                </div>
                <p className="sgm-card__caption">
                  未加入任何群組的品牌幣種對，將套用此預設點差
                </p>
              </>
            )}
          </div>

          {/* Section 2: 點差群組 */}
          <div className="sgm-card">
            <div className="sgm-card__header">
              <h2 className="sgm-card__heading">點差群組</h2>
              <button
                type="button"
                className="sgm-page__btn sgm-page__btn--primary"
                onClick={openCreateGroupModal}
              >
                + 新增群組
              </button>
            </div>

            {groupsLoading && <p className="sgm-page__status">載入中...</p>}

            {!groupsLoading && groupsError && (
              <div className="sgm-page__error">
                <p>載入點差群組失敗，請稍後再試。</p>
                <button
                  type="button"
                  onClick={() =>
                    selectedBrandId !== null && loadGroups(selectedBrandId)
                  }
                >
                  重試
                </button>
              </div>
            )}

            {!groupsLoading && !groupsError && groups && groups.length === 0 && (
              <p className="sgm-empty-state">
                此品牌尚無點差群組，點擊「+ 新增群組」建立第一個
              </p>
            )}

            {!groupsLoading && !groupsError && groups && groups.length > 0 && (
              <table className="sgm-table">
                <thead>
                  <tr>
                    <th>群組名稱</th>
                    <th>入金點差</th>
                    <th>出金點差</th>
                    <th>成員數</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {groups.map((group) => {
                    const isGroupPending = pendingGroupIds.has(group.id)
                    return (
                      <tr key={group.id}>
                        <td className="sgm-table__name-cell">
                          {group.name}
                          {isGroupPending && (
                            <span
                              className="sgm-badge sgm-badge--pending"
                              title="此列有待審核的變更，需先完成審核"
                            >
                              審核中
                            </span>
                          )}
                        </td>
                        <td className="sgm-table__spread-cell">
                          {group.depositSpread}
                        </td>
                        <td className="sgm-table__spread-cell">
                          {group.withdrawalSpread}
                        </td>
                        <td>
                          <span className={memberBadgeClass(group.memberCount)}>
                            {group.memberCount}
                          </span>
                        </td>
                        <td>
                          <div className="sgm-table__actions">
                            <button
                              type="button"
                              className="sgm-page__btn sgm-page__btn--secondary"
                              disabled={isGroupPending}
                              onClick={() => openMemberModal(group)}
                            >
                              管理成員
                            </button>
                            <button
                              type="button"
                              className="sgm-page__btn sgm-page__btn--secondary"
                              disabled={isGroupPending}
                              onClick={() => openEditGroupModal(group)}
                            >
                              編輯
                            </button>
                            <button
                              type="button"
                              className="sgm-page__btn sgm-page__btn--danger"
                              disabled={isGroupPending}
                              onClick={() => setDeletingGroup(group)}
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
          </div>

          {/* Section 4: 生效點差總覽 */}
          <div className="sgm-card">
            <h2 className="sgm-card__heading">生效點差總覽</h2>

            {effectiveLoading && <p className="sgm-page__status">載入中...</p>}

            {!effectiveLoading && effectiveError && (
              <div className="sgm-page__error">
                <p>載入生效點差總覽失敗，請稍後再試。</p>
                <button
                  type="button"
                  onClick={() =>
                    selectedBrandId !== null && loadEffective(selectedBrandId)
                  }
                >
                  重試
                </button>
              </div>
            )}

            {!effectiveLoading &&
              !effectiveError &&
              effectiveSpreads &&
              effectiveSpreads.length === 0 && (
                <p className="sgm-empty-state">此品牌尚無幣種對</p>
              )}

            {!effectiveLoading &&
              !effectiveError &&
              effectiveSpreads &&
              effectiveSpreads.length > 0 && (
                <table className="sgm-table">
                  <thead>
                    <tr>
                      <th>幣種對</th>
                      <th>來源</th>
                      <th>入金點差</th>
                      <th>出金點差</th>
                    </tr>
                  </thead>
                  <tbody>
                    {effectiveSpreads.map((item) => (
                      <tr key={item.currencyPairId}>
                        <td className="sgm-table__code-cell">
                          {item.baseCurrencyCode}/{item.quoteCurrencyCode}
                        </td>
                        <td>
                          <span
                            className={`sgm-badge ${
                              item.source === 'GROUP'
                                ? 'sgm-badge--source-group'
                                : 'sgm-badge--source-default'
                            }`}
                          >
                            {item.source === 'GROUP'
                              ? item.spreadGroupName
                              : '預設'}
                          </span>
                        </td>
                        <td className="sgm-table__spread-cell">
                          {item.depositSpread}
                        </td>
                        <td className="sgm-table__spread-cell">
                          {item.withdrawalSpread}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
          </div>
        </>
      )}

      {/* Section 2 create/edit modal */}
      {groupModalMode && (
        <div className="sgm-modal__overlay">
          <div className="sgm-modal__card">
            <h2 className="sgm-modal__title">
              {isGroupEdit ? '編輯點差群組' : '新增點差群組'}
            </h2>
            <form onSubmit={handleGroupSubmit}>
              <div className="sgm-modal-form__field">
                <label className="sgm-form__label" htmlFor="groupName">
                  群組名稱
                </label>
                <input
                  id="groupName"
                  type="text"
                  className="sgm-form__input sgm-modal-form__input"
                  value={groupForm.name}
                  disabled={groupSubmitting}
                  onChange={(e) =>
                    handleGroupFieldChange('name', e.target.value)
                  }
                />
                {groupFormErrors.name && (
                  <p className="sgm-form__error">{groupFormErrors.name}</p>
                )}
              </div>
              <div className="sgm-modal-form__field">
                <label className="sgm-form__label" htmlFor="groupDeposit">
                  入金點差
                </label>
                <input
                  id="groupDeposit"
                  type="number"
                  className="sgm-form__input sgm-modal-form__input"
                  value={groupForm.depositSpread}
                  disabled={groupSubmitting}
                  onChange={(e) =>
                    handleGroupFieldChange('depositSpread', e.target.value)
                  }
                />
                {groupFormErrors.depositSpread && (
                  <p className="sgm-form__error">
                    {groupFormErrors.depositSpread}
                  </p>
                )}
              </div>
              <div className="sgm-modal-form__field">
                <label className="sgm-form__label" htmlFor="groupWithdrawal">
                  出金點差
                </label>
                <input
                  id="groupWithdrawal"
                  type="number"
                  className="sgm-form__input sgm-modal-form__input"
                  value={groupForm.withdrawalSpread}
                  disabled={groupSubmitting}
                  onChange={(e) =>
                    handleGroupFieldChange('withdrawalSpread', e.target.value)
                  }
                />
                {groupFormErrors.withdrawalSpread && (
                  <p className="sgm-form__error">
                    {groupFormErrors.withdrawalSpread}
                  </p>
                )}
              </div>

              <div className="sgm-modal__actions">
                <button
                  type="button"
                  className="sgm-page__btn sgm-page__btn--secondary"
                  onClick={closeGroupModal}
                  disabled={groupSubmitting}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="sgm-page__btn sgm-page__btn--primary"
                  disabled={groupSubmitting}
                >
                  儲存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Section 2 delete confirm */}
      {deletingGroup && (
        <div className="sgm-modal__overlay">
          <div className="sgm-modal__card">
            <p className="sgm-modal__confirm-text">
              確定要刪除群組「{deletingGroup.name}」嗎？群組內的{' '}
              {deletingGroup.memberCount} 個品牌幣種對將改為套用預設點差。
            </p>
            <div className="sgm-modal__actions">
              <button
                type="button"
                className="sgm-page__btn sgm-page__btn--secondary"
                onClick={() => setDeletingGroup(null)}
                disabled={groupDeleting}
              >
                取消
              </button>
              <button
                type="button"
                className="sgm-page__btn sgm-page__btn--danger"
                onClick={confirmDeleteGroup}
                disabled={groupDeleting}
              >
                刪除
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Section 3: 管理成員 modal */}
      {managingGroup && (
        <div className="sgm-modal__overlay">
          <div className="sgm-modal__card sgm-modal__card--wide">
            <h2 className="sgm-modal__title">管理成員 - {managingGroup.name}</h2>

            {memberLoading && <p className="sgm-page__status">載入中...</p>}

            {!memberLoading && memberError && (
              <div className="sgm-page__error">
                <p>載入群組成員失敗，請稍後再試。</p>
                <button
                  type="button"
                  onClick={() => loadMemberDetail(managingGroup.id)}
                >
                  重試
                </button>
              </div>
            )}

            {!memberLoading && !memberError && memberDetail && (
              <>
                {memberDetail.members.length === 0 ? (
                  <p className="sgm-empty-state">此群組尚無成員</p>
                ) : (
                  <table className="sgm-table">
                    <thead>
                      <tr>
                        <th>幣種對</th>
                        <th>狀態</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {memberDetail.members.map((member) => {
                        const isMemberPending = pendingMemberIds.has(
                          member.currencyPairId,
                        )
                        return (
                          <tr key={member.currencyPairId}>
                            <td className="sgm-table__code-cell">
                              {member.baseCurrencyCode}/
                              {member.quoteCurrencyCode}
                              {isMemberPending && (
                                <span
                                  className="sgm-badge sgm-badge--pending"
                                  title="此列有待審核的變更，需先完成審核"
                                >
                                  審核中
                                </span>
                              )}
                            </td>
                            <td
                              className={
                                member.active
                                  ? 'sgm-status--active'
                                  : 'sgm-status--inactive'
                              }
                            >
                              {member.active ? '啟用' : '停用'}
                            </td>
                            <td>
                              <button
                                type="button"
                                className="sgm-page__btn sgm-page__btn--danger"
                                disabled={
                                  removingMemberId === member.currencyPairId ||
                                  isMemberPending
                                }
                                onClick={() =>
                                  handleRemoveMember(member.currencyPairId)
                                }
                              >
                                移除
                              </button>
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                )}

                <div className="sgm-member-picker">
                  <h3 className="sgm-member-picker__heading">加入品牌幣種對</h3>

                  {pickerLoading && (
                    <p className="sgm-page__status">載入中...</p>
                  )}

                  {!pickerLoading && pickerError && (
                    <div className="sgm-page__error">
                      <p>載入品牌幣種對失敗，請稍後再試。</p>
                      <button
                        type="button"
                        onClick={() =>
                          selectedBrandId !== null &&
                          loadPickerPairs(selectedBrandId)
                        }
                      >
                        重試
                      </button>
                    </div>
                  )}

                  {!pickerLoading &&
                    !pickerError &&
                    brandPairsForPicker &&
                    brandPairsForPicker.length === 0 && (
                      <p className="sgm-empty-state">
                        此品牌尚無幣種對，請先於「幣別對管理」新增幣種對定義
                      </p>
                    )}

                  {!pickerLoading &&
                    !pickerError &&
                    brandPairsForPicker &&
                    brandPairsForPicker.length > 0 &&
                    unassignedPairs &&
                    unassignedPairs.length === 0 && (
                      <p className="sgm-empty-state">
                        此品牌沒有可加入的幣種對
                      </p>
                    )}

                  {!pickerLoading &&
                    !pickerError &&
                    unassignedPairs &&
                    unassignedPairs.length > 0 && (
                      <>
                        <ul className="sgm-picker-list">
                          {unassignedPairs.map((pair) => (
                            <li key={pair.id} className="sgm-picker-list__item">
                              <label className="sgm-checkbox">
                                <input
                                  type="checkbox"
                                  checked={selectedPairIds.has(pair.id)}
                                  onChange={() =>
                                    togglePairSelection(pair.id)
                                  }
                                />
                                {pair.baseCurrencyCode}/{pair.quoteCurrencyCode}
                              </label>
                            </li>
                          ))}
                        </ul>
                        <button
                          type="button"
                          className="sgm-page__btn sgm-page__btn--primary"
                          disabled={selectedPairIds.size === 0 || joining}
                          onClick={handleJoin}
                        >
                          加入
                        </button>
                      </>
                    )}
                </div>
              </>
            )}

            <div className="sgm-modal__actions">
              <button
                type="button"
                className="sgm-page__btn sgm-page__btn--secondary"
                onClick={closeMemberModal}
              >
                關閉
              </button>
            </div>
          </div>
        </div>
      )}

      {toastMessage && (
        <Toast message={toastMessage} onDismiss={() => setToastMessage(null)} />
      )}
    </div>
  )
}

export default SpreadGroupManagementPage
