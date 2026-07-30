package pl.piomin.services.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.piomin.services.backend.dto.CurrencyPairCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionCreateRequest;
import pl.piomin.services.backend.dto.CurrencyPairDefinitionUpdateRequest;
import pl.piomin.services.backend.exception.CurrencyNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionExistsException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionInUseException;
import pl.piomin.services.backend.exception.CurrencyPairDefinitionNotFoundException;
import pl.piomin.services.backend.exception.InvalidCurrencyPairException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairDefinitionMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.CurrencyPairDefinition;

@ExtendWith(MockitoExtension.class)
class CurrencyPairDefinitionServiceTest {

    @Mock
    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;

    @Mock
    private CurrencyPairMapper currencyPairMapper;

    @Mock
    private CurrencyMapper currencyMapper;

    @Mock
    private BrandMapper brandMapper;

    @Mock
    private CurrencyPairService currencyPairService;

    private CurrencyPairDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyPairDefinitionService(currencyPairDefinitionMapper, currencyPairMapper,
                currencyMapper, brandMapper, currencyPairService);
    }

    private Currency currency(Long id) {
        Currency currency = new Currency();
        currency.setId(id);
        return currency;
    }

    private Brand brand(Long id, String code) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setCode(code);
        return brand;
    }

    private CurrencyPairDefinitionCreateRequest createRequest(Long base, Long quote, int fwd, int rev) {
        CurrencyPairDefinitionCreateRequest request = new CurrencyPairDefinitionCreateRequest();
        request.setBaseCurrencyId(base);
        request.setQuoteCurrencyId(quote);
        request.setForwardPrecision(fwd);
        request.setReversePrecision(rev);
        return request;
    }

    private CurrencyPairDefinition sample(Long id, Long base, Long quote) {
        CurrencyPairDefinition definition = new CurrencyPairDefinition();
        definition.setId(id);
        definition.setBaseCurrencyId(base);
        definition.setQuoteCurrencyId(quote);
        definition.setForwardPrecision(2);
        definition.setReversePrecision(5);
        return definition;
    }

    private CurrencyPair activePair(String brandCode) {
        CurrencyPair pair = new CurrencyPair();
        pair.setBrandCode(brandCode);
        pair.setActive(true);
        return pair;
    }

    @Test
    void list_returnsAllRowsFromMapper() {
        when(currencyPairDefinitionMapper.findAll(null, null)).thenReturn(List.of(sample(1L, 2L, 3L)));

        List<CurrencyPairDefinition> result = service.list(null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getById_returnsRow_whenFound() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));

        CurrencyPairDefinition result = service.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
    }

    @Test
    void create_insertsDefinition_andFansOutToAllBrands() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));
        when(currencyMapper.findById(3L)).thenReturn(currency(3L));
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(null);
        when(brandMapper.findAll(null)).thenReturn(List.of(brand(1L, "PUG"), brand(2L, "STAR")));
        when(currencyPairMapper.findByBrandBaseQuote(1L, 2L, 3L)).thenReturn(null);
        when(currencyPairMapper.findByBrandBaseQuote(2L, 2L, 3L)).thenReturn(null);
        when(currencyPairDefinitionMapper.findById(any())).thenReturn(sample(10L, 2L, 3L));

        CurrencyPairDefinition result = service.create(createRequest(2L, 3L, 2, 5));

        assertThat(result.getId()).isEqualTo(10L);
        verify(currencyPairDefinitionMapper).insert(any(CurrencyPairDefinition.class));
        verify(currencyPairService, times(2)).create(any(CurrencyPairCreateRequest.class));
    }

    @Test
    void create_skipsBrand_whenLivePairAlreadyExistsForThatBrand() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));
        when(currencyMapper.findById(3L)).thenReturn(currency(3L));
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(null);
        when(brandMapper.findAll(null)).thenReturn(List.of(brand(1L, "PUG"), brand(2L, "STAR")));
        when(currencyPairMapper.findByBrandBaseQuote(1L, 2L, 3L)).thenReturn(new CurrencyPair());
        when(currencyPairMapper.findByBrandBaseQuote(2L, 2L, 3L)).thenReturn(null);
        when(currencyPairDefinitionMapper.findById(any())).thenReturn(sample(10L, 2L, 3L));

        service.create(createRequest(2L, 3L, 2, 5));

        verify(currencyPairService, times(1)).create(any(CurrencyPairCreateRequest.class));
        verify(currencyPairService).create(argThatBrandId(2L));
    }

    private CurrencyPairCreateRequest argThatBrandId(Long brandId) {
        return org.mockito.ArgumentMatchers.argThat(req -> req.getBrandId().equals(brandId)
                && "AUTO".equals(req.getRateType()) && req.getRate() == null
                && Boolean.TRUE.equals(req.getActive()));
    }

    @Test
    void create_throwsNotFound_whenBaseCurrencyMissing() {
        when(currencyMapper.findById(2L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createRequest(2L, 3L, 2, 5)))
                .isInstanceOf(CurrencyNotFoundException.class);
        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsNotFound_whenQuoteCurrencyMissing() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));
        when(currencyMapper.findById(3L)).thenReturn(null);

        assertThatThrownBy(() -> service.create(createRequest(2L, 3L, 2, 5)))
                .isInstanceOf(CurrencyNotFoundException.class);
    }

    @Test
    void create_throwsInvalid_whenBaseEqualsQuote() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));

        assertThatThrownBy(() -> service.create(createRequest(2L, 2L, 2, 5)))
                .isInstanceOf(InvalidCurrencyPairException.class);
        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
    }

    @Test
    void create_throwsExists_whenReverseDirectionAlreadyDefined() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));
        when(currencyMapper.findById(3L)).thenReturn(currency(3L));
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(sample(1L, 3L, 2L));

        assertThatThrownBy(() -> service.create(createRequest(2L, 3L, 2, 5)))
                .isInstanceOf(CurrencyPairDefinitionExistsException.class);
        verify(currencyPairDefinitionMapper, never()).insert(any(CurrencyPairDefinition.class));
        verify(currencyPairService, never()).create(any(CurrencyPairCreateRequest.class));
    }

    @Test
    void create_throwsExists_whenExactDirectionAlreadyDefined() {
        when(currencyMapper.findById(2L)).thenReturn(currency(2L));
        when(currencyMapper.findById(3L)).thenReturn(currency(3L));
        when(currencyPairDefinitionMapper.findByEitherDirection(2L, 3L)).thenReturn(sample(1L, 2L, 3L));

        assertThatThrownBy(() -> service.create(createRequest(2L, 3L, 2, 5)))
                .isInstanceOf(CurrencyPairDefinitionExistsException.class);
    }

    @Test
    void update_updatesPrecision_whenFound() {
        CurrencyPairDefinition existing = sample(1L, 2L, 3L);
        CurrencyPairDefinition updated = sample(1L, 2L, 3L);
        updated.setForwardPrecision(3);
        updated.setReversePrecision(6);
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(existing).thenReturn(updated);

        CurrencyPairDefinitionUpdateRequest request = new CurrencyPairDefinitionUpdateRequest();
        request.setForwardPrecision(3);
        request.setReversePrecision(6);

        CurrencyPairDefinition result = service.update(1L, request);

        assertThat(result.getForwardPrecision()).isEqualTo(3);
        assertThat(result.getReversePrecision()).isEqualTo(6);
        verify(currencyPairDefinitionMapper).update(existing);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        CurrencyPairDefinitionUpdateRequest request = new CurrencyPairDefinitionUpdateRequest();
        request.setForwardPrecision(3);
        request.setReversePrecision(6);

        assertThatThrownBy(() -> service.update(999L, request))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
        verify(currencyPairDefinitionMapper, never()).update(any(CurrencyPairDefinition.class));
    }

    @Test
    void delete_removesRow_whenFound() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper).deleteById(1L);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(currencyPairDefinitionMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CurrencyPairDefinitionNotFoundException.class);
        verify(currencyPairDefinitionMapper, never()).deleteById(any());
    }

    @Test
    void delete_throwsInUse_whenOneBrandStillActive() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of(activePair("PUG")));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CurrencyPairDefinitionInUseException.class)
                .satisfies(ex -> assertThat(((CurrencyPairDefinitionInUseException) ex).getActiveBrandCodes())
                        .containsExactly("PUG"));
        verify(currencyPairDefinitionMapper, never()).deleteById(any());
    }

    @Test
    void delete_throwsInUse_withFullActiveBrandCodesList_whenMultipleBrandsStillActive() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L))
                .thenReturn(List.of(activePair("AU"), activePair("PUG")));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CurrencyPairDefinitionInUseException.class)
                .satisfies(ex -> assertThat(((CurrencyPairDefinitionInUseException) ex).getActiveBrandCodes())
                        .containsExactly("AU", "PUG"));
        verify(currencyPairDefinitionMapper, never()).deleteById(any());
    }

    @Test
    void delete_succeeds_whenAllBrandRowsInactive() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper).deleteById(1L);
    }

    @Test
    void delete_succeeds_whenZeroRowsExistForThatPair() {
        when(currencyPairDefinitionMapper.findById(1L)).thenReturn(sample(1L, 2L, 3L));
        when(currencyPairMapper.findActiveByBaseQuote(2L, 3L)).thenReturn(List.of());

        service.delete(1L);

        verify(currencyPairDefinitionMapper).deleteById(1L);
    }
}
