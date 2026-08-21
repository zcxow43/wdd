import { useCallback, useEffect, useState } from 'react'
import {
  type Currency,
  createCurrency,
  deleteCurrency,
  fetchCurrencies,
  updateCurrency,
} from '../api/currencies'
import { ApiError } from '../api/http'
import Toast from '../components/Toast'
import './CurrencyManagementPage.css'

const CODE_PATTERN = /^[A-Z]{3}$/

interface FormState {
  code: string
  name: string
  symbol: string
  decimalPlaces: string
}

interface FormErrors {
  code?: string
  name?: string
  symbol?: string
  decimalPlaces?: string
}

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  symbol: '',
  decimalPlaces: '',
}

function validateForm(form: FormState, isEdit: boolean): FormErrors {
  const errors: FormErrors = {}

  if (!isEdit) {
    if (!form.code.trim()) {
      errors.code = '代碼為必填'
    } else if (!CODE_PATTERN.test(form.code.trim())) {
      errors.code = '代碼須為 3 個大寫字母'
    }
  }

  if (!form.name.trim()) {
    errors.name = '名稱為必填'
  }

  if (!form.symbol.trim()) {
    errors.symbol = '符號為必填'
  }

  if (form.decimalPlaces.trim() === '') {
    errors.decimalPlaces = '小數位數為必填'
  } else {
    const value = Number(form.decimalPlaces)
    if (
      !Number.isInteger(value) ||
      value < 0 ||
      value > 8 ||
      !/^-?\d+$/.test(form.decimalPlaces.trim())
    ) {
      errors.decimalPlaces = '小數位數須為 0–8 之間的整數'
    }
  }

  return errors
}

