package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.EffectiveSpread;
import com.wdd.backend.dto.EffectiveSpreadResponse;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;

class EffectiveSpreadServiceTest {

    private CurrencyPairMapper currencyPairMapper;
    private BrandMapper brandMapper;
    private EffectiveSpreadService service;

    @BeforeEach
    void setUp() {
        currencyPairMapper = mock(CurrencyPairMapper.class);
        brandMapper = mock(BrandMapper.class);
        service = new EffectiveSpreadService(currencyPairMapper, brandMapper);
    }

    private static EffectiveSpread sample(Long id, Long groupId, String groupName, String source,
            BigDecimal deposit, BigDecimal withdrawal) {
        EffectiveSpread spread = new EffectiveSpread();
        spread.setCurrencyPairId(id);
        spread.setCurrencyPairDefinitionId(1L);
        spread.setBaseCurrencyCode("USD");
        spread.setQuoteCurrencyCode("JPY");
        spread.setBrandId(1L);
        spread.setBrandCode("au");
        spread.setSpreadGroupId(groupId);
        spread.setSpreadGroupName(groupName);
        spread.setSource(source);
        spread.setDepositSpread(deposit);
        spread.setWithdrawalSpread(withdrawal);
        return spread;
    }

    @Test
    void findByBrandIdThrowsWhenBrandIdMissing() {
        assertThatThrownBy(() -> service.findByBrandId(null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void findByBrandIdThrowsWhenBrandUnknown() {
        when(brandMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.findByBrandId(999L))
                .isInstanceOf(BrandNotFoundException.class);
    }

    @Test
    void findByBrandIdReturnsResolvedSpreadsWithSource() {
        when(brandMapper.findById(1L)).thenReturn(new Brand());
        when(currencyPairMapper.findEffectiveSpreadsByBrandId(1L)).thenReturn(List.of(
                sample(10L, 3L, "VIP", "GROUP", new BigDecimal("0.0002"), new BigDecimal("0.0003")),
                sample(11L, null, null, "DEFAULT", new BigDecimal("0.0005"), new BigDecimal("0.0008"))));

        List<EffectiveSpreadResponse> result = service.findByBrandId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSource()).isEqualTo("GROUP");
        assertThat(result.get(0).getSpreadGroupId()).isEqualTo(3L);
        assertThat(result.get(0).getDepositSpread()).isEqualByComparingTo("0.0002");
        assertThat(result.get(1).getSource()).isEqualTo("DEFAULT");
        assertThat(result.get(1).getSpreadGroupId()).isNull();
        assertThat(result.get(1).getDepositSpread()).isEqualByComparingTo("0.0005");
    }
}
