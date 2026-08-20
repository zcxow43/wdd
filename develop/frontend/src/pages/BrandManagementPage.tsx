import { useCallback, useEffect, useState } from 'react'
import { type Brand, fetchBrands, updateBrandActive } from '../api/brands'
import ToggleSwitch from '../components/ToggleSwitch'
import Toast from '../components/Toast'
import './BrandManagementPage.css'

function BrandManagementPage() {
  const [brands, setBrands] = useState<Brand[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [updatingId, setUpdatingId] = useState<number | null>(null)
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const loadBrands = useCallback(() => {
    setLoading(true)
    setLoadError(false)
    fetchBrands()
      .then((data) => {
        setBrands(data)
      })
      .catch(() => {
        setLoadError(true)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    loadBrands()
  }, [loadBrands])

  const handleToggle = (brand: Brand) => {
    const previousActive = brand.active
    const nextActive = !previousActive

    setBrands((current) =>
      current?.map((b) =>
        b.id === brand.id ? { ...b, active: nextActive } : b,
      ) ?? current,
    )
    setUpdatingId(brand.id)

    updateBrandActive(brand.id, nextActive)
      .then((updated) => {
        setBrands((current) =>
          current?.map((b) => (b.id === updated.id ? updated : b)) ?? current,
        )
      })
      .catch(() => {
        setBrands((current) =>
          current?.map((b) =>
            b.id === brand.id ? { ...b, active: previousActive } : b,
          ) ?? current,
        )
        setToastMessage('更新品牌狀態失敗，請稍後再試')
      })
      .finally(() => {
        setUpdatingId(null)
      })
  }

  return (
    <div className="brand-page">
      <div className="brand-page__breadcrumb">匯率中心 &gt; 品牌管理</div>
      <h1 className="brand-page__title">品牌管理</h1>

      {loading && <p className="brand-page__status">載入中...</p>}

      {!loading && loadError && (
        <div className="brand-page__error">
          <p>載入品牌清單失敗，請稍後再試。</p>
          <button type="button" onClick={loadBrands}>
            重試
          </button>
        </div>
      )}

      {!loading && !loadError && brands && (
        <table className="brand-table">
          <thead>
            <tr>
              <th>品牌代碼</th>
              <th>品牌名稱</th>
              <th>狀態</th>
            </tr>
          </thead>
          <tbody>
            {brands.map((brand) => (
              <tr key={brand.id}>
                <td className="brand-table__code-cell">{brand.code}</td>
                <td>{brand.name}</td>
                <td>
                  <ToggleSwitch
                    checked={brand.active}
                    disabled={updatingId === brand.id}
                    isPending={updatingId === brand.id}
                    onChange={() => handleToggle(brand)}
                    label={brand.active ? '啟用' : '停用'}
                    pendingLabel="更新中..."
                    ariaLabel={`切換 ${brand.code} 啟用狀態`}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
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

export default BrandManagementPage
