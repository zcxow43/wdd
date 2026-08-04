import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { CurrencyPair, CurrencyPairInput, RateType } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'
import './CurrencyPairFormModal.css'

interface CurrencyPairFormModalProps {
  /** Edit-only — there is no create mode on this page, see specs/frontend/currency-pair.md's Delta. */
  initial: CurrencyPair
  brands: Brand[]
  currencies: Currency[]
  onClose: () => void
  onSubmit: (input: CurrencyPairInput) => Promise<void>
}

interface FormState {
  brandId: string
  baseCurrencyId: string
  quoteCurrencyId: string
  rateType: RateType
  rate: string
  active: boolean
}

interface FormErrors {
  brandId?: string
  baseCurrencyId?: string
  quoteCurrencyId?: string
  rate?: string
  general?: string
}

const LIVE_DUPLICATE_MESSAGE = 'Currency pair already exists for this brand/base/quote combination'

function toFormState(initial: CurrencyPair): FormState {
  return {
    brandId: String(initial.brandId),
    baseCurrencyId: String(initial.baseCurrencyId),
    quoteCurrencyId: String(initial.quoteCurrencyId),
    rateType: initial.rateType,
    rate: initial.rateType === 'AUTO' || initial.rate === null ? '' : String(initial.rate),
    active: initial.active,
  }
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.brandId) {
    errors.brandId = '品牌為必填'
  }

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

  if (form.rateType === 'MANUAL') {
    const value = Number(form.rate)
    if (form.rate === '' || !Number.isFinite(value) || value <= 0) {
      errors.rate = '匯率為必填，且須大於 0'
    }
  }

  return errors
}

export function CurrencyPairFormModal({
  initial,
  brands,
  currencies,
  onClose,
  onSubmit,
}: CurrencyPairFormModalProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)

  const baseQuoteConflict =
    form.baseCurrencyId !== '' && form.baseCurrencyId === form.quoteCurrencyId

  const handleSelectChange = (field: 'brandId' | 'baseCurrencyId' | 'quoteCurrencyId') => (
    event: ChangeEvent<HTMLSelectElement>,
  ) => {
    const value = event.target.value
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined, general: undefined }))
  }

  const handleRateChange = (event: ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, rate: event.target.value }))
    setErrors((current) => ({ ...current, rate: undefined }))
  }

  const handleRateTypeChange = (event: ChangeEvent<HTMLSelectElement>) => {
    const value = event.target.value as RateType
    // Switching in either direction clears the rate field (and its stale
    // disabled-state value), matching the backend's own AUTO-clears-rate behavior
    // and requiring MANUAL to be re-entered explicitly rather than resubmitting a
    // stale value.
    setForm((current) => ({ ...current, rateType: value, rate: '' }))
    setErrors((current) => ({ ...current, rate: undefined }))
  }

  const handleActiveChange = (event: ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, active: event.target.checked }))
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
        brandId: Number(form.brandId),
        baseCurrencyId: Number(form.baseCurrencyId),
        quoteCurrencyId: Number(form.quoteCurrencyId),
        rateType: form.rateType,
        rate: form.rateType === 'AUTO' ? null : Number(form.rate),
        active: form.active,
      })
    } catch (error) {
      // The parent page handles 404/network errors (toast + close/refetch) and the
      // pending-duplicate 409 (toast). Only a live-duplicate 409 is expected to
      // reach here (rethrown by the page) as an inline field error, keeping the
      // modal open — with a defense-in-depth fallback for any other 409 that
      // somehow reaches this point uncaught.
      if (error instanceof ApiError && error.status === 409) {
        if (error.message === LIVE_DUPLICATE_MESSAGE) {
          setErrors((current) => ({ ...current, general: '此品牌已存在相同的幣種對' }))
        } else {
          setErrors((current) => ({ ...current, general: '此幣種對已有待審核的異動申請' }))
        }
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title="編輯幣種對" onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="currency-pair-brand">品牌</label>
          <select
            id="currency-pair-brand"
            value={form.brandId}
            onChange={handleSelectChange('brandId')}
          >
            <option value="">請選擇</option>
            {brands.map((brand) => (
              <option key={brand.id} value={String(brand.id)}>
                {brand.code}
              </option>
            ))}
          </select>
          {errors.brandId && <span className="field-error">{errors.brandId}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-pair-base">基準幣別</label>
          <select
            id="currency-pair-base"
            value={form.baseCurrencyId}
            onChange={handleSelectChange('baseCurrencyId')}
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
          <label htmlFor="currency-pair-quote">對應幣別</label>
          <select
            id="currency-pair-quote"
            value={form.quoteCurrencyId}
            onChange={handleSelectChange('quoteCurrencyId')}
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
          <label htmlFor="currency-pair-rate-type">匯率類型</label>
          <select
            id="currency-pair-rate-type"
            value={form.rateType}
            onChange={handleRateTypeChange}
          >
            <option value="MANUAL">手動</option>
            <option value="AUTO">自動</option>
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="currency-pair-rate">匯率</label>
          <input
            id="currency-pair-rate"
            type="number"
            step="any"
            value={form.rate}
            onChange={handleRateChange}
            disabled={form.rateType === 'AUTO'}
            placeholder={form.rateType === 'AUTO' ? '系統自動維護' : undefined}
          />
          {form.rateType === 'AUTO' ? (
            <span className="field-help">系統將自動維護匯率</span>
          ) : (
            errors.rate && <span className="field-error">{errors.rate}</span>
          )}
        </div>

        <div className="form-field form-field--inline">
          <label htmlFor="currency-pair-active">狀態</label>
          <label className="toggle-switch">
            <input
              id="currency-pair-active"
              type="checkbox"
              checked={form.active}
              onChange={handleActiveChange}
            />
            <span className="toggle-track">
              <span className="toggle-knob" />
            </span>
          </label>
          <span className="toggle-label">{form.active ? '啟用' : '停用'}</span>
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
