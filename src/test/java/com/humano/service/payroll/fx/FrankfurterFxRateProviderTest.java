package com.humano.service.payroll.fx;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FrankfurterFxRateProvider#parseRates} — the provider response → {@link CurrencyCode}
 * mapping (#8). Exercises parsing with a stubbed payload; the live HTTP call is opt-in and unverified here.
 */
class FrankfurterFxRateProviderTest {

    @Test
    void mapsKnownCurrencyCodesToTheirRates() {
        Map<String, BigDecimal> payload = new HashMap<>();
        payload.put("EUR", new BigDecimal("0.92"));
        payload.put("GBP", new BigDecimal("0.79"));

        Map<CurrencyCode, BigDecimal> parsed = FrankfurterFxRateProvider.parseRates(payload);

        assertThat(parsed).containsOnlyKeys(CurrencyCode.EUR, CurrencyCode.GBP);
        assertThat(parsed.get(CurrencyCode.EUR)).isEqualByComparingTo("0.92");
    }

    @Test
    void dropsUnknownCodesAndNonPositiveRates() {
        Map<String, BigDecimal> payload = new HashMap<>();
        payload.put("EUR", new BigDecimal("0.92"));
        payload.put("XXX", new BigDecimal("1.5")); // not a modelled CurrencyCode
        payload.put("GBP", BigDecimal.ZERO); // non-positive
        payload.put("JPY", new BigDecimal("-1")); // negative

        Map<CurrencyCode, BigDecimal> parsed = FrankfurterFxRateProvider.parseRates(payload);

        assertThat(parsed).containsOnlyKeys(CurrencyCode.EUR);
    }

    @Test
    void handlesNullAndEmptyPayload() {
        assertThat(FrankfurterFxRateProvider.parseRates(null)).isEmpty();
        assertThat(FrankfurterFxRateProvider.parseRates(Map.of())).isEmpty();
    }
}
