import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { CurrencyPair } from '../types/currencyPair'
import type { SpreadGroup, SpreadGroupInput } from '../types/spread'
import './SpreadGroupFormModal.css'

interface SpreadGroupFormModalProps {
  mode: 'create' | 'edit'
  initial?: SpreadGroup
  brandId: number
  availablePairs: CurrencyPair[]
  groups: SpreadGroup[]
  onSubmit: (input: SpreadGroupInput) => Promise<void>
  onClose: () => void
}

interface FormErrors {
  name?: string
  depositSpread?: string
  withdrawSpread?: string
}

const LIVE_DUPLICATE_ERROR = 'Spread group name already exists for this brand'
const NAME_DUPLICATE_MESSAGE = '此名稱已被使用'
const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'

export function SpreadGroupFormModal({
  mode,
  initial,
  brandId,
  availablePairs,
  groups,
  onSubmit,
  onClose,
}: SpreadGroupFormModalProps) {
  const [name, setName] = useState(initial?.name ?? '')
  const [depositSpread, setDepositSpread] = useState<string>(
    initial ? String(initial.depositSpread) : '',
  )
  const [withdrawSpread, setWithdrawSpread] = useState<string>(
    initial ? String(initial.withdrawSpread) : '',
  )
  const [selectedIds, setSelectedIds] = useState<Set<number>>(
    () => new Set(initial?.members.map((member) => member.currencyPairId) ?? []),
  )
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // Which *other* existing group (if any) each pair currently belongs to,
  // so the "not in this group" panel can hint at the move that will happen
  // once an update assigning it here is approved. Excludes the group being
  // edited itself, since that membership isn't "another" group.
  const otherGroupNameByPairId = useMemo(() => {
    const map = new Map<number, string>()
    for (const group of groups) {
      if (mode === 'edit' && initial && group.id === initial.id) continue
      for (const member of group.members) {
        map.set(member.currencyPairId, group.name)
      }
    }
    return map
  }, [groups, mode, initial])

  const unassignedPairs = availablePairs.filter((pair) => !selectedIds.has(pair.id))
  const assignedPairs = availablePairs.filter((pair) => selectedIds.has(pair.id))

  function handleAssign(pairId: number) {
    setSelectedIds((prev) => new Set(prev).add(pairId))
  }

  function handleUnassign(pairId: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.delete(pairId)
      return next
    })
  }

  function validate(): FormErrors {
    const next: FormErrors = {}
    if (!name.trim()) {
      next.name = '名稱為必填'
    }
    const depositValue = Number(depositSpread)
    if (!depositSpread.trim() || Number.isNaN(depositValue) || depositValue < 0) {
      next.depositSpread = '入金點差為必填，且須大於等於 0'
    }
    const withdrawValue = Number(withdrawSpread)
    if (!withdrawSpread.trim() || Number.isNaN(withdrawValue) || withdrawValue < 0) {
      next.withdrawSpread = '出金點差為必填，且須大於等於 0'
    }
    return next
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitError(null)
    const validationErrors = validate()
    setErrors(validationErrors)
    if (Object.keys(validationErrors).length > 0) {
      return
    }

    setSubmitting(true)
    try {
      await onSubmit({
        brandId,
        name: name.trim(),
        depositSpread: Number(depositSpread),
        withdrawSpread: Number(withdrawSpread),
        currencyPairIds: Array.from(selectedIds),
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409 && error.body?.error === LIVE_DUPLICATE_ERROR) {
        setErrors((prev) => ({ ...prev, name: NAME_DUPLICATE_MESSAGE }))
      } else {
        setSubmitError(NETWORK_ERROR_MESSAGE)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={mode === 'create' ? '新增點差群組' : '編輯點差群組'} onClose={onClose} size="lg">
      <form className="spread-group-form" onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="name">名稱</label>
          <input
            id="name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={Boolean(errors.name)}
          />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </div>

        <div className="form-row">
          <div className="form-field">
            <label htmlFor="depositSpread">入金點差</label>
            <input
              id="depositSpread"
              type="number"
              step="any"
              min={0}
              value={depositSpread}
              onChange={(event) => setDepositSpread(event.target.value)}
              aria-invalid={Boolean(errors.depositSpread)}
            />
            {errors.depositSpread && <span className="field-error">{errors.depositSpread}</span>}
          </div>

          <div className="form-field">
            <label htmlFor="withdrawSpread">出金點差</label>
            <input
              id="withdrawSpread"
              type="number"
              step="any"
              min={0}
              value={withdrawSpread}
              onChange={(event) => setWithdrawSpread(event.target.value)}
              aria-invalid={Boolean(errors.withdrawSpread)}
            />
            {errors.withdrawSpread && <span className="field-error">{errors.withdrawSpread}</span>}
          </div>
        </div>

        <div className="form-field">
          <label>幣種對指派</label>
          <div className="pair-assigner">
            <div className="pair-assigner-panel">
              <div className="pair-assigner-panel-title">未加入本群組</div>
              <ul className="pair-assigner-list">
                {unassignedPairs.length === 0 && <li className="pair-assigner-empty">無可指派的幣種對</li>}
                {unassignedPairs.map((pair) => {
                  const otherGroupName = otherGroupNameByPairId.get(pair.id)
                  return (
                    <li key={pair.id} className="pair-assigner-item">
                      <div className="pair-assigner-item-info">
                        <span className="currency-code">
                          {pair.baseCurrencyCode}/{pair.quoteCurrencyCode}
                        </span>
                        {otherGroupName && (
                          <span className="pair-assigner-hint">
                            目前屬於：{otherGroupName}，核准後將自動移出
                          </span>
                        )}
                      </div>
                      <button type="button" className="btn btn-link" onClick={() => handleAssign(pair.id)}>
                        加入 →
                      </button>
                    </li>
                  )
                })}
              </ul>
            </div>

            <div className="pair-assigner-panel">
              <div className="pair-assigner-panel-title">已加入本群組</div>
              <ul className="pair-assigner-list">
                {assignedPairs.length === 0 && <li className="pair-assigner-empty">尚未加入任何幣種對</li>}
                {assignedPairs.map((pair) => (
                  <li key={pair.id} className="pair-assigner-item">
                    <span className="currency-code">
                      {pair.baseCurrencyCode}/{pair.quoteCurrencyCode}
                    </span>
                    <button type="button" className="btn btn-link" onClick={() => handleUnassign(pair.id)}>
                      ← 移除
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        {submitError && (
          <div className="form-error" role="alert">
            {submitError}
          </div>
        )}

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? '儲存中…' : '儲存'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
