import { useCallback, useEffect, useState } from 'react'
import {
  type AuditActionType,
  type AuditEntityType,
  type AuditRequestDetail,
  type AuditRequestSummary,
  type AuditStatus,
  approveAuditRequest,
  fetchAuditRequest,
  fetchAuditRequests,
  rejectAuditRequest,
} from '../api/audit'
import { type Brand, fetchBrands } from '../api/brands'
import { ApiError } from '../api/http'
import Toast from '../components/Toast'
import './AuditRequestPage.css'

const ACTOR_STORAGE_KEY = 'wdd_audit_actor'

type StatusFilterValue = AuditStatus | 'ALL'

const STATUS_TABS: { value: StatusFilterValue; label: string }[] = [
  { value: 'PENDING', label: '待審核' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'REJECTED', label: '已駁回' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'ALL', label: '全部' },
]

const ENTITY_TYPE_OPTIONS: { value: AuditEntityType | ''; label: string }[] = [
  { value: '', label: '全部類型' },
  { value: 'CURRENCY_PAIR', label: '品牌幣種對' },
  { value: 'BRAND_SPREAD', label: '預設點差' },
  { value: 'SPREAD_GROUP', label: '點差群組' },
  { value: 'SPREAD_GROUP_MEMBER', label: '群組成員' },
]

const ENTITY_TYPE_LABELS: Record<AuditEntityType, string> = {
  CURRENCY_PAIR: '品牌幣種對',
  BRAND_SPREAD: '預設點差',
  SPREAD_GROUP: '點差群組',
  SPREAD_GROUP_MEMBER: '群組成員',
}

const ACTION_TYPE_LABELS: Record<AuditActionType, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '刪除',
}

const ACTION_TYPE_CLASSES: Record<AuditActionType, string> = {
  CREATE: 'aud-action--create',
  UPDATE: 'aud-action--update',
  DELETE: 'aud-action--delete',
}

const STATUS_LABELS: Record<AuditStatus, string> = {
  PENDING: '待審核',
  APPROVED: '已核准',
  REJECTED: '已駁回',
  CANCELLED: '已取消',
}

const STATUS_BADGE_CLASSES: Record<AuditStatus, string> = {
  PENDING: 'aud-badge--pending',
  APPROVED: 'aud-badge--approved',
  REJECTED: 'aud-badge--rejected',
  CANCELLED: 'aud-badge--cancelled',
}

interface DiffRow {
  field: string
  before: string
  after: string
}

