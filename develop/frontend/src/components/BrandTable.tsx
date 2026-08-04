import type { Brand } from '../types/brand'
import './BrandTable.css'

interface BrandTableProps {
  brands: Brand[]
  loading: boolean
  togglingId: number | null
  onToggle: (brand: Brand) => void
}

export function BrandTable({ brands, loading, togglingId, onToggle }: BrandTableProps) {
  if (loading) {
    return (
      <div className="table-empty" role="status">
        載入中...
      </div>
    )
  }

  if (brands.length === 0) {
    return <div className="table-empty">目前沒有品牌資料</div>
  }

  return (
    <table className="brand-table data-table">
      <thead>
        <tr>
          <th className="col-code">代碼</th>
          <th className="col-name">名稱</th>
          <th className="col-status">狀態</th>
        </tr>
      </thead>
      <tbody>
        {brands.map((brand) => (
          <tr key={brand.id}>
            <td className="col-code currency-code">{brand.code}</td>
            <td className="col-name">{brand.name}</td>
            <td className="col-status">
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={brand.active}
                  disabled={togglingId === brand.id}
                  aria-label={`${brand.code} 狀態`}
                  onChange={() => onToggle(brand)}
                />
                <span className="toggle-track">
                  <span className="toggle-knob" />
                </span>
              </label>
              <span className="toggle-label">{brand.active ? '啟用' : '停用'}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
