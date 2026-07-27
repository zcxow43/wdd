import { useCallback, useEffect, useState } from 'react'
import { brandApi } from '../api/brandApi'
import { ApiError } from '../api/client'
import { BrandTable } from '../components/BrandTable'
import { useToast } from '../components/ToastProvider'
import type { Brand } from '../types/brand'
import './BrandPage.css'

const NETWORK_ERROR_MESSAGE = '網路錯誤，請稍後再試'
const NOT_FOUND_MESSAGE = '品牌不存在，請重新整理頁面'
const UPDATE_FAILED_MESSAGE = '更新失敗，請稍後再試'

export function BrandPage() {
  const { showToast } = useToast()
  const [brands, setBrands] = useState<Brand[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [togglingIds, setTogglingIds] = useState<Set<number>>(new Set())

  const fetchBrands = useCallback(async () => {
    setLoading(true)
    setLoadError(false)
    try {
      const data = await brandApi.list()
      setBrands(data)
    } catch {
      setLoadError(true)
      showToast(NETWORK_ERROR_MESSAGE)
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    fetchBrands()
  }, [fetchBrands])

  async function handleToggle(brand: Brand) {
    const previousActive = brand.active
    const nextActive = !previousActive

    setBrands((prev) => prev.map((item) => (item.id === brand.id ? { ...item, active: nextActive } : item)))
    setTogglingIds((prev) => new Set(prev).add(brand.id))

    try {
      const updated = await brandApi.updateActive(brand.id, nextActive)
      setBrands((prev) => prev.map((item) => (item.id === brand.id ? updated : item)))
    } catch (error) {
      setBrands((prev) =>
        prev.map((item) => (item.id === brand.id ? { ...item, active: previousActive } : item)),
      )
      if (error instanceof ApiError && error.status === 404) {
        showToast(NOT_FOUND_MESSAGE)
        await fetchBrands()
      } else if (error instanceof ApiError && error.status === 400) {
        showToast(UPDATE_FAILED_MESSAGE)
      } else {
        showToast(NETWORK_ERROR_MESSAGE)
      }
    } finally {
      setTogglingIds((prev) => {
        const next = new Set(prev)
        next.delete(brand.id)
        return next
      })
    }
  }

  return (
    <div className="brand-page">
      <div className="page-title">
        <h1>Brand Management</h1>
      </div>

      <div className="search-table-card">
        <div className="search-table-header">
          <div className="search-table-title">Brands</div>
        </div>

        <div className="brand-table-wrapper">
          {loading && <div className="table-empty">載入中…</div>}
          {!loading && loadError && (
            <div className="table-empty brand-table-status--error">
              資料載入失敗
              <button type="button" className="btn btn-link" onClick={fetchBrands}>
                重試
              </button>
            </div>
          )}
          {!loading && !loadError && (
            <BrandTable brands={brands} onToggle={handleToggle} togglingIds={togglingIds} />
          )}
        </div>

        <div className="table-footer">
          <div className="total-count">Total {brands.length} items</div>
        </div>
      </div>
    </div>
  )
}
