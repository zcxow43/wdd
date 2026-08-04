import { get, put } from './client'
import type { Brand } from '../types/brand'

const BASE_PATH = '/api/brands'

/**
 * Standalone, reusable brand API module — not page-specific.
 * Consumed by the Brand Management page and (per specs/frontend/currency-pair.md)
 * the Currency Pair page's brand filter/picker.
 */
export const brandApi = {
  list(active?: boolean): Promise<Brand[]> {
    const query = active === undefined ? '' : `?active=${active}`
    return get<Brand[]>(`${BASE_PATH}${query}`)
  },

  updateActive(id: number, active: boolean): Promise<Brand> {
    return put<Brand>(`${BASE_PATH}/${id}`, { active })
  },
}
