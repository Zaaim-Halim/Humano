package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.ExchangeRate;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link ExchangeRateService#upsertProviderRate} — the idempotent provider-rate upsert
 * behind FX ingestion (#8). Re-running a day's ingest must update the existing row in place, never
 * insert a duplicate.
 */
@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceUpsertTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @InjectMocks
    private ExchangeRateService service;

    private final Currency from = currency(CurrencyCode.EUR);
    private final Currency to = currency(CurrencyCode.USD);
    private final LocalDate date = LocalDate.of(2026, 6, 24);

    private static Currency currency(CurrencyCode code) {
        Currency c = new Currency();
        c.setCode(code);
        return c;
    }

    @Test
    void updatesExistingRowInPlaceRatherThanInserting() {
        ExchangeRate existing = new ExchangeRate();
        existing.setFromCcy(from);
        existing.setToCcy(to);
        existing.setDate(date);
        existing.setRate(new BigDecimal("1.000000"));
        when(exchangeRateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(existing)));

        service.upsertProviderRate(from, to, date, new BigDecimal("1.085"), "frankfurter");

        // Saves the SAME instance — no duplicate row — with refreshed rate + provenance.
        verify(exchangeRateRepository).save(existing);
        assertThat(existing.getRate()).isEqualByComparingTo("1.085000");
        assertThat(existing.getSource()).isEqualTo("frankfurter");
        assertThat(existing.getFetchedAt()).isNotNull();
        // Business effective date (read by the staleness guard) is untouched.
        assertThat(existing.getDate()).isEqualTo(date);
    }

    @Test
    void insertsANewRowWhenNoneExistsForThePairAndDate() {
        when(exchangeRateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        service.upsertProviderRate(from, to, date, new BigDecimal("1.085"), "frankfurter");

        ArgumentCaptor<ExchangeRate> captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        ExchangeRate saved = captor.getValue();
        assertThat(saved.getFromCcy()).isSameAs(from);
        assertThat(saved.getToCcy()).isSameAs(to);
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getRate()).isEqualByComparingTo("1.085000");
        assertThat(saved.getSource()).isEqualTo("frankfurter");
        assertThat(saved.getFetchedAt()).isNotNull();
    }
}
