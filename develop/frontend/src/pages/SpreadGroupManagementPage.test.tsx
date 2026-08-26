import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SpreadGroupManagementPage from './SpreadGroupManagementPage'
import type { AuditRequestSummary } from '../api/audit'
import { fetchAuditRequests } from '../api/audit'
import type { Brand } from '../api/brands'
import { fetchBrands } from '../api/brands'
import type { CurrencyPair } from '../api/currencyPairDefinitions'
import { fetchCurrencyPairsByBrand } from '../api/currencyPairDefinitions'
import { ApiError } from '../api/http'
import type {
  BrandSpread,
  EffectiveSpread,
  SpreadAuditSubmission,
  SpreadGroup,
  SpreadGroupDetail,
} from '../api/spreads'
import {
  addSpreadGroupMembers,
  createSpreadGroup,
  deleteSpreadGroup,
  fetchBrandSpread,
  fetchEffectiveSpreads,
  fetchSpreadGroup,
  fetchSpreadGroups,
  removeSpreadGroupMember,
  updateBrandSpread,
  updateSpreadGroup,
} from '../api/spreads'

vi.mock('../api/brands', () => ({
  fetchBrands: vi.fn(),
}))

vi.mock('../api/audit', () => ({
  fetchAuditRequests: vi.fn(),
}))

vi.mock('../api/currencyPairDefinitions', () => ({
  fetchCurrencyPairsByBrand: vi.fn(),
}))

vi.mock('../api/spreads', () => ({
  fetchBrandSpread: vi.fn(),
  updateBrandSpread: vi.fn(),
  fetchSpreadGroups: vi.fn(),
  fetchSpreadGroup: vi.fn(),
  createSpreadGroup: vi.fn(),
  updateSpreadGroup: vi.fn(),
  deleteSpreadGroup: vi.fn(),
  addSpreadGroupMembers: vi.fn(),
  removeSpreadGroupMember: vi.fn(),
  fetchEffectiveSpreads: vi.fn(),
}))

const mockedFetchBrands = vi.mocked(fetchBrands)
const mockedFetchAuditRequests = vi.mocked(fetchAuditRequests)
const mockedFetchPairsByBrand = vi.mocked(fetchCurrencyPairsByBrand)
const mockedFetchBrandSpread = vi.mocked(fetchBrandSpread)
const mockedUpdateBrandSpread = vi.mocked(updateBrandSpread)
const mockedFetchSpreadGroups = vi.mocked(fetchSpreadGroups)
const mockedFetchSpreadGroup = vi.mocked(fetchSpreadGroup)
const mockedCreateSpreadGroup = vi.mocked(createSpreadGroup)
const mockedUpdateSpreadGroup = vi.mocked(updateSpreadGroup)
const mockedDeleteSpreadGroup = vi.mocked(deleteSpreadGroup)
const mockedAddSpreadGroupMembers = vi.mocked(addSpreadGroupMembers)
const mockedRemoveSpreadGroupMember = vi.mocked(removeSpreadGroupMember)
const mockedFetchEffectiveSpreads = vi.mocked(fetchEffectiveSpreads)

function makeAuditSubmission(
  overrides: Partial<SpreadAuditSubmission> = {},
): SpreadAuditSubmission {
  return {
    auditRequestId: 9001,
    status: 'PENDING',
    entityType: 'BRAND_SPREAD',
    actionType: 'UPDATE',
    entityId: 1,
    summary: 'summary',
    ...overrides,
  }
}

function makeAuditRequestSummary(
  overrides: Partial<AuditRequestSummary> = {},
): AuditRequestSummary {
  return {
    id: 9001,
    entityType: 'BRAND_SPREAD',
    actionType: 'UPDATE',
    entityId: 1,
    brandId: 1,
    summary: 'summary',
    status: 'PENDING',
    requestedBy: 'system',
    requestedAt: '2026-08-23T00:00:00',
    reviewedBy: null,
    reviewedAt: null,
    reviewComment: null,
    applyError: null,
    ...overrides,
  }
}

