import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CurrencyPairDefinitionTable } from './CurrencyPairDefinitionTable'
import type { CurrencyPairDefinition } from '../types/currencyPairDefinition'

const DEFINITIONS: CurrencyPairDefinition[] = [
  {
    id: 1,
    baseCurrencyId: 2,
    baseCurrencyCode: 'USD',
    quoteCurrencyId: 3,
    quoteCurrencyCode: 'JPY',
    forwardPrecision: 2,
    reversePrecision: 5,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
  {
    id: 2,
    baseCurrencyId: 1,
    baseCurrencyCode: 'EUR',
    quoteCurrencyId: 2,
    quoteCurrencyCode: 'USD',
    forwardPrecision: 4,
    reversePrecision: 4,
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  },
]

describe('CurrencyPairDefinitionTable', () => {
  it('renders all columns for each definition row', () => {
    render(<CurrencyPairDefinitionTable definitions={DEFINITIONS} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('JPY')).toBeInTheDocument()
    expect(screen.getByText('EUR')).toBeInTheDocument()
    expect(screen.getAllByText('USD')).toHaveLength(2)
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getAllByText('4')).toHaveLength(2)
  })

  it('renders the empty state when there are no definitions', () => {
    render(<CurrencyPairDefinitionTable definitions={[]} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('目前尚無幣種對主檔')).toBeInTheDocument()
  })

  it('calls onEdit and onDelete with the clicked definition', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<CurrencyPairDefinitionTable definitions={DEFINITIONS} onEdit={onEdit} onDelete={onDelete} />)

    const row = screen.getByText('JPY').closest('tr')!
    await user.click(within(row).getByText('編輯'))
    expect(onEdit).toHaveBeenCalledWith(DEFINITIONS[0])

    await user.click(within(row).getByText('刪除'))
    expect(onDelete).toHaveBeenCalledWith(DEFINITIONS[0])
  })
})
