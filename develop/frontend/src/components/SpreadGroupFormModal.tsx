import { useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { SpreadGroup, SpreadGroupInput } from '../types/spread'
import type { CurrencyPair } from '../types/currencyPair'
import './SpreadGroupFormModal.css'

interface SpreadGroupFormModalProps {
  mode: 'create' | 'edit'
  /** Required when mode === 'edit'. */
  initial?: SpreadGroup
  brandId: number
  /** All active currency pairs for the brand — the full pool the assigner offers. */
  availablePairs: CurrencyPair[]
  /** All groups for the brand, used to compute which pair belongs to which group. */
  groups: SpreadGroup[]
  onClose: () => void
  onSubmit: (input: SpreadGroupInput) => Promise<void>
}

interface FormState {
  name: string
  depositSpread: string
  withdrawSpread: string
}

interface FormErrors {
  name?: string
  depositSpread?: string
  withdrawSpread?: string
  general?: string
}

function toFormState(initial: SpreadGroup | undefined): FormState {
  if (!initial) {
    return { name: '', depositSpread: '', withdrawSpread: '' }
  }
  return {
    name: initial.name,
    depositSpread: String(initial.depositSpread),
    withdrawSpread: String(initial.withdrawSpread),
  }
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.name.trim()) {
    errors.name = '名稱為必填'
  }

  const deposit = Number(form.depositSpread)
  if (form.depositSpread === '' || !Number.isFinite(deposit) || deposit < 0) {
    errors.depositSpread = '入金點差為必填，且不可小於 0'
  }

  const withdraw = Number(form.withdrawSpread)
  if (form.withdrawSpread === '' || !Number.isFinite(withdraw) || withdraw < 0) {
    errors.withdrawSpread = '出金點差為必填，且不可小於 0'
  }

  return errors
}

export function SpreadGroupFormModal({
  mode,
  initial,
  brandId,
  availablePairs,
  groups,
  onClose,
  onSubmit,
}: SpreadGroupFormModalProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<number>>(
    () => new Set(initial?.members.map((member) => member.currencyPairId) ?? []),
  )
  const isEdit = mode === 'edit'

  // Which group (other than the one being edited) each pair currently belongs to,
  // so the left panel can show a "will be moved out on approval" hint.
  const otherGroupByPairId = useMemo(() => {
    const map = new Map<number, string>()
    for (const group of groups) {
      if (initial && group.id === initial.id) {
        continue
      }
      for (const member of group.members) {
        map.set(member.currencyPairId, group.name)
      }
    }
    return map
  }, [groups, initial])

  const unassignedPairs = availablePairs.filter((pair) => !selectedIds.has(pair.id))
  const assignedPairs = availablePairs.filter((pair) => selectedIds.has(pair.id))

  const addPair = (id: number) => {
    setSelectedIds((current) => new Set(current).add(id))
  }

  const removePair = (id: number) => {
    setSelectedIds((current) => {
      const next = new Set(current)
      next.delete(id)
      return next
    })
  }

  const handleFieldChange = (field: 'name' | 'depositSpread' | 'withdrawSpread') => (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    setForm((current) => ({ ...current, [field]: event.target.value }))
    setErrors((current) => ({ ...current, [field]: undefined, general: undefined }))
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const validationErrors = validate(form)
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        brandId,
        name: form.name.trim(),
        depositSpread: Number(form.depositSpread),
        withdrawSpread: Number(form.withdrawSpread),
        currencyPairIds: Array.from(selectedIds),
      })
    } catch (error) {
      // The page pre-filters the pending-duplicate 409 and the 400/404
      // currency-pair-reference cases (close + toast + refresh) before they ever
      // reach here — only the live-duplicate-name 409 is rethrown, for an inline
      // field error that keeps the modal open.
      if (error instanceof ApiError && error.status === 409) {
        setErrors((current) => ({ ...current, name: '此名稱已被使用' }))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={isEdit ? '編輯點差群組' : '新增點差群組'} size="lg" onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="spread-group-name">名稱</label>
          <input
            id="spread-group-name"
            type="text"
            value={form.name}
            onChange={handleFieldChange('name')}
          />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </div>

        <div className="spread-group-form-fields">
          <div className="form-field">
            <label htmlFor="spread-group-deposit">入金點差</label>
            <input
              id="spread-group-deposit"
              type="number"
              step="any"
              min={0}
              value={form.depositSpread}
              onChange={handleFieldChange('depositSpread')}
            />
            {errors.depositSpread && <span className="field-error">{errors.depositSpread}</span>}
          </div>

          <div className="form-field">
            <label htmlFor="spread-group-withdraw">出金點差</label>
            <input
              id="spread-group-withdraw"
              type="number"
              step="any"
              min={0}
              value={form.withdrawSpread}
              onChange={handleFieldChange('withdrawSpread')}
            />
            {errors.withdrawSpread && <span className="field-error">{errors.withdrawSpread}</span>}
          </div>
        </div>

        <div className="pair-assigner-label">幣種對指派</div>
        <div className="pair-assigner">
          <div className="pair-assigner-panel">
            <div className="pair-assigner-panel-title">未加入本群組</div>
            <div className="pair-assigner-panel-body">
              {unassignedPairs.length === 0 ? (
                <div className="pair-assigner-empty">（無可指派的幣種對）</div>
              ) : (
                unassignedPairs.map((pair) => {
                  const otherGroupName = otherGroupByPairId.get(pair.id)
                  return (
                    <div className="pair-assigner-row" key={pair.id}>
                      <div className="pair-assigner-row-main">
                        <span className="currency-code">{`${pair.baseCurrencyCode}/${pair.quoteCurrencyCode}`}</span>
                        {otherGroupName && (
                          <span className="pair-assigner-hint">
                            目前屬於：{otherGroupName}，核准後將自動移出
                          </span>
                        )}
                      </div>
                      <button type="button" className="action-btn" onClick={() => addPair(pair.id)}>
                        加入 →
                      </button>
                    </div>
                  )
                })
              )}
            </div>
          </div>

          <div className="pair-assigner-panel">
            <div className="pair-assigner-panel-title">已加入本群組</div>
            <div className="pair-assigner-panel-body">
              {assignedPairs.length === 0 ? (
                <div className="pair-assigner-empty">（尚未加入任何幣種對）</div>
              ) : (
                assignedPairs.map((pair) => (
                  <div className="pair-assigner-row" key={pair.id}>
                    <div className="pair-assigner-row-main">
                      <span className="currency-code">{`${pair.baseCurrencyCode}/${pair.quoteCurrencyCode}`}</span>
                    </div>
                    <button type="button" className="action-btn" onClick={() => removePair(pair.id)}>
                      ← 移除
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {errors.general && <div className="field-error field-error--general">{errors.general}</div>}

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? '送出中...' : '送出'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
