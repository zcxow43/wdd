import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
} from '../types/currencyPairDefinition'
import type { Currency } from '../types/currency'
import './CurrencyPairDefinitionFormModal.css'

interface CurrencyPairDefinitionFormModalProps {
  mode: 'create' | 'edit'
  /** Required when mode === 'edit'. */
  initial?: CurrencyPairDefinition
  currencies: Currency[]
  onClose: () => void
  onSubmit: (input: CurrencyPairDefinitionCreateInput) => Promise<void>
}

interface FormState {
  baseCurrencyId: string
  quoteCurrencyId: string
  forwardPrecision: string
  reversePrecision: string
}

interface FormErrors {
  baseCurrencyId?: string
  quoteCurrencyId?: string
  forwardPrecision?: string
  reversePrecision?: string
  general?: string
}

function toFormState(initial: CurrencyPairDefinition | undefined): FormState {
  if (!initial) {
    return { baseCurrencyId: '', quoteCurrencyId: '', forwardPrecision: '', reversePrecision: '' }
  }
  return {
    baseCurrencyId: String(initial.baseCurrencyId),
    quoteCurrencyId: String(initial.quoteCurrencyId),
    forwardPrecision: String(initial.forwardPrecision),
    reversePrecision: String(initial.reversePrecision),
  }
}

function validatePrecision(value: string, label: string): string | undefined {
  if (value === '') {
    return `${label}為必填`
  }
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 0 || parsed > 8) {
    return `${label}須為 0 到 8 的整數`
  }
  return undefined
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.baseCurrencyId) {
    errors.baseCurrencyId = '基準幣別為必填'
  }

  if (!form.quoteCurrencyId) {
    errors.quoteCurrencyId = '對應幣別為必填'
  }

  if (form.baseCurrencyId && form.quoteCurrencyId && form.baseCurrencyId === form.quoteCurrencyId) {
    errors.baseCurrencyId = '基準幣別與對應幣別不可相同'
    errors.quoteCurrencyId = '基準幣別與對應幣別不可相同'
  }

  const forwardError = validatePrecision(form.forwardPrecision, '正向精度')
  if (forwardError) {
    errors.forwardPrecision = forwardError
  }

  const reverseError = validatePrecision(form.reversePrecision, '反向精度')
  if (reverseError) {
    errors.reversePrecision = reverseError
  }

  return errors
}

export function CurrencyPairDefinitionFormModal({
  mode,
  initial,
  currencies,
  onClose,
  onSubmit,
}: CurrencyPairDefinitionFormModalProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const isEdit = mode === 'edit'

  const baseQuoteConflict =
    form.baseCurrencyId !== '' && form.baseCurrencyId === form.quoteCurrencyId

  const handleSelectChange = (field: 'baseCurrencyId' | 'quoteCurrencyId') => (
    event: ChangeEvent<HTMLSelectElement>,
  ) => {
    const value = event.target.value
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined, general: undefined }))
  }

  const handlePrecisionChange = (field: 'forwardPrecision' | 'reversePrecision') => (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    const value = event.target.value
    setForm((current) => ({ ...current, [field]: value }))
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
        baseCurrencyId: Number(form.baseCurrencyId),
        quoteCurrencyId: Number(form.quoteCurrencyId),
        forwardPrecision: Number(form.forwardPrecision),
        reversePrecision: Number(form.reversePrecision),
      })
    } catch (error) {
      // The parent page handles 404/network errors (toast + close/refetch); a 409
      // (duplicate/reverse-direction already exists) and a 400 (out-of-range
      // precision or base===quote slipping past client validation) are both
      // rethrown here so the modal can stay open with an inline error.
      if (error instanceof ApiError && error.status === 409) {
        setErrors((current) => ({ ...current, quoteCurrencyId: '此幣種對（或其反向）已存在' }))
      } else if (error instanceof ApiError && error.status === 400) {
        setErrors((current) => ({ ...current, general: '輸入資料有誤，請確認後再試' }))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={isEdit ? '編輯幣種對主檔' : '新增幣種對主檔'} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="currency-pair-definition-base">基準幣別</label>
          <select
            id="currency-pair-definition-base"
            value={form.baseCurrencyId}
            onChange={handleSelectChange('baseCurrencyId')}
            disabled={isEdit}
          >
            <option value="">請選擇</option>
            {currencies.map((currency) => (
              <option key={currency.id} value={String(currency.id)}>
                {currency.code}
              </option>
            ))}
          </select>
          {errors.baseCurrencyId && <span className="field-error">{errors.baseCurrencyId}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-pair-definition-quote">對應幣別</label>
          <select
            id="currency-pair-definition-quote"
            value={form.quoteCurrencyId}
            onChange={handleSelectChange('quoteCurrencyId')}
            disabled={isEdit}
          >
            <option value="">請選擇</option>
            {currencies.map((currency) => (
              <option key={currency.id} value={String(currency.id)}>
                {currency.code}
              </option>
            ))}
          </select>
          {errors.quoteCurrencyId && <span className="field-error">{errors.quoteCurrencyId}</span>}
        </div>

        {baseQuoteConflict && !errors.baseCurrencyId && (
          <div className="field-error">基準幣別與對應幣別不可相同</div>
        )}

        <div className="form-field">
          <label htmlFor="currency-pair-definition-forward-precision">正向精度</label>
          <input
            id="currency-pair-definition-forward-precision"
            type="number"
            min={0}
            max={8}
            value={form.forwardPrecision}
            onChange={handlePrecisionChange('forwardPrecision')}
          />
          {errors.forwardPrecision && <span className="field-error">{errors.forwardPrecision}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-pair-definition-reverse-precision">反向精度</label>
          <input
            id="currency-pair-definition-reverse-precision"
            type="number"
            min={0}
            max={8}
            value={form.reversePrecision}
            onChange={handlePrecisionChange('reversePrecision')}
          />
          {errors.reversePrecision && <span className="field-error">{errors.reversePrecision}</span>}
        </div>

        {errors.general && <div className="field-error field-error--general">{errors.general}</div>}

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting || baseQuoteConflict}
          >
            {submitting ? '儲存中...' : '儲存'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