const BRAND_CODES = ['au', 'moneta', 'pug', 'star', 'um', 'vjp', 'vt']

function makeBrands(): Brand[] {
  return BRAND_CODES.map((code, index) => ({
    id: index + 1,
    code,
    name: code,
    active: true,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }))
}

function makeDefaultSpread(brandId = 1): BrandSpread {
  return {
    brandId,
    brandCode: 'au',
    depositSpreadPercent: 0.0005,
    withdrawalSpreadPercent: 0.0008,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
  }
}

function makeGroup(overrides: Partial<SpreadGroup> = {}): SpreadGroup {
  return {
    id: 1,
    brandId: 1,
    brandCode: 'au',
    name: 'VIP',
    depositSpreadPercent: 0.0002,
    withdrawalSpreadPercent: 0.0003,
    memberCount: 2,
    createdAt: '2026-01-01T00:00:00',
    updatedAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

function makeGroupDetail(
  overrides: Partial<SpreadGroupDetail> = {},
): SpreadGroupDetail {
  return {
    ...makeGroup(),
    members: [
      {
        currencyPairId: 10,
        currencyPairDefinitionId: 1,
        baseCurrencyCode: 'USD',
        quoteCurrencyCode: 'JPY',
        active: true,
      },
      {
        currencyPairId: 11,
        currencyPairDefinitionId: 2,
        baseCurrencyCode: 'EUR',
        quoteCurrencyCode: 'USD',
        active: false,
      },
    ],
    ...overrides,
  }
}

function makeEffective(): EffectiveSpread[] {
  return [
    {
      currencyPairId: 10,
      currencyPairDefinitionId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'JPY',
      brandId: 1,
      brandCode: 'au',
      spreadGroupId: 1,
      spreadGroupName: 'VIP',
      source: 'GROUP',
      depositSpreadPercent: 0.0002,
      withdrawalSpreadPercent: 0.0003,
    },
    {
      currencyPairId: 12,
      currencyPairDefinitionId: 3,
      baseCurrencyCode: 'GBP',
      quoteCurrencyCode: 'USD',
      brandId: 1,
      brandCode: 'au',
      spreadGroupId: null,
      spreadGroupName: null,
      source: 'DEFAULT',
      depositSpreadPercent: 0.0005,
      withdrawalSpreadPercent: 0.0008,
    },
  ]
}

function makePickerPairs(): CurrencyPair[] {
  return [
    {
      id: 10,
      currencyPairDefinitionId: 1,
      baseCurrencyCode: 'USD',
      quoteCurrencyCode: 'JPY',
      brandId: 1,
      brandCode: 'au',
      rateType: 'AUTO',
      rate: null,
      active: true,
      spreadGroupId: 1,
      spreadGroupName: 'VIP',
      depositRate: null,
      withdrawalRate: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
    {
      id: 13,
      currencyPairDefinitionId: 4,
      baseCurrencyCode: 'AUD',
      quoteCurrencyCode: 'USD',
      brandId: 1,
      brandCode: 'au',
      rateType: 'AUTO',
      rate: null,
      active: true,
      spreadGroupId: null,
      spreadGroupName: null,
      depositRate: null,
      withdrawalRate: null,
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    },
  ]
}

function getGroupsSection(): HTMLElement {
  return screen.getByText('點差群組').closest('.sgm-card') as HTMLElement
}

async function renderReady() {
  render(<SpreadGroupManagementPage />)
  await screen.findByText('價差群組管理')
  await screen.findAllByRole('tab')
  await waitFor(() => {
    expect(mockedFetchBrandSpread).toHaveBeenCalled()
  })
}

describe('SpreadGroupManagementPage', () => {
  beforeEach(() => {
    mockedFetchBrands.mockReset()
    mockedFetchAuditRequests.mockReset()
    mockedFetchPairsByBrand.mockReset()
    mockedFetchBrandSpread.mockReset()
    mockedUpdateBrandSpread.mockReset()
    mockedFetchSpreadGroups.mockReset()
    mockedFetchSpreadGroup.mockReset()
    mockedCreateSpreadGroup.mockReset()
    mockedUpdateSpreadGroup.mockReset()
    mockedDeleteSpreadGroup.mockReset()
    mockedAddSpreadGroupMembers.mockReset()
    mockedRemoveSpreadGroupMember.mockReset()
    mockedFetchEffectiveSpreads.mockReset()

    mockedFetchBrands.mockResolvedValue(makeBrands())
    mockedFetchAuditRequests.mockResolvedValue([])
    mockedFetchBrandSpread.mockResolvedValue(makeDefaultSpread())
    mockedFetchSpreadGroups.mockResolvedValue([makeGroup()])
    mockedFetchEffectiveSpreads.mockResolvedValue(makeEffective())
    mockedFetchPairsByBrand.mockResolvedValue(makePickerPairs())
  })

  it('loads brands, selects the first by default, and loads its default spread, groups, and effective spreads', async () => {
    await renderReady()

    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(7)
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true')

    expect(mockedFetchBrandSpread).toHaveBeenCalledWith(1)
    expect(mockedFetchSpreadGroups).toHaveBeenCalledWith(1)
    expect(mockedFetchEffectiveSpreads).toHaveBeenCalledWith(1)

    expect((screen.getByLabelText('入金點差 (%)') as HTMLInputElement).value).toBe(
      '0.0005',
    )
    expect(
      (screen.getByLabelText('出金點差 (%)') as HTMLInputElement).value,
    ).toBe('0.0008')

    expect(within(getGroupsSection()).getByText('VIP')).toBeInTheDocument()
  })

  it('switching brands reloads default spread, group table, and effective spreads', async () => {
    await renderReady()

    await userEvent.click(screen.getByRole('tab', { name: 'moneta' }))

    await waitFor(() => {
      expect(mockedFetchBrandSpread).toHaveBeenCalledWith(2)
    })
    expect(mockedFetchSpreadGroups).toHaveBeenCalledWith(2)
    expect(mockedFetchEffectiveSpreads).toHaveBeenCalledWith(2)
  })

  it('saves 預設點差 via PUT: on 202 reverts the inputs, shows the 審核中 badge and submission toast', async () => {
    mockedUpdateBrandSpread.mockResolvedValue(
      makeAuditSubmission({ entityId: 1 }),
    )

    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '0.001')
    await userEvent.clear(screen.getByLabelText('出金點差 (%)'))
    await userEvent.type(screen.getByLabelText('出金點差 (%)'), '0.002')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedUpdateBrandSpread).toHaveBeenCalledWith(1, {
      depositSpreadPercent: 0.001,
      withdrawalSpreadPercent: 0.002,
    })

    await screen.findByText('已送出審核，核准後才會生效')

    // Reverted to the currently-effective (still-original) values.
    expect((screen.getByLabelText('入金點差 (%)') as HTMLInputElement).value).toBe(
      '0.0005',
    )
    expect(
      (screen.getByLabelText('出金點差 (%)') as HTMLInputElement).value,
    ).toBe('0.0008')
    expect(screen.getByText('審核中')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '儲存' })).toBeDisabled()
    expect(screen.getByLabelText('入金點差 (%)')).toBeDisabled()
  })

  it('shows the already-pending error toast on a 409 default-spread conflict', async () => {
    mockedUpdateBrandSpread.mockRejectedValue(new ApiError(409, 'conflict'))

    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '0.001')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    await screen.findByText('此品牌的預設點差已有待審核的變更')
    expect((screen.getByLabelText('入金點差 (%)') as HTMLInputElement).value).toBe(
      '0.0005',
    )
  })

  it('loads the 預設點差 card already marked 審核中 with disabled controls when a pending request exists', async () => {
    mockedFetchAuditRequests.mockResolvedValue([
      makeAuditRequestSummary({ entityType: 'BRAND_SPREAD', entityId: 1 }),
    ])

    await renderReady()

    await waitFor(() => {
      expect(screen.getByText('審核中')).toBeInTheDocument()
    })
    expect(screen.getByLabelText('入金點差 (%)')).toBeDisabled()
    expect(screen.getByRole('button', { name: '儲存' })).toBeDisabled()
  })

  it('blocks the save request and shows an inline error for a negative or over-8-decimal value', async () => {
    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '-1')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedUpdateBrandSpread).not.toHaveBeenCalled()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '0.123456789')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedUpdateBrandSpread).not.toHaveBeenCalled()
  })

  it('blocks the save request and shows an inline error for a value over 100, but accepts 100 itself', async () => {
    mockedUpdateBrandSpread.mockResolvedValue(
      makeAuditSubmission({ entityId: 1 }),
    )

    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '100.00000001')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedUpdateBrandSpread).not.toHaveBeenCalled()

    await userEvent.clear(screen.getByLabelText('入金點差 (%)'))
    await userEvent.type(screen.getByLabelText('入金點差 (%)'), '100')
    await userEvent.clear(screen.getByLabelText('出金點差 (%)'))
    await userEvent.type(screen.getByLabelText('出金點差 (%)'), '100')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedUpdateBrandSpread).toHaveBeenCalledWith(1, {
      depositSpreadPercent: 100,
      withdrawalSpreadPercent: 100,
    })
    await screen.findByText('已送出審核，核准後才會生效')
  })

  it('新增群組 submits via POST: on 202 no row is added, and a duplicate name shows an inline error without closing the modal', async () => {
    mockedCreateSpreadGroup.mockResolvedValueOnce(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP',
        actionType: 'CREATE',
        entityId: null,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '+ 新增群組' }))
    await userEvent.type(screen.getByLabelText('群組名稱'), 'STD')

    const createModalCard = screen
      .getByText('新增點差群組')
      .closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(
      within(createModalCard).getByRole('button', { name: '儲存' }),
    )

    expect(mockedCreateSpreadGroup).toHaveBeenCalledWith({
      brandId: 1,
      name: 'STD',
      depositSpreadPercent: 0,
      withdrawalSpreadPercent: 0,
    })

    await screen.findByText('已送出審核，核准後才會生效')
    expect(screen.queryByText('新增點差群組')).not.toBeInTheDocument()
    // No row is added — the group does not exist until approved.
    expect(within(getGroupsSection()).queryByText('STD')).not.toBeInTheDocument()

    // duplicate name case
    mockedCreateSpreadGroup.mockRejectedValueOnce(new ApiError(409, 'conflict'))
    await userEvent.click(screen.getByRole('button', { name: '+ 新增群組' }))
    await userEvent.type(screen.getByLabelText('群組名稱'), 'VIP')
    const modalCard = screen.getByText('新增點差群組').closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(within(modalCard).getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('此品牌已有相同名稱的群組'),
    ).toBeInTheDocument()
    expect(screen.getByText('新增點差群組')).toBeInTheDocument()
  })

  it('新增群組 blocks a spread value over 100 with the inline error, but accepts 100 itself', async () => {
    mockedCreateSpreadGroup.mockResolvedValue(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP',
        actionType: 'CREATE',
        entityId: null,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '+ 新增群組' }))
    const createModalCard = screen
      .getByText('新增點差群組')
      .closest('.sgm-modal__card') as HTMLElement
    await userEvent.type(within(createModalCard).getByLabelText('群組名稱'), 'STD')
    await userEvent.clear(within(createModalCard).getByLabelText('入金點差 (%)'))
    await userEvent.type(
      within(createModalCard).getByLabelText('入金點差 (%)'),
      '100.5',
    )
    await userEvent.click(
      within(createModalCard).getByRole('button', { name: '儲存' }),
    )

    expect(
      await screen.findByText('請輸入 0 至 100 之間的百分比數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedCreateSpreadGroup).not.toHaveBeenCalled()

    await userEvent.clear(within(createModalCard).getByLabelText('入金點差 (%)'))
    await userEvent.type(
      within(createModalCard).getByLabelText('入金點差 (%)'),
      '100',
    )
    await userEvent.click(
      within(createModalCard).getByRole('button', { name: '儲存' }),
    )

    expect(mockedCreateSpreadGroup).toHaveBeenCalledWith({
      brandId: 1,
      name: 'STD',
      depositSpreadPercent: 100,
      withdrawalSpreadPercent: 0,
    })
    await screen.findByText('已送出審核，核准後才會生效')
  })

  it('編輯 submits name/spreads via PUT: on 202 the row is left unchanged and marked 審核中', async () => {
    mockedUpdateSpreadGroup.mockResolvedValue(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP',
        actionType: 'UPDATE',
        entityId: 1,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '編輯' }))
    const modalCard = screen.getByText('編輯點差群組').closest('.sgm-modal__card') as HTMLElement
    expect(within(modalCard).queryByText('品牌')).not.toBeInTheDocument()

    const nameInput = within(modalCard).getByLabelText('群組名稱')
    await userEvent.clear(nameInput)
    await userEvent.type(nameInput, 'VIP+')
    await userEvent.click(within(modalCard).getByRole('button', { name: '儲存' }))

    expect(mockedUpdateSpreadGroup).toHaveBeenCalledWith(1, {
      name: 'VIP+',
      depositSpreadPercent: 0.0002,
      withdrawalSpreadPercent: 0.0003,
    })
    await screen.findByText('已送出審核，核准後才會生效')
    // Row values are left unchanged — still 'VIP', not 'VIP+'.
    expect(within(getGroupsSection()).getByText('VIP')).toBeInTheDocument()
    expect(within(getGroupsSection()).queryByText('VIP+')).not.toBeInTheDocument()
    expect(within(getGroupsSection()).getByText('審核中')).toBeInTheDocument()
  })

  it('編輯 already-pending 409 shows the error toast and marks the row 審核中', async () => {
    mockedUpdateSpreadGroup.mockRejectedValue(new ApiError(409, 'conflict'))

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '編輯' }))
    const modalCard = screen.getByText('編輯點差群組').closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(within(modalCard).getByRole('button', { name: '儲存' }))

    await screen.findByText('此群組已有待審核的變更')
    expect(within(getGroupsSection()).getByText('審核中')).toBeInTheDocument()
  })

  it('刪除 confirms with member-count wording: on 202 the row stays on screen marked 審核中', async () => {
    mockedDeleteSpreadGroup.mockResolvedValue(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP',
        actionType: 'DELETE',
        entityId: 1,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '刪除' }))
    await screen.findByText(
      /確定要刪除群組「VIP」嗎？群組內的 2 個品牌幣種對將改為套用預設點差。/,
    )

    const dialog = screen.getByText(/確定要刪除群組/).closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(within(dialog).getByRole('button', { name: '刪除' }))

    expect(mockedDeleteSpreadGroup).toHaveBeenCalledWith(1)
    await screen.findByText('已送出審核，核准後才會生效')
    expect(within(getGroupsSection()).getByText('VIP')).toBeInTheDocument()
    expect(within(getGroupsSection()).getByText('審核中')).toBeInTheDocument()
  })

  it('shows the already-pending error toast on a 409 group-delete conflict', async () => {
    mockedDeleteSpreadGroup.mockRejectedValue(new ApiError(409, 'conflict'))

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '刪除' }))
    const dialog = screen.getByText(/確定要刪除群組/).closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(within(dialog).getByRole('button', { name: '刪除' }))

    await screen.findByText('此群組已有待審核的變更')
    expect(within(getGroupsSection()).getByText('審核中')).toBeInTheDocument()
  })

  it('loads a group row already marked 審核中 with 管理成員/編輯/刪除 disabled when a pending request exists', async () => {
    mockedFetchAuditRequests.mockResolvedValue([
      makeAuditRequestSummary({ entityType: 'SPREAD_GROUP', entityId: 1 }),
    ])

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await waitFor(() => {
      expect(within(getGroupsSection()).getByText('審核中')).toBeInTheDocument()
    })
    expect(
      screen.getByRole('button', { name: '管理成員' }),
    ).toBeDisabled()
    expect(screen.getByRole('button', { name: '編輯' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '刪除' })).toBeDisabled()
  })

  it('管理成員 lists members: 移除 submits via DELETE, leaving the member listed with a 審核中 marker and 成員數 unchanged', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedRemoveSpreadGroupMember.mockResolvedValue(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP_MEMBER',
        actionType: 'UPDATE',
        entityId: 1,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '管理成員' }))
    expect(mockedFetchSpreadGroup).toHaveBeenCalledWith(1)

    const modalCard = screen
      .getByText('管理成員 - VIP')
      .closest('.sgm-modal__card') as HTMLElement
    await within(modalCard).findByText('USD/JPY')
    await within(modalCard).findByText('EUR/USD')

    const rows = within(modalCard).getAllByRole('row').slice(1)
    const usdJpyRow = rows.find(
      (r) => within(r).getAllByRole('cell')[0].textContent?.startsWith('USD/JPY'),
    )!
    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '移除' }))

    expect(mockedRemoveSpreadGroupMember).toHaveBeenCalledWith(1, 10)
    await screen.findByText('已送出審核，核准後才會生效')

    // The member stays listed, marked 審核中, with 移除 disabled.
    expect(within(modalCard).getByText('USD/JPY')).toBeInTheDocument()
    const updatedRow = within(modalCard)
      .getAllByRole('row')
      .slice(1)
      .find((r) => within(r).getAllByRole('cell')[0].textContent?.startsWith('USD/JPY'))!
    expect(within(updatedRow).getByText('審核中')).toBeInTheDocument()
    expect(within(updatedRow).getByRole('button', { name: '移除' })).toBeDisabled()

    // 成員數 badge in the group table behind the modal stays at 2.
    const tables = screen.getAllByRole('table')
    const groupsTable = tables.find((t) => within(t).queryByText('VIP'))!
    const groupRow = within(groupsTable)
      .getAllByRole('row')
      .find((r) => within(r).queryByText('VIP'))!
    expect(within(groupRow).getByText('2')).toBeInTheDocument()
  })

  it('移除 already-pending 409 shows the error toast', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedRemoveSpreadGroupMember.mockRejectedValue(new ApiError(409, 'conflict'))

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '管理成員' }))
    const modalCard = screen
      .getByText('管理成員 - VIP')
      .closest('.sgm-modal__card') as HTMLElement
    await within(modalCard).findByText('USD/JPY')

    const rows = within(modalCard).getAllByRole('row').slice(1)
    const usdJpyRow = rows.find(
      (r) => within(r).getAllByRole('cell')[0].textContent?.startsWith('USD/JPY'),
    )!
    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '移除' }))

    await screen.findByText('此群組已有待審核的變更')
  })

  it('加入 picker offers only unassigned pairs; joining submits via POST leaving the member list and 成員數 untouched', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedAddSpreadGroupMembers.mockResolvedValue(
      makeAuditSubmission({
        entityType: 'SPREAD_GROUP_MEMBER',
        actionType: 'UPDATE',
        entityId: 1,
      }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '管理成員' }))
    const modalCard = screen
      .getByText('管理成員 - VIP')
      .closest('.sgm-modal__card') as HTMLElement

    // only pair 13 (AUD/USD, spreadGroupId null) should appear as an option;
    // pair 10 (USD/JPY) is already assigned to this group and must not appear
    // in the picker list.
    const picker = await within(modalCard).findByText('加入品牌幣種對')
    const pickerSection = picker.closest('.sgm-member-picker') as HTMLElement
    expect(within(pickerSection).getByText('AUD/USD')).toBeInTheDocument()
    expect(within(pickerSection).queryByText('USD/JPY')).not.toBeInTheDocument()

    await userEvent.click(within(pickerSection).getByRole('checkbox'))
    await userEvent.click(within(modalCard).getByRole('button', { name: '加入' }))

    expect(mockedAddSpreadGroupMembers).toHaveBeenCalledWith(1, [13])
    await screen.findByText('已送出審核，核准後才會生效')

    // The member list and 成員數 are not updated — the batch joins only
    // once the request is approved.
    const memberTable = within(modalCard).getByRole('table')
    expect(within(memberTable).queryByText('AUD/USD')).not.toBeInTheDocument()
    expect(within(pickerSection).getByText('AUD/USD')).toBeInTheDocument()
    const tables = screen.getAllByRole('table')
    const groupsTable = tables.find((t) => within(t).queryByText('VIP'))!
    const groupRow = within(groupsTable)
      .getAllByRole('row')
      .find((r) => within(r).queryByText('VIP'))!
    expect(within(groupRow).getByText('2')).toBeInTheDocument()
  })

  it('a business 409 from 加入 (pair claimed elsewhere) shows the error toast and re-fetches both lists', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedAddSpreadGroupMembers.mockRejectedValue(
      new ApiError(409, 'conflict', { error: 'conflict', conflicts: [] }),
    )

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '管理成員' }))
    const modalCard = screen
      .getByText('管理成員 - VIP')
      .closest('.sgm-modal__card') as HTMLElement

    const picker = await within(modalCard).findByText('加入品牌幣種對')
    const pickerSection = picker.closest('.sgm-member-picker') as HTMLElement
    await userEvent.click(within(pickerSection).getByRole('checkbox'))

    mockedFetchSpreadGroup.mockClear()
    mockedFetchPairsByBrand.mockClear()

    await userEvent.click(within(modalCard).getByRole('button', { name: '加入' }))

    await screen.findByText('部分幣種對已屬於其他群組，請重新整理')
    await waitFor(() => {
      expect(mockedFetchSpreadGroup).toHaveBeenCalledWith(1)
      expect(mockedFetchPairsByBrand).toHaveBeenCalledWith(1)
    })
  })

  it('an already-pending 409 from 加入 (no conflicts body) shows the generic error toast', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedAddSpreadGroupMembers.mockRejectedValue(new ApiError(409, 'conflict'))

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '管理成員' }))
    const modalCard = screen
      .getByText('管理成員 - VIP')
      .closest('.sgm-modal__card') as HTMLElement

    const picker = await within(modalCard).findByText('加入品牌幣種對')
    const pickerSection = picker.closest('.sgm-member-picker') as HTMLElement
    await userEvent.click(within(pickerSection).getByRole('checkbox'))
    await userEvent.click(within(modalCard).getByRole('button', { name: '加入' }))

    await screen.findByText('此群組已有待審核的變更')
  })

  it('shows the empty-state message when the brand has no groups', async () => {
    mockedFetchSpreadGroups.mockResolvedValue([])

    await renderReady()

    expect(
      await screen.findByText(
        '此品牌尚無點差群組，點擊「+ 新增群組」建立第一個',
      ),
    ).toBeInTheDocument()
  })

  it('生效點差總覽 shows the group name badge for GROUP source and 預設 for DEFAULT source', async () => {
    await renderReady()

    const effectiveHeading = await screen.findByText('生效點差總覽')
    const card = effectiveHeading.closest('.sgm-card') as HTMLElement

    await within(card).findByText('USD/JPY')
    expect(within(card).getByText('VIP')).toBeInTheDocument()
    expect(within(card).getByText('GBP/USD')).toBeInTheDocument()
    expect(within(card).getByText('預設')).toBeInTheDocument()
  })

  it('shows an inline error with a retry button when the brand list fails to load', async () => {
    mockedFetchBrands.mockReset()
    mockedFetchBrands.mockRejectedValueOnce(new Error('network error'))

    render(<SpreadGroupManagementPage />)

    expect(
      await screen.findByText('載入品牌清單失敗，請稍後再試。'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()

    mockedFetchBrands.mockResolvedValueOnce(makeBrands())
    await userEvent.click(screen.getByRole('button', { name: '重試' }))

    await screen.findAllByRole('tab')
  })
})