function CurrencyManagementPage() {
  const [currencies, setCurrencies] = useState<Currency[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [editingCurrency, setEditingCurrency] = useState<Currency | null>(
    null,
  )
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)

  const [deletingCurrency, setDeletingCurrency] = useState<Currency | null>(
    null,
  )
  const [deleting, setDeleting] = useState(false)

  const loadCurrencies = useCallback(() => {
    setLoading(true)
    setLoadError(false)
    fetchCurrencies()
      .then((data) => {
        setCurrencies(data)
      })
      .catch(() => {
        setLoadError(true)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    loadCurrencies()
  }, [loadCurrencies])

  const openCreateModal = () => {
    setModalMode('create')
    setEditingCurrency(null)
    setForm(EMPTY_FORM)
    setFormErrors({})
  }

  const openEditModal = (currency: Currency) => {
    setModalMode('edit')
    setEditingCurrency(currency)
    setForm({
      code: currency.code,
      name: currency.name,
      symbol: currency.symbol,
      decimalPlaces: String(currency.decimalPlaces),
    })
    setFormErrors({})
  }

  const closeModal = () => {
    setModalMode(null)
    setEditingCurrency(null)
    setForm(EMPTY_FORM)
    setFormErrors({})
  }

  const handleFieldChange = (field: keyof FormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const isEdit = modalMode === 'edit'
  const currentErrors = validateForm(form, isEdit)
  const isFormValid = Object.keys(currentErrors).length === 0

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const errors = validateForm(form, isEdit)
    setFormErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    setSubmitting(true)
    const decimalPlaces = Number(form.decimalPlaces)

    if (isEdit && editingCurrency) {
      updateCurrency(editingCurrency.id, {
        name: form.name.trim(),
        symbol: form.symbol.trim(),
        decimalPlaces,
      })
        .then((updated) => {
          setCurrencies((current) =>
            current?.map((c) => (c.id === updated.id ? updated : c)) ??
            current,
          )
          closeModal()
          setToastMessage('幣種已更新')
        })
        .catch(() => {
          setToastMessage('儲存失敗，請稍後再試')
        })
        .finally(() => {
          setSubmitting(false)
        })
    } else {
      createCurrency({
        code: form.code.trim(),
        name: form.name.trim(),
        symbol: form.symbol.trim(),
        decimalPlaces,
      })
        .then((created) => {
          setCurrencies((current) =>
            current ? [...current, created] : [created],
          )
          closeModal()
          setToastMessage('幣種已新增')
        })
        .catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 409) {
            setFormErrors((current) => ({
              ...current,
              code: '此代碼已存在',
            }))
          } else {
            setToastMessage('儲存失敗，請稍後再試')
          }
        })
        .finally(() => {
          setSubmitting(false)
        })
    }
  }

  const confirmDelete = () => {
    if (!deletingCurrency) {
      return
    }
    const target = deletingCurrency
    setDeleting(true)
    deleteCurrency(target.id)
      .then(() => {
        setCurrencies((current) =>
          current?.filter((c) => c.id !== target.id) ?? current,
        )
        setDeletingCurrency(null)
        setToastMessage('幣種已刪除')
      })
      .catch(() => {
        setDeletingCurrency(null)
        setToastMessage('刪除失敗，請稍後再試')
      })
      .finally(() => {
        setDeleting(false)
      })
  }

  return (
    <div className="currency-page">
      <div className="currency-page__breadcrumb">匯率中心 &gt; 幣別管理</div>
      <div className="currency-page__header">
        <h1 className="currency-page__title">幣別管理</h1>
        <button
          type="button"
          className="currency-page__btn currency-page__btn--primary"
          onClick={openCreateModal}
        >
          + 新增幣種
        </button>
      </div>

      {loading && <p className="currency-page__status">載入中...</p>}

      {!loading && loadError && (
        <div className="currency-page__error">
          <p>載入幣種清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadCurrencies}>
            重試
          </button>
        </div>
      )}

      {!loading && !loadError && currencies && (
        <table className="currency-table">
          <thead>
            <tr>
              <th>代碼</th>
              <th>名稱</th>
              <th>符號</th>
              <th>小數位數</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {currencies.map((currency) => (
              <tr key={currency.id}>
                <td className="currency-table__code-cell">{currency.code}</td>
                <td>{currency.name}</td>
                <td>{currency.symbol}</td>
                <td>{currency.decimalPlaces}</td>
                <td>
                  <div className="currency-table__actions">
                    <button
                      type="button"
                      className="currency-page__btn currency-page__btn--secondary"
                      onClick={() => openEditModal(currency)}
                    >
                      編輯
                    </button>
                    <button
                      type="button"
                      className="currency-page__btn currency-page__btn--danger"
                      onClick={() => setDeletingCurrency(currency)}
                    >
                      刪除
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {modalMode && (
        <div className="currency-modal__overlay">
          <div className="currency-modal__card">
            <h2 className="currency-modal__title">
              {isEdit ? '編輯幣種' : '新增幣種'}
            </h2>
            <form onSubmit={handleSubmit}>
              <div className="currency-form__field">
                <label className="currency-form__label" htmlFor="code">
                  代碼
                </label>
                <input
                  id="code"
                  className="currency-form__input"
                  type="text"
                  value={form.code}
                  disabled={isEdit}
                  onChange={(e) =>
                    handleFieldChange('code', e.target.value.toUpperCase())
                  }
                />
                {formErrors.code && (
                  <p className="currency-form__error">{formErrors.code}</p>
                )}
              </div>

              <div className="currency-form__field">
                <label className="currency-form__label" htmlFor="name">
                  名稱
                </label>
                <input
                  id="name"
                  className="currency-form__input"
                  type="text"
                  value={form.name}
                  onChange={(e) => handleFieldChange('name', e.target.value)}
                />
                {formErrors.name && (
                  <p className="currency-form__error">{formErrors.name}</p>
                )}
              </div>

              <div className="currency-form__field">
                <label className="currency-form__label" htmlFor="symbol">
                  符號
                </label>
                <input
                  id="symbol"
                  className="currency-form__input"
                  type="text"
                  value={form.symbol}
                  onChange={(e) =>
                    handleFieldChange('symbol', e.target.value)
                  }
                />
                {formErrors.symbol && (
                  <p className="currency-form__error">{formErrors.symbol}</p>
                )}
              </div>

              <div className="currency-form__field">
                <label
                  className="currency-form__label"
                  htmlFor="decimalPlaces"
                >
                  小數位數
                </label>
                <input
                  id="decimalPlaces"
                  className="currency-form__input"
                  type="number"
                  min={0}
                  max={8}
                  value={form.decimalPlaces}
                  onChange={(e) =>
                    handleFieldChange('decimalPlaces', e.target.value)
                  }
                />
                {formErrors.decimalPlaces && (
                  <p className="currency-form__error">
                    {formErrors.decimalPlaces}
                  </p>
                )}
              </div>

              <div className="currency-modal__actions">
                <button
                  type="button"
                  className="currency-page__btn currency-page__btn--secondary"
                  onClick={closeModal}
                  disabled={submitting}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="currency-page__btn currency-page__btn--primary"
                  disabled={!isFormValid || submitting}
                >
                  儲存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deletingCurrency && (
        <div className="currency-modal__overlay">
          <div className="currency-modal__card">
            <p className="currency-modal__confirm-text">
              確定要刪除幣種「{deletingCurrency.code} {deletingCurrency.name}
              」嗎？此操作無法復原。
            </p>
            <div className="currency-modal__actions">
              <button
                type="button"
                className="currency-page__btn currency-page__btn--secondary"
                onClick={() => setDeletingCurrency(null)}
                disabled={deleting}
              >
                取消
              </button>
              <button
                type="button"
                className="currency-page__btn currency-page__btn--danger"
                onClick={confirmDelete}
                disabled={deleting}
              >
                刪除
              </button>
            </div>
          </div>
        </div>
      )}

      {toastMessage && (
        <Toast
          message={toastMessage}
          onDismiss={() => setToastMessage(null)}
        />
      )}
    </div>
  )
}

export default CurrencyManagementPage
