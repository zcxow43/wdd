import type { StatusFilter as StatusFilterValue } from '../types/currency'

interface StatusFilterProps {
  value: StatusFilterValue
  onChange: (value: StatusFilterValue) => void
}

const OPTIONS: { value: StatusFilterValue; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
]

export function StatusFilter({ value, onChange }: StatusFilterProps) {
  return (
    <select
      className="status-filter"
      aria-label="狀態篩選"
      value={value}
      onChange={(event) => onChange(event.target.value as StatusFilterValue)}
    >
      {OPTIONS.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}
