import { useCallback, useEffect, useState } from 'react'
import {
  type CurrencyPairDefinition,
  createCurrencyPairDefinition,
  deleteCurrencyPairDefinition,
  fetchCurrencyPairDefinitions,
  fetchCurrencyPairsByDefinition,
  updateCurrencyPairDefinitionPrecision,
} from '../api/currencyPairDefinitions'
import { type Currency, fetchCurrencies } from '../api/currencies'
import { ApiError } from '../api/http'
import Toast from '../components/Toast'
import './CurrencyPairManagementPage.css'

type CountState = 'loading' | 'error' | { active: number; total: number }

interface FormState {
  baseCurrencyId: string
  quoteCurrencyId: string
  precision: string
}

interface FormErrors {
  baseCurrencyId?: string
  quoteCurrencyId?: string
  precision?: string
}

const EMPTY_FORM: FormState = {
  baseCurrencyId: '',
  quoteCurrencyId: '',
  precision: '4',
}

function validateForm(form: FormState, isEdit: boolean): FormErrors {
  const errors: FormErrors = {}

  if (!isEdit) {
    if (!form.baseCurrencyId) {
      errors.baseCurrencyId = '基準幣為必填'
    }
    if (!form.quoteCurrencyId) {
      errors.quoteCurrencyId = '報價幣為必填'
    }
    if (
      form.baseCurrencyId &&
      form.quoteCurrencyId &&
      form.baseCurrencyId === form.quoteCurrencyId
    ) {
      errors.quoteCurrencyId = '基準幣與報價幣不可相同'
    }
  }

  if (form.precision.trim() === '') {
    errors.precision = '精度為必填'
  } else {
    const value = Number(form.precision)
    if (
      !Number.isInteger(value) ||
      value < 0 ||
      value > 8 ||
      !/^-?\d+$/.test(form.precision.trim())
    ) {
      errors.precision = '精度須為 0–8 之間的整數'
    }
  }

  return errors
}

function countLabel(state: CountState | undefined): string {
  if (!state || state === 'loading') {
    return '…'
  }
  if (state === 'error') {
    return '—'
  }
  return `${state.active} / ${state.total}`
}

