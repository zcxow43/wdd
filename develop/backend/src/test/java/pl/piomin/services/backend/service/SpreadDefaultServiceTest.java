package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.exception.SpreadDefaultNotFoundException;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.model.SpreadDefault;

@ExtendWith(MockitoExtension.class)
class SpreadDefaultServiceTest {

    @Mock
    private SpreadDefaultMapper spreadDefaultMapper;

    private SpreadDefaultService spreadDefaultService;

    @BeforeEach
    void setUp() {
        spreadDefaultService = new SpreadDefaultService(spreadDefaultMapper);
    }

    private SpreadDefault sample(Long id, Long brandId, String brandCode, String deposit, String withdraw) {
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setId(id);
        spreadDefault.setBrandId(brandId);
        spreadDefault.setBrandCode(brandCode);
        spreadDefault.setDepositSpread(new BigDecimal(deposit));
        spreadDefault.setWithdrawSpread(new BigDecimal(withdraw));
        return spreadDefault;
    }

    @Test
    void list_returnsAllRowsFromMapper() {
        when(spreadDefaultMapper.findAll(null)).thenReturn(List.of(sample(1L, 1L, "AU", "0", "0")));

        List<SpreadDefault> result = spreadDefaultService.list(null);

        assertThat(result).hasSize(1);
    }

    @Test
    void list_filtersByBrandId() {
        when(spreadDefaultMapper.findAll(1L)).thenReturn(List.of(sample(1L, 1L, "AU", "0", "0")));

        List<SpreadDefault> result = spreadDefaultService.list(1L);

        assertThat(result).hasSize(1);
        verify(spreadDefaultMapper).findAll(1L);
    }

    @Test
    void getById_returnsRow_whenFound() {
        when(spreadDefaultMapper.findById(1L)).thenReturn(sample(1L, 1L, "AU", "0", "0"));

        SpreadDefault result = spreadDefaultService.getById(1L);

        assertThat(result.getBrandCode()).isEqualTo("AU");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadDefaultService.getById(999L))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
    }

    @Test
    void update_appliesNewSpreads_whenFound() {
        SpreadDefault existing = sample(1L, 1L, "AU", "0", "0");
        SpreadDefault updated = sample(1L, 1L, "AU", "0.1", "0.2");
        when(spreadDefaultMapper.findById(1L)).thenReturn(existing).thenReturn(updated);

        SpreadDefault result = spreadDefaultService.update(1L, new BigDecimal("0.1"), new BigDecimal("0.2"));

        assertThat(result.getDepositSpread()).isEqualByComparingTo("0.1");
        assertThat(result.getWithdrawSpread()).isEqualByComparingTo("0.2");
        verify(spreadDefaultMapper).update(existing);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(spreadDefaultMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> spreadDefaultService.update(999L, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(SpreadDefaultNotFoundException.class);
        verify(spreadDefaultMapper, never()).update(org.mockito.ArgumentMatchers.any(SpreadDefault.class));
    }
}
