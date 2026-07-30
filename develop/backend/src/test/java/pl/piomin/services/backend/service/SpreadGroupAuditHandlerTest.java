package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.audit.AuditRequest;
import pl.piomin.services.backend.audit.AuditRequestMapper;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.DuplicatePendingSpreadGroupCreateException;
import pl.piomin.services.backend.exception.DuplicateSpreadGroupMemberException;
import pl.piomin.services.backend.exception.InvalidSpreadException;
import pl.piomin.services.backend.exception.InvalidSpreadGroupMemberException;
import pl.piomin.services.backend.exception.SpreadGroupNameExistsException;
import pl.piomin.services.backend.exception.SpreadGroupNotFoundException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMemberMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.model.SpreadGroupMember;

/**
 * Unit tests for {@link SpreadGroupAuditHandler}: snapshotOf/validate/apply/summarize,
 * exercised with real {@link SpreadGroupValidator}/{@link SpreadGroupService} backed
 * by mocked mappers, matching the layering convention used by
 * {@code CurrencyPairAuditHandlerTest}.
 */
@ExtendWith(MockitoExtension.class)
class SpreadGroupAuditHandlerTest {

    @Mock
    private SpreadGroupMapper spreadGroupMapper;

    @Mock
    private SpreadGroupMemberMapper spreadGroupMemberMapper;

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private AuditRequestMapper auditRequestMapper;

    private SpreadGroupAuditHandler handler;

    @BeforeEach
    void setUp() {
        SpreadGroupValidator validator = new SpreadGroupValidator(brandMapper, currencyPairMapper, spreadGroupMapper);
        SpreadGroupService service = new SpreadGroupService(spreadGroupMapper, spreadGroupMemberMapper,
                spreadDefaultMapper, currencyPairMapper);
        handler = new SpreadGroupAuditHandler(service, validator, spreadGroupMemberMapper, auditRequestMapper,
                new ObjectMapper());
    }

