import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { BrandTable } from './BrandTable'
import type { Brand } from '../types/brand'

const AU: Brand = {
  id: 1,
  code: 'AU',
  name: 'AU',
  active: true,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

const PUG: Brand = {
  id: 2,
  code: 'PUG',
  name: 'PUG',
  active: false,
  createdAt: '2025-01-01T00:00:00',
  updatedAt: '2025-01-01T00:00:00',
}

describe('BrandTable', () => {
  it('renders all rows with code, name, and correct 啟用/停用 label', () => {
    render(<BrandTable brands={[AU, PUG]} loading={false} togglingId={null} onToggle={vi.fn()} />)

    expect(screen.getByText('代碼')).toBeInTheDocument()
    expect(screen.getByText('名稱')).toBeInTheDocument()
    expect(screen.getByText('狀態')).toBeInTheDocument()

    expect(screen.getAllByText('AU').length).toBe(2)
    expect(screen.getAllByText('PUG').length).toBe(2)
    expect(screen.getByText('啟用')).toBeInTheDocument()
    expect(screen.getByText('停用')).toBeInTheDocument()
  })

  it('renders an empty state when there are no brands', () => {
    render(<BrandTable brands={[]} loading={false} togglingId={null} onToggle={vi.fn()} />)

    expect(screen.getByText('目前沒有品牌資料')).toBeInTheDocument()
  })

  it('renders a loading state', () => {
    render(<BrandTable brands={[]} loading={true} togglingId={null} onToggle={vi.fn()} />)

    expect(screen.getByText('載入中...')).toBeInTheDocument()
  })

  it('calls onToggle with the clicked brand', async () => {
    const onToggle = vi.fn()
    render(<BrandTable brands={[AU]} loading={false} togglingId={null} onToggle={onToggle} />)

    await userEvent.click(screen.getByLabelText('AU 狀態'))

    expect(onToggle).toHaveBeenCalledWith(AU)
  })

  it('disables the toggle for the row currently being toggled', () => {
    render(<BrandTable brands={[AU, PUG]} loading={false} togglingId={1} onToggle={vi.fn()} />)

    expect(screen.getByLabelText('AU 狀態')).toBeDisabled()
    expect(screen.getByLabelText('PUG 狀態')).not.toBeDisabled()
  })

  it('renders no add/edit/delete controls', () => {
    render(<BrandTable brands={[AU, PUG]} loading={false} togglingId={null} onToggle={vi.fn()} />)

    expect(screen.queryByText('+ Add')).not.toBeInTheDocument()
    expect(screen.queryByText('編輯')).not.toBeInTheDocument()
    expect(screen.queryByText('刪除')).not.toBeInTheDocument()
  })
})
