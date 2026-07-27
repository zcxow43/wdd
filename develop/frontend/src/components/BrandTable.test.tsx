import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrandTable } from './BrandTable'
import type { Brand } from '../types/brand'

const BRANDS: Brand[] = [
  {
    id: 1,
    code: 'AU',
    name: 'AU',
    active: true,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
  {
    id: 2,
    code: 'PUG',
    name: 'PUG',
    active: false,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

describe('BrandTable', () => {
  it('renders all brands with code, name, and status label', () => {
    render(<BrandTable brands={BRANDS} onToggle={vi.fn()} togglingIds={new Set()} />)

    expect(screen.getAllByText('AU')).toHaveLength(2)
    expect(screen.getAllByText('PUG')).toHaveLength(2)

    const auRow = screen.getByLabelText('AU 狀態').closest('tr')!
    expect(within(auRow).getByText('啟用')).toBeInTheDocument()

    const pugRow = screen.getByLabelText('PUG 狀態').closest('tr')!
    expect(within(pugRow).getByText('停用')).toBeInTheDocument()
  })

  it('renders the empty state when there are no brands', () => {
    render(<BrandTable brands={[]} onToggle={vi.fn()} togglingIds={new Set()} />)

    expect(screen.getByText('目前沒有品牌資料')).toBeInTheDocument()
  })

  it('calls onToggle with the clicked brand', async () => {
    const user = userEvent.setup()
    const onToggle = vi.fn()
    render(<BrandTable brands={BRANDS} onToggle={onToggle} togglingIds={new Set()} />)

    await user.click(screen.getByLabelText('AU 狀態'))
    expect(onToggle).toHaveBeenCalledWith(BRANDS[0])
  })

  it('disables the toggle for a brand currently being updated', () => {
    render(<BrandTable brands={BRANDS} onToggle={vi.fn()} togglingIds={new Set([1])} />)

    expect(screen.getByLabelText('AU 狀態')).toBeDisabled()
    expect(screen.getByLabelText('PUG 狀態')).not.toBeDisabled()
  })

  it('does not render add/edit/delete controls', () => {
    render(<BrandTable brands={BRANDS} onToggle={vi.fn()} togglingIds={new Set()} />)

    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '+ Add' })).not.toBeInTheDocument()
  })
})
