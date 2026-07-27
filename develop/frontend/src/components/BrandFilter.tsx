import type { Brand } from '../types/brand'

interface BrandFilterProps {
  brands: Brand[]
  value: number | 'ALL'
  onChange: (value: number | 'ALL') => void
}

export function BrandFilter({ brands, value, onChange }: BrandFilterProps) {
  return (
    <select
      className="status-filter"
      aria-label="品牌篩選"
      value={value}
      onChange={(event) => {
        const raw = event.target.value
        onChange(raw === 'ALL' ? 'ALL' : Number(raw))
      }}
    >
      <option value="ALL">All</option>
      {brands.map((brand) => (
        <option key={brand.id} value={brand.id}>
          {brand.code}
        </option>
      ))}
    </select>
  )
}
