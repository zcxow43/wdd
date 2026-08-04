/**
 * Formats an ISO-ish datetime string (e.g. "2026-07-29T10:00:00") for display as
 * "2026-07-29 10:00". Falls back to the raw value if it doesn't look like a
 * recognizable timestamp, rather than throwing.
 */
export function formatDateTime(value: string): string {
  if (!value) {
    return '—'
  }
  return value.replace('T', ' ').slice(0, 16)
}
