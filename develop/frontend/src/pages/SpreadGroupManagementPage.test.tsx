import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SpreadGroupManagementPage from './SpreadGroupManagementPage'
import type { Brand } from '../api/brands'
import { fetchBrands } from '../api/brands'
import type { CurrencyPair } from '../api/currencyPairDefinitions'
import { fetchCurrencyPairsByBrand } from '../api/currencyPairDefinitions'
import { ApiError } from '../api/http'
import type {
  BrandSpread,
  EffectiveSpread,
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
    depositSpread: 0.0005,
    withdrawalSpread: 0.0008,
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
    depositSpread: 0.0002,
    withdrawalSpread: 0.0003,
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
      depositSpread: 0.0002,
      withdrawalSpread: 0.0003,
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
      depositSpread: 0.0005,
      withdrawalSpread: 0.0008,
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

    expect((screen.getByLabelText('入金點差') as HTMLInputElement).value).toBe(
      '0.0005',
    )
    expect(
      (screen.getByLabelText('出金點差') as HTMLInputElement).value,
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

  it('saves 預設點差 via PUT and shows the success toast with server values', async () => {
    mockedUpdateBrandSpread.mockResolvedValue({
      ...makeDefaultSpread(),
      depositSpread: 0.001,
      withdrawalSpread: 0.002,
    })

    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.type(screen.getByLabelText('入金點差'), '0.001')
    await userEvent.clear(screen.getByLabelText('出金點差'))
    await userEvent.type(screen.getByLabelText('出金點差'), '0.002')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(mockedUpdateBrandSpread).toHaveBeenCalledWith(1, {
      depositSpread: 0.001,
      withdrawalSpread: 0.002,
    })

    await screen.findByText('預設點差已更新')
    expect((screen.getByLabelText('入金點差') as HTMLInputElement).value).toBe(
      '0.001',
    )
  })

  it('blocks the save request and shows an inline error for a negative or over-8-decimal value', async () => {
    await renderReady()

    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.type(screen.getByLabelText('入金點差'), '-1')

    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('請輸入 0 或以上的數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedUpdateBrandSpread).not.toHaveBeenCalled()

    await userEvent.clear(screen.getByLabelText('入金點差'))
    await userEvent.type(screen.getByLabelText('入金點差'), '0.123456789')
    await userEvent.click(screen.getByRole('button', { name: '儲存' }))

    expect(
      await screen.findByText('請輸入 0 或以上的數值，小數點後最多 8 位'),
    ).toBeInTheDocument()
    expect(mockedUpdateBrandSpread).not.toHaveBeenCalled()
  })

  it('creates a group via POST with memberCount 0, and a duplicate name shows an inline error without closing the modal', async () => {
    mockedCreateSpreadGroup.mockResolvedValueOnce(
      makeGroup({ id: 2, name: 'STD', memberCount: 0 }),
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
      depositSpread: 0,
      withdrawalSpread: 0,
    })

    await screen.findByText('點差群組已新增')
    await screen.findByText('STD')

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

  it('編輯 updates name/spreads via PUT without an editable brand field', async () => {
    mockedUpdateSpreadGroup.mockResolvedValue(
      makeGroup({ name: 'VIP+', depositSpread: 0.0004 }),
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
      depositSpread: 0.0002,
      withdrawalSpread: 0.0003,
    })
    await screen.findByText('點差群組已更新')
    await screen.findByText('VIP+')
  })

  it('刪除 confirms with member-count wording, deletes via DELETE, and removes the row', async () => {
    mockedDeleteSpreadGroup.mockResolvedValue(undefined)

    await renderReady()
    await within(getGroupsSection()).findByText('VIP')

    await userEvent.click(screen.getByRole('button', { name: '刪除' }))
    await screen.findByText(
      /確定要刪除群組「VIP」嗎？群組內的 2 個品牌幣種對將改為套用預設點差。/,
    )

    const dialog = screen.getByText(/確定要刪除群組/).closest('.sgm-modal__card') as HTMLElement
    await userEvent.click(within(dialog).getByRole('button', { name: '刪除' }))

    expect(mockedDeleteSpreadGroup).toHaveBeenCalledWith(1)
    await screen.findByText('點差群組已刪除')
    expect(within(getGroupsSection()).queryByText('VIP')).not.toBeInTheDocument()
  })

  it('管理成員 lists members and 移除 removes one and decrements 成員數', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedRemoveSpreadGroupMember.mockResolvedValue(undefined)

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
      (r) => within(r).getAllByRole('cell')[0].textContent === 'USD/JPY',
    )!
    await userEvent.click(within(usdJpyRow).getByRole('button', { name: '移除' }))

    expect(mockedRemoveSpreadGroupMember).toHaveBeenCalledWith(1, 10)
    await screen.findByText('已從群組移除')
    expect(within(modalCard).queryByText('USD/JPY')).not.toBeInTheDocument()

    // 成員數 badge in the group table behind the modal is decremented from 2 to 1
    const tables = screen.getAllByRole('table')
    const groupsTable = tables.find((t) => within(t).queryByText('VIP'))!
    const groupRow = within(groupsTable)
      .getAllByRole('row')
      .find((r) => within(r).queryByText('VIP'))!
    expect(within(groupRow).getByText('1')).toBeInTheDocument()
  })

  it('加入 picker offers only unassigned pairs and joins every checked pair in one call', async () => {
    mockedFetchSpreadGroup.mockResolvedValue(makeGroupDetail())
    mockedAddSpreadGroupMembers.mockResolvedValue({
      ...makeGroupDetail(),
      memberCount: 3,
      members: [
        ...makeGroupDetail().members,
        {
          currencyPairId: 13,
          currencyPairDefinitionId: 4,
          baseCurrencyCode: 'AUD',
          quoteCurrencyCode: 'USD',
          active: true,
        },
      ],
    })

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

    // after joining, the picker's own re-fetch reflects that pair 13 is no
    // longer unassigned
    mockedFetchPairsByBrand.mockResolvedValueOnce(
      makePickerPairs().map((p) =>
        p.id === 13 ? { ...p, spreadGroupId: 1, spreadGroupName: 'VIP' } : p,
      ),
    )

    await userEvent.click(within(pickerSection).getByRole('checkbox'))
    await userEvent.click(within(modalCard).getByRole('button', { name: '加入' }))

    expect(mockedAddSpreadGroupMembers).toHaveBeenCalledWith(1, [13])
    await screen.findByText('已加入群組')

    const memberTable = within(modalCard).getByRole('table')
    await within(memberTable).findByText('AUD/USD')
    await waitFor(() => {
      expect(
        within(modalCard).queryByText('此品牌沒有可加入的幣種對'),
      ).toBeInTheDocument()
    })
  })

  it('a 409 from 加入 shows the error toast and re-fetches both lists', async () => {
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

    mockedFetchSpreadGroup.mockClear()
    mockedFetchPairsByBrand.mockClear()

    await userEvent.click(within(modalCard).getByRole('button', { name: '加入' }))

    await screen.findByText('部分幣種對已屬於其他群組，請重新整理')
    await waitFor(() => {
      expect(mockedFetchSpreadGroup).toHaveBeenCalledWith(1)
      expect(mockedFetchPairsByBrand).toHaveBeenCalledWith(1)
    })
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
