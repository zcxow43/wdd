import { useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { SpreadDefault, SpreadDefaultInput } from '../types/spread'
import './SpreadDefaultFormModal.css'

interface SpreadDefaultFormModalProps {
  spreadDefault: SpreadDefault
  onSubmit: (input: SpreadDefaultInput) => Promise<void>
  onClose: () => void
}

interface FormErrors {
  depositSpread?: string
  withdrawSpread?: string
}

const SPREAD_RANGE_ERROR = '入金點差與出金點差為必填，且須大於等於 0'
const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'

export function SpreadDefaultFormModal({ spreadDefault, onSubmit, onClose }: SpreadDefaultFormModalProps) {
  const [depositSpread, setDepositSpread] = useState<string>(String(spreadDefault.depositSpread))
  const [withdrawSpread, setWithdrawSpread] = useState<string>(String(spreadDefault.withdrawSpread))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function validate(): FormErrors {
    const next: FormErrors = {}
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
        depositSpread: Number(depositSpread),
        withdrawSpread: Number(withdrawSpread),
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setSubmitError(SPREAD_RANGE_ERROR)
      } else {
        setSubmitError(NETWORK_ERROR_MESSAGE)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={`編輯預設點差 - ${spreadDefault.brandCode}`} onClose={onClose}>
      <form className="spread-default-form" onSubmit={handleSubmit} noValidate>
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
