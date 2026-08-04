import { get, post, put, del } from './client'
import type {
  CurrencyPairDefinition,
  CurrencyPairDefinitionCreateInput,
  CurrencyPairDefinitionUpdateInput,
} from '../types/currencyPairDefinition'

const BASE_PATH = '/api/currency-pair-definitions'

export interface CurrencyPairDefinitionListParams {
  baseCurrencyId?: number
  quoteCurrencyId?: number
}

/**
 * Client for `/api/currency-pair-definitions` (specs/backend/currency-pair-definition.md).
 * This is a brand-agnostic, direct-apply (non-audit-gated) CRUD resource — unlike
 * `currencyPairApi.ts`, every call here resolves the entity directly, never an
 * `AuditRequest`. Creating a definition fans it out to every brand on the backend;
 * deleting one is blocked (`409`) while any brand's pair for that direction is
 * still active.
 */
export const currencyPairDefinitionApi = {
  list(params: CurrencyPairDefinitionListParams = {}): Promise<CurrencyPairDefinition[]> {
    const query = new URLSearchParams()
    if (params.baseCurrencyId !== undefined) {
      query.set('baseCurrencyId', String(params.baseCurrencyId))
    }
    if (params.quoteCurrencyId !== undefined) {
      query.set('quoteCurrencyId', String(params.quoteCurrencyId))
    }
    const qs = query.toString()
    return get<CurrencyPairDefinition[]>(`${BASE_PATH}${qs ? `?${qs}` : ''}`)
  },

  create(input: CurrencyPairDefinitionCreateInput): Promise<CurrencyPairDefinition> {
    return post<CurrencyPairDefinition>(BASE_PATH, input)
  },

  update(id: number, input: CurrencyPairDefinitionUpdateInput): Promise<CurrencyPairDefinition> {
    return put<CurrencyPairDefinition>(`${BASE_PATH}/${id}`, input)
  },

  remove(id: number): Promise<void> {
    return del<void>(`${BASE_PATH}/${id}`)
  },
}
