import type { Brand } from '../types/brand'
import './BrandTable.css'

interface BrandTableProps {
  brands: Brand[]
  onToggle: (brand: Brand) => void
  togglingIds: Set<number>
}

export function BrandTable({ brands, onToggle, togglingIds }: BrandTableProps) {
  if (brands.length === 0) {
    return <div className="table-empty">目前沒有品牌資料</div>
  }

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th style={{ width: 100 }}>代碼</th>
          <th>名稱</th>
          <th style={{ width: 120 }}>狀態</th>
        </tr>
      </thead>
      <tbody>
        {brands.map((brand) => (
          <tr key={brand.id}>
            <td>
              <span className="currency-code">{brand.code}</span>
            </td>
            <td>{brand.name}</td>
            <td>
              <label className="toggle-switch">
                <input
                  type="checkbox"
                  checked={brand.active}
                  disabled={togglingIds.has(brand.id)}
                  onChange={() => onToggle(brand)}
                  aria-label={`${brand.code} 狀態`}
                />
                <span className="toggle-switch-track" aria-hidden="true" />
                <span className="toggle-switch-label">{brand.active ? '啟用' : '停用'}</span>
              </label>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
