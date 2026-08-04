import { useCallback, useEffect, useState } from 'react'
import { BrandTable } from '../components/BrandTable'
import { useToast } from '../components/ToastProvider'
import { brandApi } from '../api/brandApi'
import { ApiError } from '../api/client'
import type { Brand } from '../types/brand'
import './BrandPage.css'

export function BrandPage() {
  const { showToast } = useToast()
  const [brands, setBrands] = useState<Brand[]>([])
  const [loading, setLoading] = useState(true)
  const [togglingId, setTogglingId] = useState<number | null>(null)

  const fetchBrands = useCallback(async () => {
    setLoading(true)
    try {
      const data = await brandApi.list()
      setBrands(data)
    } catch {
      showToast('網路錯誤，請稍後再試')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    fetchBrands()
  }, [fetchBrands])

  const handleToggle = async (brand: Brand) => {
    const nextActive = !brand.active
    setTogglingId(brand.id)
    setBrands((current) =>
      current.map((item) => (item.id === brand.id ? { ...item, active: nextActive } : item)),
    )

    try {
      const updated = await brandApi.updateActive(brand.id, nextActive)
      setBrands((current) => current.map((item) => (item.id === brand.id ? updated : item)))
    } catch (error) {
      setBrands((current) =>
        current.map((item) => (item.id === brand.id ? { ...item, active: brand.active } : item)),
      )
      if (error instanceof ApiError && error.status === 404) {
        showToast('品牌不存在，請重新整理頁面')
        await fetchBrands()
      } else if (error instanceof ApiError && error.status === 400) {
        showToast('更新失敗，請稍後再試')
      } else {
        showToast('網路錯誤，請稍後再試')
      }
    } finally {
      setTogglingId(null)
    }
  }

  return (
    <div className="brand-page">
      <h1 className="page-title">品牌管理</h1>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">
            <span>品牌列表</span>
          </div>
        </div>

        <BrandTable brands={brands} loading={loading} togglingId={togglingId} onToggle={handleToggle} />

        <div className="table-footer">
          <div className="total-count">Total {brands.length} items</div>
        </div>
      </div>
    </div>
  )
}
