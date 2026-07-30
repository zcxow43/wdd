package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.audit.AuditActionType;
import pl.piomin.services.backend.exception.InvalidSpreadException;
import pl.piomin.services.backend.exception.SpreadDefaultNotFoundException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMapper;
import pl.piomin.services.backend.model.SpreadDefault;

/**
 * Unit tests for {@link SpreadDefaultAuditHandler}: snapshotOf/validate/apply/summarize.
 */
@ExtendWith(MockitoExtension.class)
class SpreadDefaultAuditHandlerTest {

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private SpreadGroupMapper spreadGroupMapper;

    private SpreadDefaultAuditHandler handler;

    @BeforeEach
    void setUp() {
        SpreadDefaultService spreadDefaultService = new SpreadDefaultService(spreadDefaultMapper);
        SpreadGroupValidator validator = new SpreadGroupValidator(brandMapper, currencyPairMapper, spreadGroupMapper);
        handler = new SpreadDefaultAuditHandler(spreadDefaultService, validator);
    }

    private SpreadDefault sample(Long id, Long brandId, String brandCode) {
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setId(id);
        spreadDefault.setBrandId(brandId);
        spreadDefault.setBrandCode(brandCode);
        spreadDefault.setDepositSpread(new BigDecimal("0.1"));
        spreadDefault.setWithdrawSpread(new BigDecimal("0.2"));
        return spreadDefault;
    }

    private Map<String, Object> sampleAfter() {
        return new HashMap<>(Map.of("depositSpread", new BigDecimal("0.1"), "withdrawSpread", new BigDecimal("0.2")));
    }

    @Test
    void entityType_isSpreadDefault() {
        assertThat(handler.entityType()).isEqualTo("SPREAD_DEFAULT");
    }

    @Test
    void snapshotOf_returnsSnapshot_whenFound() {
        when(spreadDefaultMapper.findById(1L)).thenReturn(sample(1L, 3L, "AU"));

        Map<String, Object> snapshot = handler.snapshotOf(1L);

        assertThat(snapshot.get("brandId")).isEqualTo(3L);
        assertThat(snapshot.get("brandCode")).isEqualTo("AU");
        assertThat(snapshot.get("depositSpread")).isEqualTo(new BigDecimal("0.1"));
        assertThat(snapshot.get("withdrawSpread")).isEqualTo(new BigDecimal("0.2"));
    }

    @Test
    void snapshotOf_throwsNotFound_whenMissing() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> handler.snapshotOf(999L))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    @Test
    void validate_succeeds_whenSpreadsNonNegative() {
        handler.validate(AuditActionType.UPDATE, 1L, sampleAfter());
        // no exception
    }

    @Test
    void validate_throws400_whenDepositSpreadNegative() {
        Map<String, Object> after = sampleAfter();
        after.put("depositSpread", new BigDecimal("-1"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_throws400_whenWithdrawSpreadNegative() {
        Map<String, Object> after = sampleAfter();
        after.put("withdrawSpread", new BigDecimal("-1"));

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void validate_throws400_whenDepositSpreadMissing() {
        Map<String, Object> after = sampleAfter();
        after.put("depositSpread", null);

        assertThatThrownBy(() -> handler.validate(AuditActionType.UPDATE, 1L, after))
                .isInstanceOf(InvalidSpreadException.class);
    }

    @Test
    void apply_updatesSpreadDefault_andReturnsEntityId() {
        SpreadDefault existing = sample(1L, 3L, "AU");
        when(spreadDefaultMapper.findById(1L)).thenReturn(existing);

        Long id = handler.apply(AuditActionType.UPDATE, 1L, sampleAfter());

        assertThat(id).isEqualTo(1L);
        verify(spreadDefaultMapper).update(any(SpreadDefault.class));
    }

    @Test
    void apply_throwsNotFound_whenEntityNoLongerExists() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> handler.apply(AuditActionType.UPDATE, 999L, sampleAfter()))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    @Test
    void summarize_returnsBrandCodeAndLabel() {
        Map<String, Object> snapshot = Map.of("brandCode", "AU");

        assertThat(handler.summarize(snapshot)).isEqualTo("AU · 預設點差");
    }
}