    private Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private CurrencyPair samplePair(Long id, Long brandId, String baseCode, String quoteCode) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setBrandId(brandId);
        pair.setBaseCurrencyCode(baseCode);
        pair.setQuoteCurrencyCode(quoteCode);
        return pair;
    }

    private SpreadGroup sampleGroup(Long id, Long brandId, String brandCode, String name) {
        SpreadGroup group = new SpreadGroup();
        group.setId(id);
        group.setBrandId(brandId);
        group.setBrandCode(brandCode);
        group.setName(name);
        group.setDepositSpread(new BigDecimal("0.1"));
        group.setWithdrawSpread(new BigDecimal("0.2"));
        return group;
    }

    private SpreadGroupMember sampleMember(Long id, Long groupId, Long pairId, String baseCode, String quoteCode) {
        SpreadGroupMember member = new SpreadGroupMember();
        member.setId(id);
        member.setSpreadGroupId(groupId);
        member.setCurrencyPairId(pairId);
        member.setBaseCurrencyCode(baseCode);
        member.setQuoteCurrencyCode(quoteCode);
        return member;
    }

    private Map<String, Object> sampleAfter() {
        Map<String, Object> after = new HashMap<>();
        after.put("brandId", 1L);
        after.put("name", "Group A");
        after.put("depositSpread", new BigDecimal("0.1"));
        after.put("withdrawSpread", new BigDecimal("0.2"));
        after.put("currencyPairIds", new ArrayList<>(List.of(3L, 4L)));
        return after;
    }

    private void stubHappyPathValidation() {
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));
        when(spreadGroupMapper.findByBrandAndName(1L, "Group A")).thenReturn(null);
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 1L, "USD", "JPY"));
        when(currencyPairMapper.findById(4L)).thenReturn(samplePair(4L, 1L, "USD", "EUR"));
    }

    // --- entityType ----------------------------------------------------------

    @Test
    void entityType_isSpreadGroup() {
        assertThat(handler.entityType()).isEqualTo("SPREAD_GROUP");
    }

    // --- snapshotOf ------------------------------------------------------------

    @Test
    void snapshotOf_returnsSnapshotWithMembers_whenFound() {
        when(spreadGroupMapper.findById(10L)).thenReturn(sampleGroup(10L, 1L, "AU", "Group A"));
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(
                sampleMember(1L, 10L, 3L, "USD", "JPY"), sampleMember(2L, 10L, 4L, "USD", "EUR")));

        Map<String, Object> snapshot = handler.snapshotOf(10L);

        assertThat(snapshot.get("brandCode")).isEqualTo("AU");
        assertThat(snapshot.get("name")).isEqualTo("Group A");
        assertThat(snapshot.get("currencyPairIds")).isEqualTo(List.of(3L, 4L));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) snapshot.get("members");
        assertThat(members).hasSize(2);
        assertThat(members.get(0).get("baseCurrencyCode")).isEqualTo("USD");
    }

    @Test
    void snapshotOf_throwsNotFound_whenMissing() {
        when(spreadGroupMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> handler.snapshotOf(999L))
                .isInstanceOf(SpreadGroupNotFoundException.class);
    }

    // --- validate ---------------------------------------------------------------

    @Test
    void validate_succeeds_andEnrichesSnapshot() {
        Map<String, Object> after = sampleAfter();
        stubHappyPathValidation();

        handler.validate(AuditActionType.CREATE, null, after);

        assertThat(after.get("brandCode")).isEqualTo("AU");
        assertThat(after.get("currencyPairIds")).isEqualTo(List.of(3L, 4L));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) after.get("members");
        assertThat(members).hasSize(2);
        assertThat(members.get(0).get("baseCurrencyCode")).isEqualTo("USD");
        assertThat(members.get(1).get("quoteCurrencyCode")).isEqualTo("EUR");
    }

    @Test
    void validate_throwsNotFound_whenBrandMissing() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(1L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void validate_throws400_whenNameBlank() {
        Map<String, Object> after = sampleAfter();
        after.put("name", "   ");
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_throws400_whenNameTooLong() {
        Map<String, Object> after = sampleAfter();
        after.put("name", "x".repeat(101));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_throws400_whenDepositSpreadNegative() {
        Map<String, Object> after = sampleAfter();
        after.put("depositSpread", new BigDecimal("-1"));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_throws400_whenDuplicateCurrencyPairId() {
        Map<String, Object> after = sampleAfter();
        after.put("currencyPairIds", new ArrayList<>(List.of(3L, 3L)));
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(DuplicateSpreadGroupMemberException.class);
    }

    @Test
    void validate_throwsNotFound_whenCurrencyPairMissing() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));
        when(currencyPairMapper.findById(3L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void validate_throws400_whenCurrencyPairBrandMismatch() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 99L, "USD", "JPY"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidSpreadGroupMemberException.class);
    }

    @Test
    void validate_throwsConflict_whenLiveNameAlreadyExistsInBrand() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 1L, "USD", "JPY"));
        when(currencyPairMapper.findById(4L)).thenReturn(samplePair(4L, 1L, "USD", "EUR"));
        when(spreadGroupMapper.findByBrandAndName(1L, "Group A")).thenReturn(sampleGroup(99L, 1L, "AU", "Group A"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(SpreadGroupNameExistsException.class);
    }

    @Test
    void validate_update_excludesSelf_fromNameUniquenessCheck() {
        Map<String, Object> after = sampleAfter();
        stubHappyPathValidation();
        when(spreadGroupMapper.findByBrandAndName(1L, "Group A")).thenReturn(sampleGroup(10L, 1L, "AU", "Group A"));

        handler.validate(AuditActionType.UPDATE, 10L, after);
        // no exception - the only match is the group being updated itself
    }

    @Test
    void validate_update_freezesCurrentLiveMembership_whenCurrencyPairIdsOmitted() {
        Map<String, Object> after = sampleAfter();
        after.remove("currencyPairIds");
        when(brandMapper.findById(1L)).thenReturn(sampleBrand(1L, "AU"));
        when(spreadGroupMapper.findByBrandAndName(1L, "Group A")).thenReturn(null);
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(
                sampleMember(1L, 10L, 3L, "USD", "JPY"), sampleMember(2L, 10L, 4L, "USD", "EUR")));
        when(currencyPairMapper.findById(3L)).thenReturn(samplePair(3L, 1L, "USD", "JPY"));
        when(currencyPairMapper.findById(4L)).thenReturn(samplePair(4L, 1L, "USD", "EUR"));

        handler.validate(AuditActionType.UPDATE, 10L, after);

        assertThat(after.get("currencyPairIds")).isEqualTo(List.of(3L, 4L));
    }

    @Test
    void validate_create_throwsDuplicate_whenPendingCreateExistsForSameBrandAndName() {
        Map<String, Object> after = sampleAfter();
        stubHappyPathValidation();

        AuditRequest pendingCreate = new AuditRequest();
        pendingCreate.setEntityType("SPREAD_GROUP");
        pendingCreate.setActionType("CREATE");
        pendingCreate.setStatus("PENDING");
        pendingCreate.setAfterSnapshot("{\"brandId\":1,\"name\":\"Group A\"}");
        when(auditRequestMapper.findAll("SPREAD_GROUP", "PENDING", "CREATE")).thenReturn(List.of(pendingCreate));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(DuplicatePendingSpreadGroupCreateException.class);
    }

    @Test
    void validate_create_succeeds_whenPendingCreateExistsForDifferentName() {
        Map<String, Object> after = sampleAfter();
        stubHappyPathValidation();

        AuditRequest pendingCreate = new AuditRequest();
        pendingCreate.setEntityType("SPREAD_GROUP");
        pendingCreate.setActionType("CREATE");
        pendingCreate.setStatus("PENDING");
        pendingCreate.setAfterSnapshot("{\"brandId\":1,\"name\":\"Group B\"}");
        when(auditRequestMapper.findAll("SPREAD_GROUP", "PENDING", "CREATE")).thenReturn(List.of(pendingCreate));

        handler.validate(AuditActionType.CREATE, null, after);
        // no exception
    }

    @Test
    void validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime() {
        Map<String, Object> after = sampleAfter();
        after.put("brandCode", "AU");
        stubHappyPathValidation();

        handler.validate(AuditActionType.CREATE, null, after);

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    @Test
    void validate_update_neverChecksPendingCreateDuplicate() {
        Map<String, Object> after = sampleAfter();
        stubHappyPathValidation();

        handler.validate(AuditActionType.UPDATE, 10L, after);

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    // --- apply --------------------------------------------------------------

    @Test
    void apply_create_insertsGroupAndMembers_returnsGeneratedId() {
        Map<String, Object> after = sampleAfter();
        doAnswer(invocation -> {
            SpreadGroup toInsert = invocation.getArgument(0);
            toInsert.setId(42L);
            return 1;
        }).when(spreadGroupMapper).insert(any(SpreadGroup.class));
        when(spreadGroupMapper.findById(42L)).thenReturn(sampleGroup(42L, 1L, "AU", "Group A"));

        Long id = handler.apply(AuditActionType.CREATE, null, after);

        assertThat(id).isEqualTo(42L);
        verify(spreadGroupMemberMapper, times(2)).insert(any(SpreadGroupMember.class));
    }

    @Test
    void apply_update_updatesGroupAndReplacesMembership_returnsEntityId() {
        Map<String, Object> after = sampleAfter();
        SpreadGroup existing = sampleGroup(10L, 1L, "AU", "Group A");
        when(spreadGroupMapper.findById(10L)).thenReturn(existing);
        when(spreadGroupMemberMapper.findByGroupId(10L)).thenReturn(List.of(sampleMember(1L, 10L, 5L, "TWD", "USD")));

        Long id = handler.apply(AuditActionType.UPDATE, 10L, after);

        assertThat(id).isEqualTo(10L);
        verify(spreadGroupMapper).update(existing);
        verify(spreadGroupMemberMapper).deleteByCurrencyPairId(5L); // removed
        verify(spreadGroupMemberMapper, times(2)).insert(any(SpreadGroupMember.class)); // 3 and 4 added
    }

    @Test
    void apply_delete_deletesMembersAndGroup_returnsEntityId() {
        when(spreadGroupMapper.findById(9L)).thenReturn(sampleGroup(9L, 1L, "AU", "Group A"));

        Long id = handler.apply(AuditActionType.DELETE, 9L, null);

        assertThat(id).isEqualTo(9L);
        verify(spreadGroupMemberMapper).deleteByGroupId(9L);
        verify(spreadGroupMapper).deleteById(9L);
    }

    // --- summarize -----------------------------------------------------------

    @Test
    void summarize_returnsBrandCodeAndName() {
        Map<String, Object> snapshot = Map.of("brandCode", "AU", "name", "Group A");

        assertThat(handler.summarize(snapshot)).isEqualTo("AU · Group A");
    }
}
