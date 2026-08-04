import type { Brand } from '../types/brand'

interface BrandFilterProps {
  id?: string
  brands: Brand[]
  value: number | 'ALL'
  onChange: (value: number | 'ALL') => void
}

/**
 * Reusable brand-scoped dropdown — options: All / each brand code. Renders only
 * the `<select>` itself (styled via the shared `.filter-input` class); the caller
 * is responsible for its own `.filter-group`/`.filter-label` wrapper, matching
 * how `CurrencyPairPage` inlines its own brand `<select>` today.
 */
export function BrandFilter({ id = 'brand-filter', brands, value, onChange }: BrandFilterProps) {
  return (
    <select
      id={id}
      className="filter-input"
      value={value}
      onChange={(event) => {
        const raw = event.target.value
        onChange(raw === 'ALL' ? 'ALL' : Number(raw))
      }}
    >
      <option value="ALL">全部</option>
      {brands.map((brand) => (
        <option key={brand.id} value={String(brand.id)}>
          {brand.code}
        </option>
      ))}
    </select>
  )
}
