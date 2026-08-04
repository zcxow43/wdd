import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { SpreadDefault, SpreadDefaultInput } from '../types/spread'
import './SpreadDefaultFormModal.css'

interface SpreadDefaultFormModalProps {
  initial: SpreadDefault
  onClose: () => void
  onSubmit: (input: SpreadDefaultInput) => Promise<void>
}

interface FormState {
  depositSpread: string
  withdrawSpread: string
}

interface FormErrors {
  depositSpread?: string
  withdrawSpread?: string
  general?: string
}

function toFormState(initial: SpreadDefault): FormState {
  return {
    depositSpread: String(initial.depositSpread),
    withdrawSpread: String(initial.withdrawSpread),
  }
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

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

export function SpreadDefaultFormModal({ initial, onClose, onSubmit }: SpreadDefaultFormModalProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)

  const handleFieldChange = (field: keyof FormState) => (event: ChangeEvent<HTMLInputElement>) => {
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
        depositSpread: Number(form.depositSpread),
        withdrawSpread: Number(form.withdrawSpread),
      })
    } catch (error) {
      // The page handles the pending-duplicate 409 and network errors itself
      // (close + toast + refresh); only a 400 validation failure is rethrown here
      // to keep the modal open with an inline error.
      if (error instanceof ApiError) {
        setErrors((current) => ({ ...current, general: '輸入資料有誤，請確認後再試' }))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="編輯預設點差" onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="spread-default-form-fields">
          <div className="form-field">
            <label htmlFor="spread-default-deposit">入金點差</label>
            <input
              id="spread-default-deposit"
              type="number"
              step="any"
              min={0}
              value={form.depositSpread}
              onChange={handleFieldChange('depositSpread')}
            />
            {errors.depositSpread && <span className="field-error">{errors.depositSpread}</span>}
          </div>

          <div className="form-field">
            <label htmlFor="spread-default-withdraw">出金點差</label>
            <input
              id="spread-default-withdraw"
              type="number"
              step="any"
              min={0}
              value={form.withdrawSpread}
              onChange={handleFieldChange('withdrawSpread')}
            />
            {errors.withdrawSpread && <span className="field-error">{errors.withdrawSpread}</span>}
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
