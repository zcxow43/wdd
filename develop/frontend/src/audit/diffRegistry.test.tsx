import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { hasDiffRenderer, registerDiffRenderer, renderAuditDiff, renderGenericDiff } from './diffRegistry'

describe('renderGenericDiff', () => {
  it('lists every key present in either before or after', () => {
    render(
      <>{renderGenericDiff({ code: 'USD', name: 'Dollar' }, { code: 'USD', name: 'US Dollar' })}</>,
    )

    expect(screen.getByText('code')).toBeInTheDocument()
    expect(screen.getByText('name')).toBeInTheDocument()
    expect(screen.getByText('Dollar')).toBeInTheDocument()
    expect(screen.getByText('US Dollar')).toBeInTheDocument()
  })

  it('marks a key present in both with a different value as changed', () => {
    render(<>{renderGenericDiff({ status: 'A' }, { status: 'B' })}</>)

    const row = screen.getByText('status').closest('tr')
    expect(row).toHaveClass('generic-diff-row-changed')
  })

  it('does not mark unchanged keys as changed', () => {
    render(<>{renderGenericDiff({ status: 'A' }, { status: 'A' })}</>)

    const row = screen.getByText('status').closest('tr')
    expect(row).not.toHaveClass('generic-diff-row-changed')
  })

  it('shows a dash for keys only present on one side', () => {
    render(<>{renderGenericDiff({ onlyBefore: 'x' }, { onlyAfter: 'y' })}</>)

    expect(screen.getByText('onlyBefore')).toBeInTheDocument()
    expect(screen.getByText('onlyAfter')).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(2)
  })

  it('renders a placeholder when both sides are null', () => {
    render(<>{renderGenericDiff(null, null)}</>)

    expect(screen.getByText('（無資料）')).toBeInTheDocument()
  })

  it('serializes nested object values', () => {
    render(<>{renderGenericDiff({ meta: { a: 1 } }, { meta: { a: 1 } })}</>)

    expect(screen.getAllByText('{"a":1}')).toHaveLength(2)
  })
})

describe('renderAuditDiff', () => {
  it('falls back to the generic renderer for an entityType with no registered renderer', () => {
    render(<>{renderAuditDiff('SOME_UNREGISTERED_TYPE', { foo: 'bar' }, { foo: 'baz' })}</>)

    expect(screen.getByText('foo')).toBeInTheDocument()
    expect(screen.getByText('bar')).toBeInTheDocument()
    expect(screen.getByText('baz')).toBeInTheDocument()
  })

  it('delegates to a registered renderer for its entityType', () => {
    registerDiffRenderer('TEST_ENTITY_TYPE_A', (before, after) => (
      <div data-testid="custom-diff">
        custom: {String(before?.x)} -&gt; {String(after?.x)}
      </div>
    ))

    render(<>{renderAuditDiff('TEST_ENTITY_TYPE_A', { x: 1 }, { x: 2 })}</>)

    expect(screen.getByTestId('custom-diff')).toHaveTextContent('custom: 1 -> 2')
  })

  it('does not use a registered renderer for a different entityType', () => {
    registerDiffRenderer('TEST_ENTITY_TYPE_B', () => <div data-testid="should-not-render" />)

    render(<>{renderAuditDiff('TEST_ENTITY_TYPE_C', { foo: 'bar' }, { foo: 'bar' })}</>)

    expect(screen.queryByTestId('should-not-render')).not.toBeInTheDocument()
    expect(screen.getByText('foo')).toBeInTheDocument()
  })
})

describe('hasDiffRenderer', () => {
  it('returns false for an entityType with no registered renderer', () => {
    expect(hasDiffRenderer('SOME_OTHER_UNREGISTERED_TYPE')).toBe(false)
  })

  it('returns true once a renderer has been registered for that entityType', () => {
    registerDiffRenderer('TEST_ENTITY_TYPE_HAS_RENDERER', () => <div />)

    expect(hasDiffRenderer('TEST_ENTITY_TYPE_HAS_RENDERER')).toBe(true)
  })
})
