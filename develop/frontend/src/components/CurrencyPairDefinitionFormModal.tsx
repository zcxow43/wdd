import { useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
  CurrencyPairDefinitionUpdateInput,
} from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'
import './CurrencyPairDefinitionFormModal.css'

interface CurrencyPairDefinitionFormModalProps {
  mode: 'create' | 'edit'
  initial?: CurrencyPairDefinition
  currencies: Currency[]
  onSubmit: (input: CurrencyPairDefinitionCreateInput | CurrencyPairDefinitionUpdateInput) => Promise<void>
  onClose: () => void
}

interface FormErrors {
  baseCurrencyId?: string
  quoteCurrencyId?: string
  forwardPrecision?: string
  reversePrecision?: string
}

const SAME_CURRENCY_ERROR = '基準幣別與對應幣別不可相同'
const DUPLICATE_DIRECTION_ERROR = '此幣種對（或其反向）已存在'
const INVALID_INPUT_ERROR = '輸入資料有誤，請確認後再試'
const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'

function isValidPrecision(value: string): boolean {
  if (!value.trim()) return false
  const num = Number(value)
  return Number.isInteger(num) && num >= 0 && num <= 8
}

export function CurrencyPairDefinitionFormModal({
  mode,
  initial,
  currencies,
  onSubmit,
  onClose,
}: CurrencyPairDefinitionFormModalProps) {
  const [baseCurrencyId, setBaseCurrencyId] = useState<string>(initial ? String(initial.baseCurrencyId) : '')
  const [quoteCurrencyId, setQuoteCurrencyId] = useState<string>(initial ? String(initial.quoteCurrencyId) : '')
  const [forwardPrecision, setForwardPrecision] = useState<string>(
    initial ? String(initial.forwardPrecision) : '',
  )
  const [reversePrecision, setReversePrecision] = useState<string>(
    initial ? String(initial.reversePrecision) : '',
  )
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const sameCurrency = baseCurrencyId !== '' && quoteCurrencyId !== '' && baseCurrencyId === quoteCurrencyId
  const quoteError = sameCurrency ? SAME_CURRENCY_ERROR : errors.quoteCurrencyId

  function validate(): FormErrors {
    const next: FormErrors = {}
    if (mode === 'create') {
      if (!baseCurrencyId) next.baseCurrencyId = '基準幣別為必填'
      if (!quoteCurrencyId) next.quoteCurrencyId = '對應幣別為必填'
      if (baseCurrencyId && quoteCurrencyId && baseCurrencyId === quoteCurrencyId) {
        next.quoteCurrencyId = SAME_CURRENCY_ERROR
      }
    }
    if (!isValidPrecision(forwardPrecision)) {
      next.forwardPrecision = '正向精度須為 0 到 8 之間的整數'
    }
    if (!isValidPrecision(reversePrecision)) {
      next.reversePrecision = '反向精度須為 0 到 8 之間的整數'
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
      if (mode === 'create') {
        await onSubmit({
          baseCurrencyId: Number(baseCurrencyId),
          quoteCurrencyId: Number(quoteCurrencyId),
          forwardPrecision: Number(forwardPrecision),
          reversePrecision: Number(reversePrecision),
        })
      } else {
        await onSubmit({
          forwardPrecision: Number(forwardPrecision),
          reversePrecision: Number(reversePrecision),
        })
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setErrors((prev) => ({ ...prev, quoteCurrencyId: DUPLICATE_DIRECTION_ERROR }))
      } else if (error instanceof ApiError && error.status === 400) {
        setSubmitError(INVALID_INPUT_ERROR)
      } else {
        setSubmitError(NETWORK_ERROR_MESSAGE)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={mode === 'create' ? '新增幣種對' : '編輯幣種對'} onClose={onClose}>
      <form className="currency-pair-definition-form" onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="baseCurrencyId">基準幣別</label>
          <select
            id="baseCurrencyId"
            value={baseCurrencyId}
            disabled={mode === 'edit'}
            onChange={(event) => setBaseCurrencyId(event.target.value)}
            aria-invalid={Boolean(errors.baseCurrencyId)}
          >
            <option value="">請選擇</option>
            {currencies.map((currency) => (
              <option key={currency.id} value={currency.id}>
                {currency.code}
              </option>
            ))}
          </select>
          {errors.baseCurrencyId && <span className="field-error">{errors.baseCurrencyId}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="quoteCurrencyId">對應幣別</label>
          <select
            id="quoteCurrencyId"
            value={quoteCurrencyId}
            disabled={mode === 'edit'}
            onChange={(event) => setQuoteCurrencyId(event.target.value)}
            aria-invalid={Boolean(quoteError)}
          >
            <option value="">請選擇</option>
            {currencies.map((currency) => (
              <option key={currency.id} value={currency.id}>
                {currency.code}
              </option>
            ))}
          </select>
          {quoteError && <span className="field-error">{quoteError}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="forwardPrecision">正向精度</label>
          <input
            id="forwardPrecision"
            type="number"
            min={0}
            max={8}
            step={1}
            value={forwardPrecision}
            onChange={(event) => setForwardPrecision(event.target.value)}
            aria-invalid={Boolean(errors.forwardPrecision)}
          />
          {errors.forwardPrecision && <span className="field-error">{errors.forwardPrecision}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="reversePrecision">反向精度</label>
          <input
            id="reversePrecision"
            type="number"
            min={0}
            max={8}
            step={1}
            value={reversePrecision}
            onChange={(event) => setReversePrecision(event.target.value)}
            aria-invalid={Boolean(errors.reversePrecision)}
          />
          {errors.reversePrecision && <span className="field-error">{errors.reversePrecision}</span>}
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
          <button type="submit" className="btn btn-primary" disabled={submitting || sameCurrency}>
            {submitting ? '儲存中…' : '儲存'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
