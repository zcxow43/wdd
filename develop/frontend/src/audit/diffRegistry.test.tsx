import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { hasDiffRenderer, registerDiffRenderer, renderAuditDiff, renderGenericDiff } from './diffRegistry'

describe('renderGenericDiff', () => {
  it('lists the union of before/after keys as a two-column table', () => {
    render(
      <>{renderGenericDiff({ code: 'AU', rate: 30 }, { code: 'AU', rate: 32 })}</>,
    )

    expect(screen.getByText('code')).toBeInTheDocument()
    expect(screen.getAllByText('AU')).toHaveLength(2)
    expect(screen.getByText('30')).toBeInTheDocument()
    expect(screen.getByText('32')).toBeInTheDocument()
  })

  it('highlights keys present in both with a different value', () => {
    render(<>{renderGenericDiff({ rate: 30 }, { rate: 32 })}</>)

    const row = screen.getByText('rate').closest('tr')
    expect(row).toHaveClass('audit-generic-diff-row--changed')
  })

  it('does not highlight a key with an unchanged value', () => {
    render(<>{renderGenericDiff({ code: 'AU' }, { code: 'AU' })}</>)

    const row = screen.getByText('code').closest('tr')
    expect(row).not.toHaveClass('audit-generic-diff-row--changed')
  })

  it('renders — for a key missing from one side', () => {
    render(<>{renderGenericDiff({ code: 'AU' }, { code: 'AU', rate: 32 })}</>)

    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('handles null before/after gracefully', () => {
    render(<>{renderGenericDiff(null, { code: 'AU' })}</>)

    expect(screen.getByText('code')).toBeInTheDocument()
  })

  it('shows an empty-data message when both sides are empty', () => {
    render(<>{renderGenericDiff({}, {})}</>)

    expect(screen.getByText('無資料')).toBeInTheDocument()
  })
})

describe('renderAuditDiff', () => {
  it('falls back to renderGenericDiff for an entityType with no registered renderer', () => {
    render(<>{renderAuditDiff('SOME_UNKNOWN_ENTITY', { foo: 'bar' }, { foo: 'baz' })}</>)

    expect(screen.getByText('foo')).toBeInTheDocument()
    expect(screen.getByText('bar')).toBeInTheDocument()
    expect(screen.getByText('baz')).toBeInTheDocument()
  })

  it('uses a registered renderer for its entityType instead of the generic fallback', () => {
    registerDiffRenderer('TEST_ENTITY_TYPE', (before, after) => (
      <div data-testid="custom-renderer">
        custom:{JSON.stringify(before)}:{JSON.stringify(after)}
      </div>
    ))

    render(<>{renderAuditDiff('TEST_ENTITY_TYPE', { a: 1 }, { a: 2 })}</>)

    expect(screen.getByTestId('custom-renderer')).toBeInTheDocument()
    expect(screen.queryByText('a')).not.toBeInTheDocument()
  })
})

describe('hasDiffRenderer', () => {
  it('returns false for an entityType with no registered renderer', () => {
    expect(hasDiffRenderer('SOME_OTHER_UNKNOWN_ENTITY')).toBe(false)
  })

  it('returns true once a renderer has been registered for an entityType', () => {
    registerDiffRenderer('HAS_RENDERER_TEST_ENTITY', () => null)
    expect(hasDiffRenderer('HAS_RENDERER_TEST_ENTITY')).toBe(true)
  })
})
