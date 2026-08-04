import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { Currency, CurrencyInput } from '../types/currency'
import './CurrencyFormModal.css'

interface CurrencyFormModalProps {
  initial: Currency | null
  onClose: () => void
  onSubmit: (input: CurrencyInput) => Promise<void>
}

interface FormState {
  code: string
  name: string
  nameZh: string
  symbol: string
  decimalPlaces: string
}

interface FormErrors {
  code?: string
  name?: string
  nameZh?: string
  symbol?: string
  decimalPlaces?: string
  general?: string
}

const CODE_PATTERN = /^[A-Z]{3}$/

function toFormState(initial: Currency | null): FormState {
  if (!initial) {
    return { code: '', name: '', nameZh: '', symbol: '', decimalPlaces: '' }
  }
  return {
    code: initial.code,
    name: initial.name,
    nameZh: initial.nameZh ?? '',
    symbol: initial.symbol ?? '',
    decimalPlaces: String(initial.decimalPlaces),
  }
}

function validate(form: FormState): FormErrors {
  const errors: FormErrors = {}

  if (!form.code) {
    errors.code = 'Code 為必填'
  } else if (!CODE_PATTERN.test(form.code)) {
    errors.code = 'Code 須為 3 位大寫字母'
  }

  if (!form.name.trim()) {
    errors.name = 'Name 為必填'
  } else if (form.name.length > 100) {
    errors.name = 'Name 長度不可超過 100'
  }

  if (form.nameZh.length > 100) {
    errors.nameZh = '中文名稱長度不可超過 100'
  }

  if (form.symbol.length > 10) {
    errors.symbol = 'Symbol 長度不可超過 10'
  }

  if (form.decimalPlaces === '') {
    errors.decimalPlaces = 'Decimal Places 為必填'
  } else {
    const value = Number(form.decimalPlaces)
    if (!Number.isInteger(value) || value < 0 || value > 8) {
      errors.decimalPlaces = 'Decimal Places 須為 0 到 8 的整數'
    }
  }

  return errors
}

export function CurrencyFormModal({ initial, onClose, onSubmit }: CurrencyFormModalProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initial))
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const isEdit = initial !== null

  const handleChange = (field: keyof FormState) => (
    event: ChangeEvent<HTMLInputElement>,
  ) => {
    let value = event.target.value
    if (field === 'code') {
      value = value.toUpperCase()
    }
    setForm((current) => ({ ...current, [field]: value }))
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
        code: form.code,
        name: form.name.trim(),
        nameZh: form.nameZh.trim(),
        symbol: form.symbol.trim(),
        decimalPlaces: Number(form.decimalPlaces),
      })
    } catch (error) {
      // The parent page handles 404/network errors (toast + close/refetch as appropriate);
      // only a 409 on create is surfaced here as an inline field error, keeping the modal open.
      if (error instanceof ApiError && error.status === 409) {
        setErrors((current) => ({ ...current, code: '幣種代碼已存在' }))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={isEdit ? '編輯幣種' : '新增幣種'} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="currency-code">Code</label>
          <input
            id="currency-code"
            type="text"
            value={form.code}
            onChange={handleChange('code')}
            disabled={isEdit}
            maxLength={3}
          />
          {errors.code && <span className="field-error">{errors.code}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-name">Name</label>
          <input
            id="currency-name"
            type="text"
            value={form.name}
            onChange={handleChange('name')}
            maxLength={100}
          />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-name-zh">中文名稱</label>
          <input
            id="currency-name-zh"
            type="text"
            value={form.nameZh}
            onChange={handleChange('nameZh')}
            maxLength={100}
          />
          {errors.nameZh && <span className="field-error">{errors.nameZh}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-symbol">Symbol</label>
          <input
            id="currency-symbol"
            type="text"
            value={form.symbol}
            onChange={handleChange('symbol')}
            maxLength={10}
          />
          {errors.symbol && <span className="field-error">{errors.symbol}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="currency-decimal-places">Decimal Places</label>
          <input
            id="currency-decimal-places"
            type="number"
            min={0}
            max={8}
            value={form.decimalPlaces}
            onChange={handleChange('decimalPlaces')}
          />
          {errors.decimalPlaces && <span className="field-error">{errors.decimalPlaces}</span>}
        </div>

        {errors.general && <div className="field-error field-error--general">{errors.general}</div>}

        <div className="form-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? '儲存中...' : '儲存'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
