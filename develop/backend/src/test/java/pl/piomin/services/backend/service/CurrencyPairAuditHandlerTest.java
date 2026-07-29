package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairUpdateRequest;
import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairExistsException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.DuplicatePendingCurrencyPairCreateException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Unit tests for {@link CurrencyPairAuditHandler}: snapshotOf/validate/apply/summarize,
 * exercised with a real {@link CurrencyPairValidator} and {@link CurrencyPairService}
 * backed by mocked mappers, matching the layering convention used by
 * {@link CurrencyPairServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CurrencyPairAuditHandlerTest {

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CurrencyMapper currencyMapper;

    @Mock
    private AuditRequestMapper auditRequestMapper;

    private CurrencyPairAuditHandler handler;

    @BeforeEach
    void setUp() {
        CurrencyPairValidator validator = new CurrencyPairValidator(brandMapper, currencyMapper, currencyPairMapper);
        CurrencyPairService currencyPairService = new CurrencyPairService(currencyPairMapper, validator);
        handler = new CurrencyPairAuditHandler(currencyPairMapper, validator, currencyPairService,
                auditRequestMapper, new ObjectMapper());
    }

    private Brand sampleBrand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        return brand;
    }

    private Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        currency.setActive(true);
        return currency;
    }

    private CurrencyPair samplePair(Long id, Long brandId, Long baseId, Long quoteId) {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(id);
        pair.setBrandId(brandId);
        pair.setBrandCode("PUG");
        pair.setBaseCurrencyId(baseId);
        pair.setBaseCurrencyCode("USD");
        pair.setQuoteCurrencyId(quoteId);
        pair.setQuoteCurrencyCode("TWD");
        pair.setRate(new BigDecimal("32.5"));
        pair.setRateType("MANUAL");
        pair.setActive(true);
        return pair;
    }

    private Map<String, Object> sampleAfter() {
        return new java.util.HashMap<>(Map.of(
                "brandId", 3L,
                "baseCurrencyId", 2L,
                "quoteCurrencyId", 1L,
                "rateType", "MANUAL",
                "rate", new BigDecimal("32.5")));
    }

    // --- entityType -------------------------------------------------------

    @Test
    void entityType_isCurrencyPair() {
        assertThat(handler.entityType()).isEqualTo("CURRENCY_PAIR");
    }

    // --- snapshotOf ---------------------------------------------------------

    @Test
    void snapshotOf_returnsSnapshot_whenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(samplePair(1L, 3L, 2L, 1L));

        Map<String, Object> snapshot = handler.snapshotOf(1L);

        assertThat(snapshot.get("brandId")).isEqualTo(3L);
        assertThat(snapshot.get("brandCode")).isEqualTo("PUG");
        assertThat(snapshot.get("baseCurrencyCode")).isEqualTo("USD");
        assertThat(snapshot.get("quoteCurrencyCode")).isEqualTo("TWD");
        assertThat(snapshot.get("rate")).isEqualTo(new BigDecimal("32.5"));
        assertThat(snapshot.get("rateType")).isEqualTo("MANUAL");
        assertThat(snapshot.get("active")).isEqualTo(true);
    }

    @Test
    void snapshotOf_throwsNotFound_whenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> handler.snapshotOf(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    // --- validate -------------------------------------------------------------

    @Test
    void validate_succeeds_andEnrichesSnapshotWithCodes() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        handler.validate(AuditActionType.CREATE, null, after);

        assertThat(after.get("brandCode")).isEqualTo("PUG");
        assertThat(after.get("baseCurrencyCode")).isEqualTo("USD");
        assertThat(after.get("quoteCurrencyCode")).isEqualTo("TWD");
        assertThat(after.get("rate")).isEqualTo(new BigDecimal("32.5"));
    }

    @Test
    void validate_throwsNotFound_whenBrandMissing() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void validate_throwsNotFound_whenBaseCurrencyMissing() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void validate_throwsNotFound_whenQuoteCurrencyMissing() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void validate_throws400_whenBaseEqualsQuote() {
        Map<String, Object> after = sampleAfter();
        after.put("baseCurrencyId", 1L);
        after.put("quoteCurrencyId", 1L);
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidCurrencyPairException.class);
    }

    @Test
    void validate_throwsConflict_whenLivePairAlreadyExists() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(samplePair(5L, 3L, 2L, 1L));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(CurrencyPairExistsException.class);
    }

    @Test
    void validate_forcesRateToNull_whenRateTypeAuto() {
        Map<String, Object> after = sampleAfter();
        after.put("rateType", "AUTO");
        after.put("rate", new BigDecimal("999"));
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        handler.validate(AuditActionType.CREATE, null, after);

        assertThat(after.get("rate")).isNull();
    }

    @Test
    void validate_throws400_whenRateTypeManualAndRateMissing() {
        Map<String, Object> after = sampleAfter();
        after.put("rate", null);
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(InvalidCurrencyPairException.class);
    }

    @Test
    void validate_create_throwsDuplicate_whenPendingCreateExistsForSameTriple() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        AuditRequest pendingCreate = new AuditRequest();
        pendingCreate.setEntityType("CURRENCY_PAIR");
        pendingCreate.setActionType("CREATE");
        pendingCreate.setStatus("PENDING");
        pendingCreate.setAfterSnapshot("{\"brandId\":3,\"baseCurrencyId\":2,\"quoteCurrencyId\":1}");
        when(auditRequestMapper.findAll("CURRENCY_PAIR", "PENDING", "CREATE")).thenReturn(List.of(pendingCreate));

        assertThatThrownBy(() -> handler.validate(AuditActionType.CREATE, null, after))
                .isInstanceOf(DuplicatePendingCurrencyPairCreateException.class);
    }

    @Test
    void validate_create_succeeds_whenPendingCreateExistsForDifferentTriple() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        AuditRequest pendingCreate = new AuditRequest();
        pendingCreate.setEntityType("CURRENCY_PAIR");
        pendingCreate.setActionType("CREATE");
        pendingCreate.setStatus("PENDING");
        pendingCreate.setAfterSnapshot("{\"brandId\":9,\"baseCurrencyId\":2,\"quoteCurrencyId\":1}");
        when(auditRequestMapper.findAll("CURRENCY_PAIR", "PENDING", "CREATE")).thenReturn(List.of(pendingCreate));

        handler.validate(AuditActionType.CREATE, null, after);
        // no exception
    }

    @Test
    void validate_create_skipsPendingDuplicateCheck_whenSnapshotAlreadyEnriched_asAtApprovalTime() {
        // Simulates AuditService.approve() re-validating a CREATE request: the
        // snapshot passed in is the already-persisted (and therefore already
        // code-enriched) one, so it already matches itself among PENDING creates.
        Map<String, Object> after = sampleAfter();
        after.put("brandCode", "PUG");
        after.put("baseCurrencyCode", "USD");
        after.put("quoteCurrencyCode", "TWD");
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);

        handler.validate(AuditActionType.CREATE, null, after);
        // no exception thrown - self-collision is correctly avoided

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    @Test
    void validate_update_doesNotCheckPendingCreateDuplicate() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(samplePair(7L, 3L, 2L, 1L));

        handler.validate(AuditActionType.UPDATE, 7L, after);

        verify(auditRequestMapper, never()).findAll(any(), any(), any());
    }

    // --- apply -------------------------------------------------------------

    @Test
    void apply_create_insertsPairAndReturnsGeneratedId() {
        Map<String, Object> after = sampleAfter();
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            CurrencyPair toInsert = invocation.getArgument(0);
            toInsert.setId(42L);
            return 1;
        }).when(currencyPairMapper).insert(any(CurrencyPair.class));
        when(currencyPairMapper.findById(42L)).thenReturn(samplePair(42L, 3L, 2L, 1L));

        Long id = handler.apply(AuditActionType.CREATE, null, after);

        assertThat(id).isEqualTo(42L);
        verify(currencyPairMapper).insert(any(CurrencyPair.class));
    }

    @Test
    void apply_update_updatesPairAndReturnsEntityId() {
        Map<String, Object> after = sampleAfter();
        CurrencyPair existing = samplePair(7L, 3L, 2L, 1L);
        when(currencyPairMapper.findById(7L)).thenReturn(existing);
        when(brandMapper.findById(3L)).thenReturn(sampleBrand(3L, "PUG"));
        when(currencyMapper.findById(2L)).thenReturn(sampleCurrency(2L, "USD"));
        when(currencyMapper.findById(1L)).thenReturn(sampleCurrency(1L, "TWD"));
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L)).thenReturn(existing);

        Long id = handler.apply(AuditActionType.UPDATE, 7L, after);

        assertThat(id).isEqualTo(7L);
        verify(currencyPairMapper).update(any(CurrencyPair.class));
    }

    @Test
    void apply_delete_deletesPairAndReturnsEntityId() {
        when(currencyPairMapper.findById(9L)).thenReturn(samplePair(9L, 3L, 2L, 1L));

        Long id = handler.apply(AuditActionType.DELETE, 9L, null);

        assertThat(id).isEqualTo(9L);
        verify(currencyPairMapper).deleteById(9L);
    }

    @Test
    void apply_update_throwsNotFound_whenEntityNoLongerExists() {
        when(currencyPairMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> handler.apply(AuditActionType.UPDATE, 999L, sampleAfter()))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    // --- summarize -----------------------------------------------------------

    @Test
    void summarize_returnsBrandCodeAndPair() {
        Map<String, Object> snapshot = Map.of(
                "brandCode", "PUG",
                "baseCurrencyCode", "USD",
                "quoteCurrencyCode", "TWD");

        assertThat(handler.summarize(snapshot)).isEqualTo("PUG · USD/TWD");
    }
}
