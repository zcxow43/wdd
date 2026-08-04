package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.audit.AuditActionType;
import com.wdd.backend.dto.CurrencyPairUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.CurrencyNotFoundException;
import com.wdd.backend.exception.CurrencyPairExistsException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.InvalidCurrencyPairException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.Currency;
import com.wdd.backend.model.CurrencyPair;

/**
 * Unit tests for {@link CurrencyPairAuditHandler} — the CURRENCY_PAIR plug-in for the generic
 * audit workflow (specs/backend/audit.md). Handles UPDATE/DELETE only; there is no CREATE case
 * (specs/backend/currency-pair-approval.md's "Delta: no CREATE").
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
    private CurrencyPairService currencyPairService;

    private CurrencyPairAuditHandler handler;

    @BeforeEach
    void setUp() {
        CurrencyPairValidator validator = new CurrencyPairValidator(brandMapper, currencyMapper, currencyPairMapper);
        handler = new CurrencyPairAuditHandler(currencyPairMapper, currencyPairService, validator);
    }

    private CurrencyPair samplePair() {
        CurrencyPair pair = new CurrencyPair();
        pair.setId(1L);
        pair.setBrandId(3L);
        pair.setBaseCurrencyId(2L);
        pair.setQuoteCurrencyId(1L);
        pair.setRate(new BigDecimal("32.5"));
        pair.setRateType("MANUAL");
        pair.setActive(true);
        pair.setCreatedAt(LocalDateTime.now());
        pair.setUpdatedAt(LocalDateTime.now());
        pair.setBrandCode("PUG");
        pair.setBaseCurrencyCode("USD");
        pair.setQuoteCurrencyCode("TWD");
        return pair;
    }

    private Brand sampleBrand() {
        Brand brand = new Brand();
        brand.setId(3L);
        brand.setCode("PUG");
        brand.setName("PUG");
        brand.setActive(true);
        return brand;
    }

    private Currency sampleCurrency(Long id, String code) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        return currency;
    }

    private Map<String, Object> mergedAfter() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("brandId", 3L);
        after.put("baseCurrencyId", 2L);
        after.put("quoteCurrencyId", 1L);
        after.put("rateType", "MANUAL");
        after.put("rate", new BigDecimal("32.5"));
        after.put("active", true);
        return after;
    }

    private void stubBrandAndCurrenciesExist() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand()));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(1L)).thenReturn(Optional.of(sampleCurrency(1L, "TWD")));
    }

    // ---------- entityType ----------

    @Test
    void entityType_returnsCurrencyPair() {
        assertThat(handler.entityType()).isEqualTo("CURRENCY_PAIR");
    }

    // ---------- snapshotOf ----------

    @Test
    void snapshotOf_returnsShapeMatchingSpec_whenFound() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));

        Map<String, Object> snapshot = handler.snapshotOf(1L);

        assertThat(snapshot).containsEntry("brandId", 3L)
                .containsEntry("brandCode", "PUG")
                .containsEntry("baseCurrencyId", 2L)
                .containsEntry("baseCurrencyCode", "USD")
                .containsEntry("quoteCurrencyId", 1L)
                .containsEntry("quoteCurrencyCode", "TWD")
                .containsEntry("rateType", "MANUAL")
                .containsEntry("active", true);
        assertThat((BigDecimal) snapshot.get("rate")).isEqualByComparingTo("32.5");
    }

    @Test
    void snapshotOf_throwsNotFound_whenMissing() {
        when(currencyPairMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.snapshotOf(999L))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    // ---------- validate ----------

    @Test
    void validate_success_enrichesSnapshotWithCodes() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, 1L)).thenReturn(Optional.empty());

        Map<String, Object> after = mergedAfter();
        handler.validate(AuditActionType.UPDATE, 1L, after);

        assertThat(after).containsEntry("brandCode", "PUG")
                .containsEntry("baseCurrencyCode", "USD")
                .containsEntry("quoteCurrencyCode", "TWD");
        assertThat((BigDecimal) after.get("rate")).isEqualByComparingTo("32.5");
    }

    @Test
    void validate_throwsBrandNotFound_whenBrandMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, mergedAfter()))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void validate_throwsCurrencyNotFound_whenBaseCurrencyMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand()));
        when(currencyMapper.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, mergedAfter()))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void validate_throwsCurrencyNotFound_whenQuoteCurrencyMissing() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand()));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));
        when(currencyMapper.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, mergedAfter()))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void validate_throwsBadRequest_whenBaseEqualsQuote() {
        when(brandMapper.findById(3L)).thenReturn(Optional.of(sampleBrand()));
        when(currencyMapper.findById(2L)).thenReturn(Optional.of(sampleCurrency(2L, "USD")));

        Map<String, Object> after = mergedAfter();
        after.put("quoteCurrencyId", 2L);

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidCurrencyPairException.class);
    }

    @Test
    void validate_throwsConflict_whenCollidesWithAnotherLiveRow() {
        stubBrandAndCurrenciesExist();
        CurrencyPair other = samplePair();
        other.setId(2L);
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, 1L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, mergedAfter()))
                .isInstanceOf(CurrencyPairExistsException.class);
    }

    @Test
    void validate_autoRateType_forcesRateToNull_evenIfSupplied() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, 1L)).thenReturn(Optional.empty());

        Map<String, Object> after = mergedAfter();
        after.put("rateType", "AUTO");
        after.put("rate", new BigDecimal("999"));

        handler.validate(AuditActionType.UPDATE, 1L, after);

        assertThat(after.get("rate")).isNull();
    }

    @Test
    void validate_manualWithoutRate_throwsBadRequest() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, 1L)).thenReturn(Optional.empty());

        Map<String, Object> after = mergedAfter();
        after.put("rate", null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidCurrencyPairException.class);
    }

    @Test
    void validate_neverInvokesCreateStyleDedup_forUpdate() {
        stubBrandAndCurrenciesExist();
        when(currencyPairMapper.findByBrandBaseQuote(3L, 2L, 1L, 1L)).thenReturn(Optional.empty());

        handler.validate(AuditActionType.UPDATE, 1L, mergedAfter());

        verify(currencyPairMapper).findByBrandBaseQuote(eq(3L), eq(2L), eq(1L), eq(1L));
        verify(currencyPairMapper, never()).findByBrandBaseQuote(any(), any(), any(), eq((Long) null));
    }

    // ---------- apply ----------

    @Test
    void apply_create_throwsUnsupportedOperation() {
        assertThatThrownBy(() -> handler.apply(AuditActionType.CREATE, null, mergedAfter()))
                .isInstanceOf(UnsupportedOperationException.class);

        verify(currencyPairService, never()).create(any());
    }

    @Test
    void apply_update_callsServiceUpdate_andReturnsEntityId() {
        Map<String, Object> after = mergedAfter();
        after.put("brandCode", "PUG");
        after.put("baseCurrencyCode", "USD");
        after.put("quoteCurrencyCode", "TWD");
        when(currencyPairService.update(eq(1L), any(CurrencyPairUpdateRequest.class)))
                .thenReturn(null);

        Long resultId = handler.apply(AuditActionType.UPDATE, 1L, after);

        assertThat(resultId).isEqualTo(1L);
        verify(currencyPairService).update(eq(1L), any(CurrencyPairUpdateRequest.class));
    }

    @Test
    void apply_update_throwsNotFound_whenTargetRowNoLongerExists() {
        when(currencyPairService.update(eq(1L), any(CurrencyPairUpdateRequest.class)))
                .thenThrow(new CurrencyPairNotFoundException(1L));

        assertThatThrownBy(() -> handler.apply(AuditActionType.UPDATE, 1L, mergedAfter()))
                .isInstanceOf(CurrencyPairNotFoundException.class);
    }

    @Test
    void apply_delete_callsServiceDelete_andReturnsEntityId() {
        Long resultId = handler.apply(AuditActionType.DELETE, 1L, null);

        assertThat(resultId).isEqualTo(1L);
        verify(currencyPairService).delete(1L);
    }

    // ---------- summarize ----------

    @Test
    void summarize_formatsBrandAndPair() {
        when(currencyPairMapper.findById(1L)).thenReturn(Optional.of(samplePair()));
        Map<String, Object> snapshot = handler.snapshotOf(1L);

        String summary = handler.summarize(snapshot);

        assertThat(summary).isEqualTo("PUG · USD/TWD");
    }

    @Test
    void summarize_returnsEntityType_whenSnapshotNull() {
        assertThat(handler.summarize(null)).isEqualTo("CURRENCY_PAIR");
    }
}
