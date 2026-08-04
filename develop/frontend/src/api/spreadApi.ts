import { get, post, put, del } from './client'
import type {
  SpreadDefault,
  SpreadDefaultInput,
  SpreadGroup,
  SpreadGroupInput,
  SpreadResolution,
} from '../types/spread'
import type { AuditRequest } from '../audit/types'

const DEFAULT_BASE_PATH = '/api/spread-defaults'
const GROUP_BASE_PATH = '/api/spread-groups'

/**
 * Standalone client for `/api/spread-defaults` (specs/backend/spread.md). `list`
 * reads live, already-approved rows directly; `update` resolves an `AuditRequest`
 * (the backend returns `202` + `AuditRequestResponse`, never mutating
 * `spread_default` directly) — mirroring `currencyPairApi.ts`'s post-audit-
 * integration convention. There is no `create`/`remove` — one row exists per
 * brand from the moment it is seeded and is never created/removed via the API.
 */
export const spreadDefaultApi = {
  list(brandId?: number): Promise<SpreadDefault[]> {
    const qs = brandId !== undefined ? `?brandId=${brandId}` : ''
    return get<SpreadDefault[]>(`${DEFAULT_BASE_PATH}${qs}`)
  },

  update(id: number, input: SpreadDefaultInput): Promise<AuditRequest> {
    return put<AuditRequest>(`${DEFAULT_BASE_PATH}/${id}`, input)
  },
}

/**
 * Standalone client for `/api/spread-groups` (specs/backend/spread.md). `list`/
 * `resolve` read live, already-approved data directly; `create`/`update`/`remove`
 * each resolve an `AuditRequest` (`202`), never mutating `spread_group`/
 * `spread_group_member` directly.
 */
export const spreadGroupApi = {
  list(brandId?: number): Promise<SpreadGroup[]> {
    const qs = brandId !== undefined ? `?brandId=${brandId}` : ''
    return get<SpreadGroup[]>(`${GROUP_BASE_PATH}${qs}`)
  },

  create(input: SpreadGroupInput): Promise<AuditRequest> {
    return post<AuditRequest>(GROUP_BASE_PATH, input)
  },

  update(id: number, input: Partial<SpreadGroupInput>): Promise<AuditRequest> {
    return put<AuditRequest>(`${GROUP_BASE_PATH}/${id}`, input)
  },

  remove(id: number): Promise<AuditRequest> {
    return del<AuditRequest>(`${GROUP_BASE_PATH}/${id}`)
  },

  resolve(currencyPairId: number): Promise<SpreadResolution> {
    return get<SpreadResolution>(`${GROUP_BASE_PATH}/resolve/${currencyPairId}`)
  },
}