function formatValue(value: unknown): string {
  return value === null || value === undefined ? '—' : String(value)
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function buildDiffRows(detail: AuditRequestDetail): DiffRow[] {
  const before = (detail.beforeData ?? {}) as Record<string, unknown>
  const after = (detail.afterData ?? {}) as Record<string, unknown>

  if (detail.actionType === 'CREATE') {
    return Object.keys(after).map((field) => ({
      field,
      before: '—',
      after: formatValue(after[field]),
    }))
  }

  if (detail.actionType === 'DELETE') {
    return Object.keys(before).map((field) => ({
      field,
      before: formatValue(before[field]),
      after: '—',
    }))
  }

  const fields = new Set([...Object.keys(before), ...Object.keys(after)])
  const rows: DiffRow[] = []
  fields.forEach((field) => {
    const beforeValue = before[field]
    const afterValue = after[field]
    if (JSON.stringify(beforeValue) !== JSON.stringify(afterValue)) {
      rows.push({
        field,
        before: formatValue(beforeValue),
        after: formatValue(afterValue),
      })
    }
  })
  return rows
}

interface ActionDialogState {
  id: number
  type: 'approve' | 'reject'
  comment: string
  error?: string
}

function AuditRequestPage() {
  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>('PENDING')
  const [brandFilter, setBrandFilter] = useState('')
  const [typeFilter, setTypeFilter] = useState<AuditEntityType | ''>('')
  const [actor, setActor] = useState('system')

  const [brands, setBrands] = useState<Brand[] | null>(null)

  const [requests, setRequests] = useState<AuditRequestSummary[] | null>(null)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState(false)

  const [viewingId, setViewingId] = useState<number | null>(null)
  const [detail, setDetail] = useState<AuditRequestDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(false)

  const [actionDialog, setActionDialog] = useState<ActionDialogState | null>(
    null,
  )
  const [actionSubmitting, setActionSubmitting] = useState(false)
  const [inFlightId, setInFlightId] = useState<number | null>(null)

  const [toastMessage, setToastMessage] = useState<string | null>(null)

  useEffect(() => {
    const stored = window.localStorage.getItem(ACTOR_STORAGE_KEY)
    if (stored) {
      setActor(stored)
    }
  }, [])

  useEffect(() => {
    fetchBrands()
      .then((data) => setBrands(data))
      .catch(() => setBrands([]))
  }, [])

  const handleActorChange = (value: string) => {
    setActor(value)
    window.localStorage.setItem(ACTOR_STORAGE_KEY, value)
  }

  const loadList = useCallback(() => {
    setListLoading(true)
    setListError(false)
    fetchAuditRequests({
      status: statusFilter === 'ALL' ? undefined : statusFilter,
      entityType: typeFilter === '' ? undefined : typeFilter,
      brandId: brandFilter === '' ? undefined : Number(brandFilter),
    })
      .then((data) => {
        setRequests(data)
      })
      .catch(() => {
        setListError(true)
      })
      .finally(() => {
        setListLoading(false)
      })
  }, [statusFilter, typeFilter, brandFilter])

  useEffect(() => {
    loadList()
  }, [loadList])

  const loadDetail = useCallback((id: number) => {
    setDetailLoading(true)
    setDetailError(false)
    fetchAuditRequest(id)
      .then((data) => {
        setDetail(data)
      })
      .catch(() => {
        setDetailError(true)
      })
      .finally(() => {
        setDetailLoading(false)
      })
  }, [])

  const openDetailModal = (id: number) => {
    setViewingId(id)
    setDetail(null)
    setDetailError(false)
    loadDetail(id)
  }

  const closeDetailModal = () => {
    setViewingId(null)
    setDetail(null)
    setDetailError(false)
  }

  const openApproveDialog = (id: number) => {
    setActionDialog({ id, type: 'approve', comment: '' })
  }

  const openRejectDialog = (id: number) => {
    setActionDialog({ id, type: 'reject', comment: '' })
  }

  const closeActionDialog = () => {
    if (actionSubmitting) {
      return
    }
    setActionDialog(null)
  }

  const handleActionCommentChange = (value: string) => {
    setActionDialog((current) =>
      current ? { ...current, comment: value, error: undefined } : current,
    )
  }

  const handleActionConfirm = () => {
    if (!actionDialog) {
      return
    }
    const { id, type } = actionDialog
    const trimmedComment = actionDialog.comment.trim()

    if (type === 'reject') {
      if (trimmedComment === '' || trimmedComment.length > 500) {
        setActionDialog((current) =>
          current ? { ...current, error: '請填寫駁回原因' } : current,
        )
        return
      }
    }

    setActionSubmitting(true)
    setInFlightId(id)

    const call =
      type === 'approve'
        ? approveAuditRequest(id, trimmedComment, actor)
        : rejectAuditRequest(id, trimmedComment, actor)

    call
      .then(() => {
        setActionDialog(null)
        setToastMessage(type === 'approve' ? '已核准，變更已套用' : '已駁回')
        loadList()
        if (viewingId === id) {
          loadDetail(id)
        }
      })
      .catch((error: unknown) => {
        if (
          type === 'approve' &&
          error instanceof ApiError &&
          error.status === 422
        ) {
          setToastMessage(`核准失敗：${error.message}`)
          setActionDialog(null)
          loadList()
          if (viewingId === id) {
            loadDetail(id)
          }
        } else if (error instanceof ApiError && error.status === 409) {
          setToastMessage('此申請已被處理，請重新整理')
          setActionDialog(null)
          loadList()
          if (viewingId === id) {
            loadDetail(id)
          }
        } else {
          setToastMessage(
            type === 'approve'
              ? '核准失敗，請稍後再試'
              : '駁回失敗，請稍後再試',
          )
          setActionDialog(null)
        }
      })
      .finally(() => {
        setActionSubmitting(false)
        setInFlightId(null)
      })
  }

  const getBrandCode = (brandId: number | null): string => {
    if (brandId === null) {
      return '—'
    }
    const brand = brands?.find((b) => b.id === brandId)
    return brand ? brand.code : '—'
  }

  const diffRows = detail ? buildDiffRows(detail) : []

  return (
    <div className="aud-page">
      <div className="aud-page__breadcrumb">匯率中心 &gt; 審核紀錄</div>
      <h1 className="aud-page__title">審核紀錄</h1>

      <div className="aud-filter-bar">
        <div className="aud-filter-bar__field">
          <span className="aud-filter-bar__label">狀態</span>
          <div className="aud-segmented" role="tablist" aria-label="狀態篩選">
            {STATUS_TABS.map((tab) => (
              <button
                key={tab.value}
                type="button"
                role="tab"
                aria-selected={statusFilter === tab.value}
                className={`aud-segmented__item${
                  statusFilter === tab.value
                    ? ' aud-segmented__item--selected'
                    : ''
                }`}
                onClick={() => setStatusFilter(tab.value)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        <div className="aud-filter-bar__field">
          <label className="aud-filter-bar__label" htmlFor="brandFilter">
            品牌
          </label>
          <select
            id="brandFilter"
            className="aud-filter-bar__select"
            value={brandFilter}
            onChange={(e) => setBrandFilter(e.target.value)}
          >
            <option value="">全部品牌</option>
            {brands?.map((brand) => (
              <option key={brand.id} value={brand.id}>
                {brand.code}
              </option>
            ))}
          </select>
        </div>

        <div className="aud-filter-bar__field">
          <label className="aud-filter-bar__label" htmlFor="typeFilter">
            類型
          </label>
          <select
            id="typeFilter"
            className="aud-filter-bar__select"
            value={typeFilter}
            onChange={(e) =>
              setTypeFilter(e.target.value as AuditEntityType | '')
            }
          >
            {ENTITY_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="aud-filter-bar__field">
          <label className="aud-filter-bar__label" htmlFor="actorInput">
            審核人員
          </label>
          <input
            id="actorInput"
            type="text"
            className="aud-filter-bar__input"
            value={actor}
            onChange={(e) => handleActorChange(e.target.value)}
            placeholder="system"
          />
        </div>
      </div>

      {listLoading && <p className="aud-page__status">載入中...</p>}

      {!listLoading && listError && (
        <div className="aud-page__error">
          <p>載入審核申請清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadList}>
            重試
          </button>
        </div>
      )}

      {!listLoading && !listError && requests && requests.length === 0 && (
        <p className="aud-empty-state">目前沒有符合條件的審核申請</p>
      )}

      {!listLoading && !listError && requests && requests.length > 0 && (
        <div className="aud-table-card">
          <table className="aud-table">
            <thead>
              <tr>
                <th>申請時間</th>
                <th>品牌</th>
                <th>類型</th>
                <th>動作</th>
                <th>說明</th>
                <th>申請人</th>
                <th>狀態</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {requests.map((request) => {
                const rowBusy = inFlightId === request.id
                return (
                  <tr key={request.id}>
                    <td className="aud-table__requested-at">
                      {formatDateTime(request.requestedAt)}
                    </td>
                    <td>{getBrandCode(request.brandId)}</td>
                    <td>{ENTITY_TYPE_LABELS[request.entityType]}</td>
                    <td
                      className={`aud-table__action ${ACTION_TYPE_CLASSES[request.actionType]}`}
                    >
                      {ACTION_TYPE_LABELS[request.actionType]}
                    </td>
                    <td className="aud-table__summary">{request.summary}</td>
                    <td className="aud-table__requested-by">
                      {request.requestedBy}
                    </td>
                    <td>
                      <span
                        className={`aud-badge ${STATUS_BADGE_CLASSES[request.status]}`}
                      >
                        {STATUS_LABELS[request.status]}
                      </span>
                    </td>
                    <td>
                      <div className="aud-table__actions">
                        <button
                          type="button"
                          className="aud-page__btn aud-page__btn--secondary"
                          onClick={() => openDetailModal(request.id)}
                        >
                          檢視
                        </button>
                        {request.status === 'PENDING' && (
                          <>
                            <button
                              type="button"
                              className="aud-page__btn aud-page__btn--primary"
                              disabled={rowBusy}
                              onClick={() => openApproveDialog(request.id)}
                            >
                              核准
                            </button>
                            <button
                              type="button"
                              className="aud-page__btn aud-page__btn--danger"
                              disabled={rowBusy}
                              onClick={() => openRejectDialog(request.id)}
                            >
                              駁回
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {viewingId !== null && (
        <div className="aud-modal__overlay">
          <div className="aud-modal__card aud-modal__card--wide">
            <h2 className="aud-modal__title">審核申請明細</h2>

            {detailLoading && <p className="aud-page__status">載入中...</p>}

            {!detailLoading && detailError && (
              <div className="aud-page__error">
                <p>載入審核申請明細失敗，請稍後再試。</p>
                <button type="button" onClick={() => loadDetail(viewingId)}>
                  重試
                </button>
              </div>
            )}

            {!detailLoading && !detailError && detail && (
              <>
                <dl className="aud-detail-meta">
                  <div className="aud-detail-meta__row">
                    <dt>類型</dt>
                    <dd>{ENTITY_TYPE_LABELS[detail.entityType]}</dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>動作</dt>
                    <dd className={ACTION_TYPE_CLASSES[detail.actionType]}>
                      {ACTION_TYPE_LABELS[detail.actionType]}
                    </dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>品牌</dt>
                    <dd>{getBrandCode(detail.brandId)}</dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>說明</dt>
                    <dd>{detail.summary}</dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>申請人</dt>
                    <dd>{detail.requestedBy}</dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>申請時間</dt>
                    <dd>{formatDateTime(detail.requestedAt)}</dd>
                  </div>
                  <div className="aud-detail-meta__row">
                    <dt>狀態</dt>
                    <dd>
                      <span
                        className={`aud-badge ${STATUS_BADGE_CLASSES[detail.status]}`}
                      >
                        {STATUS_LABELS[detail.status]}
                      </span>
                    </dd>
                  </div>
                  {detail.status !== 'PENDING' && (
                    <>
                      <div className="aud-detail-meta__row">
                        <dt>審核人</dt>
                        <dd>{detail.reviewedBy ?? '—'}</dd>
                      </div>
                      <div className="aud-detail-meta__row">
                        <dt>審核時間</dt>
                        <dd>{formatDateTime(detail.reviewedAt)}</dd>
                      </div>
                      <div className="aud-detail-meta__row">
                        <dt>審核意見</dt>
                        <dd>{detail.reviewComment ?? '—'}</dd>
                      </div>
                    </>
                  )}
                </dl>

                {detail.applyError && (
                  <div className="aud-apply-error">
                    上次核准失敗：{detail.applyError}
                  </div>
                )}

                <h3 className="aud-detail-section-title">變更內容</h3>
                {diffRows.length === 0 ? (
                  <p className="aud-empty-state">無變更欄位</p>
                ) : (
                  <table className="aud-diff-table">
                    <thead>
                      <tr>
                        <th>欄位</th>
                        <th>原值</th>
                        <th>新值</th>
                      </tr>
                    </thead>
                    <tbody>
                      {diffRows.map((row) => (
                        <tr key={row.field}>
                          <td>{row.field}</td>
                          <td className="aud-diff-table__before">
                            {row.before}
                          </td>
                          <td className="aud-diff-table__after">
                            {row.after}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {detail.status === 'PENDING' && (
                  <div className="aud-modal__actions">
                    <button
                      type="button"
                      className="aud-page__btn aud-page__btn--danger"
                      disabled={inFlightId === detail.id}
                      onClick={() => openRejectDialog(detail.id)}
                    >
                      駁回
                    </button>
                    <button
                      type="button"
                      className="aud-page__btn aud-page__btn--primary"
                      disabled={inFlightId === detail.id}
                      onClick={() => openApproveDialog(detail.id)}
                    >
                      核准
                    </button>
                  </div>
                )}
              </>
            )}

            <div className="aud-modal__actions">
              <button
                type="button"
                className="aud-page__btn aud-page__btn--secondary"
                onClick={closeDetailModal}
              >
                關閉
              </button>
            </div>
          </div>
        </div>
      )}

      {actionDialog && (
        <div className="aud-modal__overlay">
          <div className="aud-modal__card">
            <h2 className="aud-modal__title">
              {actionDialog.type === 'approve' ? '核准申請' : '駁回申請'}
            </h2>
            <div className="aud-form__field">
              <label className="aud-form__label" htmlFor="actionComment">
                {actionDialog.type === 'approve' ? '審核意見' : '駁回原因'}
              </label>
              <textarea
                id="actionComment"
                className="aud-form__textarea"
                value={actionDialog.comment}
                disabled={actionSubmitting}
                onChange={(e) => handleActionCommentChange(e.target.value)}
              />
              {actionDialog.error && (
                <p className="aud-form__error">{actionDialog.error}</p>
              )}
            </div>
            <div className="aud-modal__actions">
              <button
                type="button"
                className="aud-page__btn aud-page__btn--secondary"
                onClick={closeActionDialog}
                disabled={actionSubmitting}
              >
                取消
              </button>
              <button
                type="button"
                className={
                  actionDialog.type === 'approve'
                    ? 'aud-page__btn aud-page__btn--primary'
                    : 'aud-page__btn aud-page__btn--danger'
                }
                onClick={handleActionConfirm}
                disabled={actionSubmitting}
              >
                確認
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

export default AuditRequestPage
