import { apiClient } from './client'
import type { Brand } from '../types/brand'

const BASE_PATH = '/api/brands'

export const brandApi = {
  list: (active?: boolean) => {
    const query = active === undefined ? '' : `?active=${active}`
    return apiClient.get<Brand[]>(`${BASE_PATH}${query}`)
  },
  updateActive: (id: number, active: boolean) =>
    apiClient.put<Brand>(`${BASE_PATH}/${id}`, { active }),
}
