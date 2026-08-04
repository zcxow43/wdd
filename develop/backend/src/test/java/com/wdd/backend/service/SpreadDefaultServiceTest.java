package com.wdd.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wdd.backend.dto.SpreadDefaultResponse;
import com.wdd.backend.exception.SpreadDefaultNotFoundException;
import com.wdd.backend.mapper.SpreadDefaultMapper;
import com.wdd.backend.model.SpreadDefault;

@ExtendWith(MockitoExtension.class)
class SpreadDefaultServiceTest {

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    private SpreadDefaultService spreadDefaultService;

    @BeforeEach
    void setUp() {
        spreadDefaultService = new SpreadDefaultService(spreadDefaultMapper);
    }

    private SpreadDefault sampleSpreadDefault() {
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setId(1L);
        spreadDefault.setBrandId(3L);
        spreadDefault.setBrandCode("AU");
        spreadDefault.setDepositSpread(BigDecimal.ZERO);
        spreadDefault.setWithdrawSpread(BigDecimal.ZERO);
        spreadDefault.setCreatedAt(LocalDateTime.now());
        spreadDefault.setUpdatedAt(LocalDateTime.now());
        return spreadDefault;
    }

    @Test
    void list_returnsAllRowsWhenNoFilter() {
        when(spreadDefaultMapper.findAll(isNull())).thenReturn(List.of(sampleSpreadDefault()));

        List<SpreadDefaultResponse> result = spreadDefaultService.list(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBrandCode()).isEqualTo("AU");
    }

    @Test
    void list_filtersByBrandId() {
        when(spreadDefaultMapper.findAll(eq(3L))).thenReturn(List.of(sampleSpreadDefault()));

        List<SpreadDefaultResponse> result = spreadDefaultService.list(3L);

        assertThat(result).hasSize(1);
        verify(spreadDefaultMapper).findAll(eq(3L));
    }

    @Test
    void getById_returnsRowWhenFound() {
        when(spreadDefaultMapper.findById(1L)).thenReturn(Optional.of(sampleSpreadDefault()));

        SpreadDefaultResponse result = spreadDefaultService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getBrandCode()).isEqualTo("AU");
    }

    @Test
    void getById_throwsNotFoundWhenMissing() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadDefaultService.getById(999L))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    @Test
    void update_updatesSpreadsAndReturnsRow() {
        SpreadDefault existing = sampleSpreadDefault();
        SpreadDefault updated = sampleSpreadDefault();
        updated.setDepositSpread(new BigDecimal("0.1"));
        updated.setWithdrawSpread(new BigDecimal("0.2"));

        when(spreadDefaultMapper.findById(1L)).thenReturn(Optional.of(existing), Optional.of(updated));

        SpreadDefaultResponse result = spreadDefaultService.update(1L, new BigDecimal("0.1"), new BigDecimal("0.2"));

        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.1");
        assertThat(result.getWithdrawSpread()).isEqualByComparingTo("0.2");
        verify(spreadDefaultMapper).update(existing);
    }

    @Test
    void update_throwsNotFoundWhenMissing() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spreadDefaultService.update(999L, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }
}
