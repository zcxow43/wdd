import { useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from './Modal'
import { ApiError } from '../api/client'
import type { Currency, CurrencyInput } from '../types/currency'
import './CurrencyFormModal.css'

interface CurrencyFormModalProps {
  mode: 'create' | 'edit'
  initial?: Currency
  onSubmit: (input: CurrencyInput) => Promise<void>
  onClose: () => void
}

interface FormErrors {
  code?: string
  name?: string
  nameZh?: string
  symbol?: string
  decimalPlaces?: string
}

const CODE_PATTERN = /^[A-Z]{3}$/

export function CurrencyFormModal({ mode, initial, onSubmit, onClose }: CurrencyFormModalProps) {
  const [code, setCode] = useState(initial?.code ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  const [nameZh, setNameZh] = useState(initial?.nameZh ?? '')
  const [symbol, setSymbol] = useState(initial?.symbol ?? '')
  const [decimalPlaces, setDecimalPlaces] = useState(initial?.decimalPlaces ?? 2)
  const [errors, setErrors] = useState<FormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function validate(): FormErrors {
    const next: FormErrors = {}
    if (!code.trim()) {
      next.code = '代碼為必填'
    } else if (!CODE_PATTERN.test(code.trim())) {
      next.code = '代碼須為 3 位大寫英文字母'
    }
    if (!name.trim()) {
      next.name = '名稱為必填'
    } else if (name.length > 100) {
      next.name = '名稱長度不可超過 100 字元'
    }
    if (nameZh.length > 100) {
      next.nameZh = '中文名稱長度不可超過 100 字元'
    }
    if (symbol.length > 10) {
      next.symbol = '符號長度不可超過 10 字元'
    }
    if (
      decimalPlaces === null ||
      decimalPlaces === undefined ||
      Number.isNaN(decimalPlaces) ||
      !Number.isInteger(decimalPlaces) ||
      decimalPlaces < 0 ||
      decimalPlaces > 8
    ) {
      next.decimalPlaces = '小數位數須為 0 到 8 之間的整數'
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
        code: code.trim().toUpperCase(),
        name: name.trim(),
        nameZh: nameZh.trim(),
        symbol: symbol.trim(),
        decimalPlaces,
      })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSubmitError('幣種代碼已存在')
      } else if (error instanceof ApiError && error.status === 400) {
        setSubmitError('輸入資料有誤，請確認後再試')
      } else {
        setSubmitError('網路錯誤，請稍後再試')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={mode === 'create' ? '新增幣種' : '編輯幣種'} onClose={onClose}>
      <form className="currency-form" onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label htmlFor="code">代碼</label>
          <input
            id="code"
            value={code}
            disabled={mode === 'edit'}
            maxLength={3}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
            aria-invalid={Boolean(errors.code)}
          />
          {errors.code && <span className="field-error">{errors.code}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="name">名稱</label>
          <input
            id="name"
            value={name}
            maxLength={100}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={Boolean(errors.name)}
          />
          {errors.name && <span className="field-error">{errors.name}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="nameZh">中文名稱</label>
          <input
            id="nameZh"
            value={nameZh}
            maxLength={100}
            onChange={(event) => setNameZh(event.target.value)}
            aria-invalid={Boolean(errors.nameZh)}
          />
          {errors.nameZh && <span className="field-error">{errors.nameZh}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="symbol">符號</label>
          <input
            id="symbol"
            value={symbol}
            maxLength={10}
            onChange={(event) => setSymbol(event.target.value)}
            aria-invalid={Boolean(errors.symbol)}
          />
          {errors.symbol && <span className="field-error">{errors.symbol}</span>}
        </div>

        <div className="form-field">
          <label htmlFor="decimalPlaces">小數位數</label>
          <input
            id="decimalPlaces"
            type="number"
            min={0}
            max={8}
            value={decimalPlaces}
            onChange={(event) => setDecimalPlaces(event.target.valueAsNumber)}
            aria-invalid={Boolean(errors.decimalPlaces)}
          />
          {errors.decimalPlaces && <span className="field-error">{errors.decimalPlaces}</span>}
        </div>

        {submitError && <div className="form-error" role="alert">{submitError}</div>}

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