function CurrencyPairManagementPage() {
  const [definitions, setDefinitions] = useState<CurrencyPairDefinition[] | null>(
    null,
  )
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [currencies, setCurrencies] = useState<Currency[]>([])
  const [counts, setCounts] = useState<Record<number, CountState>>({})
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [editingDefinition, setEditingDefinition] =
    useState<CurrencyPairDefinition | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [formErrors, setFormErrors] = useState<FormErrors>({})
  const [submitting, setSubmitting] = useState(false)

  const [deletingDefinition, setDeletingDefinition] =
    useState<CurrencyPairDefinition | null>(null)
  const [deleting, setDeleting] = useState(false)

  const loadCount = useCallback((definitionId: number) => {
    setCounts((current) => ({ ...current, [definitionId]: 'loading' }))
    fetchCurrencyPairsByDefinition(definitionId)
      .then((pairs) => {
        const active = pairs.filter((p) => p.active).length
        setCounts((current) => ({
          ...current,
          [definitionId]: { active, total: pairs.length },
        }))
      })
      .catch(() => {
        setCounts((current) => ({ ...current, [definitionId]: 'error' }))
      })
  }, [])

  const loadDefinitions = useCallback(() => {
    setLoading(true)
    setLoadError(false)
    fetchCurrencyPairDefinitions()
      .then((data) => {
        setDefinitions(data)
        data.forEach((definition) => loadCount(definition.id))
      })
      .catch(() => {
        setLoadError(true)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [loadCount])

  useEffect(() => {
    loadDefinitions()
  }, [loadDefinitions])

  useEffect(() => {
    fetchCurrencies()
      .then((data) => setCurrencies(data))
      .catch(() => setCurrencies([]))
  }, [])

  const openCreateModal = () => {
    setModalMode('create')
    setEditingDefinition(null)
    setForm(EMPTY_FORM)
    setFormErrors({})
  }

  const openEditModal = (definition: CurrencyPairDefinition) => {
    setModalMode('edit')
    setEditingDefinition(definition)
    setForm({
      baseCurrencyId: String(definition.baseCurrencyId),
      quoteCurrencyId: String(definition.quoteCurrencyId),
      precision: String(definition.precision),
    })
    setFormErrors({})
  }

  const closeModal = () => {
    setModalMode(null)
    setEditingDefinition(null)
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
    const precision = Number(form.precision)

    if (isEdit && editingDefinition) {
      updateCurrencyPairDefinitionPrecision(editingDefinition.id, precision)
        .then((updated) => {
          setDefinitions((current) =>
            current?.map((d) => (d.id === updated.id ? updated : d)) ??
            current,
          )
          closeModal()
          setToastMessage('幣種對已更新')
        })
        .catch(() => {
          setToastMessage('儲存失敗，請稍後再試')
        })
        .finally(() => {
          setSubmitting(false)
        })
    } else {
      createCurrencyPairDefinition({
        baseCurrencyId: Number(form.baseCurrencyId),
        quoteCurrencyId: Number(form.quoteCurrencyId),
        precision,
      })
        .then((created) => {
          const { currencyPairs, ...definition } = created
          setDefinitions((current) =>
            current ? [...current, definition] : [definition],
          )
          setCounts((current) => ({
            ...current,
            [definition.id]: { active: 0, total: currencyPairs.length },
          }))
          closeModal()
          setToastMessage(
            `幣種對已新增，已為 ${currencyPairs.length} 個品牌建立品牌幣種對`,
          )
        })
        .catch((error: unknown) => {
          if (error instanceof ApiError && error.status === 409) {
            setFormErrors((current) => ({
              ...current,
              quoteCurrencyId: '此幣種對已存在',
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
    if (!deletingDefinition) {
      return
    }
    const target = deletingDefinition
    setDeleting(true)
    deleteCurrencyPairDefinition(target.id)
      .then(() => {
        setDefinitions((current) =>
          current?.filter((d) => d.id !== target.id) ?? current,
        )
        setCounts((current) => {
          const next = { ...current }
          delete next[target.id]
          return next
        })
        setDeletingDefinition(null)
        setToastMessage('幣種對已刪除')
      })
      .catch((error: unknown) => {
        setDeletingDefinition(null)
        if (error instanceof ApiError && error.status === 409) {
          setToastMessage(error.message)
        } else {
          setToastMessage('刪除失敗，請稍後再試')
        }
        loadCount(target.id)
      })
      .finally(() => {
        setDeleting(false)
      })
  }

  return (
    <div className="currency-pair-page">
      <div className="currency-pair-page__breadcrumb">
        匯率中心 &gt; 幣別對管理
      </div>
      <div className="currency-pair-page__header">
        <h1 className="currency-pair-page__title">幣別對管理</h1>
        <button
          type="button"
          className="currency-pair-page__btn currency-pair-page__btn--primary"
          onClick={openCreateModal}
        >
          + 新增幣種對
        </button>
      </div>

      {loading && <p className="currency-pair-page__status">載入中...</p>}

      {!loading && loadError && (
        <div className="currency-pair-page__error">
          <p>載入幣種對定義清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadDefinitions}>
            重試
          </button>
        </div>
      )}

      {!loading && !loadError && definitions && (
        <table className="currency-pair-table">
          <thead>
            <tr>
              <th>基準幣</th>
              <th>報價幣</th>
              <th>精度</th>
              <th>啟用品牌數</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {definitions.map((definition) => {
              const countState = counts[definition.id]
              const hasActive =
                typeof countState === 'object' && countState.active > 0
              const countUnknown =
                countState === undefined || countState === 'loading'
              const deleteDisabled = hasActive || countUnknown
              const badgeVariant = hasActive ? 'active' : 'inactive'

              return (
                <tr key={definition.id}>
                  <td className="currency-pair-table__code-cell">
                    {definition.baseCurrencyCode}
                  </td>
                  <td className="currency-pair-table__code-cell">
                    {definition.quoteCurrencyCode}
                  </td>
                  <td>{definition.precision}</td>
                  <td>
                    <span
                      className={`currency-pair-table__badge currency-pair-table__badge--${badgeVariant}`}
                    >
                      {countLabel(countState)}
                    </span>
                  </td>
                  <td>
                    <div className="currency-pair-table__actions">
                      <button
                        type="button"
                        className="currency-pair-page__btn currency-pair-page__btn--secondary"
                        onClick={() => openEditModal(definition)}
                      >
                        編輯
                      </button>
                      <button
                        type="button"
                        className="currency-pair-page__btn currency-pair-page__btn--danger"
                        disabled={deleteDisabled}
                        title={
                          hasActive
                            ? '需先於「品牌幣種對」頁面關閉所有品牌幣種對才能刪除'
                            : undefined
                        }
                        onClick={() => setDeletingDefinition(definition)}
                      >
                        刪除
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {modalMode && (
        <div className="currency-pair-modal__overlay">
          <div className="currency-pair-modal__card">
            <h2 className="currency-pair-modal__title">
              {isEdit ? '編輯幣種對' : '新增幣種對'}
            </h2>
            <form onSubmit={handleSubmit}>
              <div className="currency-pair-form__field">
                <label
                  className="currency-pair-form__label"
                  htmlFor="baseCurrencyId"
                >
                  基準幣
                </label>
                {isEdit ? (
                  <input
                    id="baseCurrencyId"
                    className="currency-pair-form__input"
                    type="text"
                    value={editingDefinition?.baseCurrencyCode ?? ''}
                    disabled
                    readOnly
                  />
                ) : (
                  <select
                    id="baseCurrencyId"
                    className="currency-pair-form__input"
                    value={form.baseCurrencyId}
                    onChange={(e) =>
                      handleFieldChange('baseCurrencyId', e.target.value)
                    }
                  >
                    <option value="">請選擇</option>
                    {currencies.map((currency) => (
                      <option key={currency.id} value={currency.id}>
                        {currency.code} - {currency.name}
                      </option>
                    ))}
                  </select>
                )}
                {formErrors.baseCurrencyId && (
                  <p className="currency-pair-form__error">
                    {formErrors.baseCurrencyId}
                  </p>
                )}
              </div>

              <div className="currency-pair-form__field">
                <label
                  className="currency-pair-form__label"
                  htmlFor="quoteCurrencyId"
                >
                  報價幣
                </label>
                {isEdit ? (
                  <input
                    id="quoteCurrencyId"
                    className="currency-pair-form__input"
                    type="text"
                    value={editingDefinition?.quoteCurrencyCode ?? ''}
                    disabled
                    readOnly
                  />
                ) : (
                  <select
                    id="quoteCurrencyId"
                    className="currency-pair-form__input"
                    value={form.quoteCurrencyId}
                    onChange={(e) =>
                      handleFieldChange('quoteCurrencyId', e.target.value)
                    }
                  >
                    <option value="">請選擇</option>
                    {currencies.map((currency) => (
                      <option key={currency.id} value={currency.id}>
                        {currency.code} - {currency.name}
                      </option>
                    ))}
                  </select>
                )}
                {formErrors.quoteCurrencyId && (
                  <p className="currency-pair-form__error">
                    {formErrors.quoteCurrencyId}
                  </p>
                )}
              </div>

              <div className="currency-pair-form__field">
                <label
                  className="currency-pair-form__label"
                  htmlFor="precision"
                >
                  精度
                </label>
                <input
                  id="precision"
                  className="currency-pair-form__input"
                  type="number"
                  min={0}
                  max={8}
                  value={form.precision}
                  onChange={(e) =>
                    handleFieldChange('precision', e.target.value)
                  }
                />
                {formErrors.precision && (
                  <p className="currency-pair-form__error">
                    {formErrors.precision}
                  </p>
                )}
              </div>

              <div className="currency-pair-modal__actions">
                <button
                  type="button"
                  className="currency-pair-page__btn currency-pair-page__btn--secondary"
                  onClick={closeModal}
                  disabled={submitting}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="currency-pair-page__btn currency-pair-page__btn--primary"
                  disabled={!isFormValid || submitting}
                >
                  儲存
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deletingDefinition && (
        <div className="currency-pair-modal__overlay">
          <div className="currency-pair-modal__card">
            <p className="currency-pair-modal__confirm-text">
              確定要刪除幣種對「{deletingDefinition.baseCurrencyCode}/
              {deletingDefinition.quoteCurrencyCode}」嗎？此操作無法復原。
            </p>
            <div className="currency-pair-modal__actions">
              <button
                type="button"
                className="currency-pair-page__btn currency-pair-page__btn--secondary"
                onClick={() => setDeletingDefinition(null)}
                disabled={deleting}
              >
                取消
              </button>
              <button
                type="button"
                className="currency-pair-page__btn currency-pair-page__btn--danger"
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

export default CurrencyPairManagementPage
