import { useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { CurrencyPair, CurrencyPairInput, RateType } from '../types/currencyPair'
import type { Brand } from '../types/brand'
import type { Currency } from '../types/currency'
import './CurrencyPairFormModal.css'

interface CurrencyPairFormModalProps {
  mode: 'create' | 'edit'
  initial?: CurrencyPair
  brands: Brand[]
  currencies: Currency[]
  onSubmit: (input: CurrencyPairInput) => Promise<void>
  onClose: () => void
}

interface FormErrors {
  brandId?: string
  baseCurrencyId?: string
  quoteCurrencyId?: string
  rate?: string
}

const SAME_CURRENCY_ERROR = '基準幣別與對應幣別不可相同'
const LIVE_DUPLICATE_ERROR = 'Currency pair already exists for this brand'
const PENDING_DUPLICATE_MESSAGE = '此幣種對已有待審核的異動申請'

export function CurrencyPairFormModal({
  mode,
  initial,
  brands,
  currencies,
  onSubmit,
  onClose,
}: CurrencyPairFormModalProps) {
  const [brandId, setBrandId] = useState<string>(initial ? String(initial.brandId) : '')
  const [baseCurrencyId, setBaseCurrencyId] = useState<string>(initial ? String(initial.baseCurrencyId) : '')
  const [quoteCurrencyId, setQuoteCurrencyId] = useState<string>(initial ? String(initial.quoteCurrencyId) : '')
  const [rateType, setRateType] = useState<RateType>(initial?.rateType ?? 'MANUAL')
  const [rate, setRate] = useState<string>(
    initial && initial.rate !== null && initial.rateType === 'MANUAL' ? String(initial.rate) : ''
  )
  const [active, setActive] = useState(initial?.active ?? true)
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const sameCurrency = baseCurrencyId !== '' && quoteCurrencyId !== '' && baseCurrencyId === quoteCurrencyId
  const quoteError = sameCurrency ? SAME_CURRENCY_ERROR : errors.quoteCurrencyId

  function handleRateTypeChange(newRateType: RateType) {
    setRateType(newRateType)
    setRate('')
    setErrors((prev) => ({ ...prev, rate: undefined }))
  }

  function validate(): FormErrors {
    const next: FormErrors = {}
    if (!brandId) next.brandId = '品牌為必填'
    if (!baseCurrencyId) next.baseCurrencyId = '基準幣別為必填'
    if (!quoteCurrencyId) next.quoteCurrencyId = '對應幣別為必填'
    if (baseCurrencyId && quoteCurrencyId && baseCurrencyId === quoteCurrencyId) {
      next.quoteCurrencyId = SAME_CURRENCY_ERROR
    }
    if (rateType === 'MANUAL') {
      const rateValue = Number(rate)
      if (!rate.trim() || Number.isNaN(rateValue) || rateValue <= 0) {
        next.rate = '匯率為必填，且須大於 0'
      }
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
        brandId: Number(brandId),
        baseCurrencyId: Number(baseCurrencyId),
        quoteCurrencyId: Number(quoteCurrencyId),
        rate: rateType === 'AUTO' ? null : Number(rate),
        rateType,
        active,
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSubmitError(
          error.body?.error === LIVE_DUPLICATE_ERROR ? '此品牌已存在相同的幣種對' : PENDING_DUPLICATE_MESSAGE,
        )
      } else if (error instanceof ApiError && error.status === 400) {
        setSubmitError(SAME_CURRENCY_ERROR)
      } else {
        setSubmitError('網路錯誤，請稍後再試')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={mode === 'create' ? '新增幣種對' : '編輯幣種對'} onClose={onClose}>
      <form className="currency-pair-form" onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="brandId">品牌</label>
          <select
            id="brandId"
            value={brandId}
            onChange={(event) => setBrandId(event.target.value)}
            aria-invalid={Boolean(errors.brandId)}
          >
            <option value="">請選擇</option>
            {brands.map((brand) => (
              <option key={brand.id} value={brand.id}>
                {brand.code}
              </option>
            ))}
          </select>
          {errors.brandId && <span className="field-error">{errors.brandId}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="baseCurrencyId">基準幣別</label>
          <select
            id="baseCurrencyId"
            value={baseCurrencyId}
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
          <label htmlFor="rateType">匯率類型</label>
          <select
            id="rateType"
            value={rateType}
            onChange={(event) => handleRateTypeChange(event.target.value as RateType)}
          >
            <option value="MANUAL">手動</option>
            <option value="AUTO">自動</option>
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="rate">匯率</label>
          <input
            id="rate"
            type="number"
            step="any"
            min={0}
            value={rate}
            onChange={(event) => setRate(event.target.value)}
            aria-invalid={Boolean(errors.rate)}
            disabled={rateType === 'AUTO'}
            placeholder={rateType === 'AUTO' ? '系統自動維護' : ''}
          />
          {rateType === 'AUTO' && <span className="field-hint">系統將自動維護匯率</span>}
          {errors.rate && <span className="field-error">{errors.rate}</span>}
        </div>

        <div className="form-field form-field--toggle">
          <label htmlFor="active">啟用</label>
          <input
            id="active"
            type="checkbox"
            checked={active}
            onChange={(event) => setActive(event.target.checked)}
          />
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
