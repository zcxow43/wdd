import { apiRequest } from './http'

export interface Brand {
  id: number
  code: string
  name: string
  active: boolean
  createdAt: string
  updatedAt: string
}

export function fetchBrands(): Promise<Brand[]> {
  return apiRequest<Brand[]>('/brands')
}

export function updateBrandActive(
  id: number,
  active: boolean,
): Promise<Brand> {
  return apiRequest<Brand>(`/brands/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ active }),
  })
}
