package com.humano.service.billing.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StripePaymentProvider#toMinorUnits(BigDecimal, String)} — the per-currency
 * major-to-minor-unit conversion (#3 per-currency rounding).
 */
class StripePaymentProviderTest {

    @Test
    void twoDecimalCurrenciesMatchTheLegacyTimes100Behaviour() {
        // Regression guard: for 2-dp currencies the result must equal the old setScale(2).multiply(100).
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("12.34"), "USD")).isEqualTo(1234L);
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("12.34"), "EUR")).isEqualTo(1234L);
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("0.05"), "USD")).isEqualTo(5L);
    }

    @Test
    void zeroDecimalCurrenciesScaleByOne() {
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("1000"), "JPY")).isEqualTo(1000L);
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("1000.40"), "KRW")).isEqualTo(1000L);
    }

    @Test
    void threeDecimalCurrenciesScaleByThousand() {
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("1.234"), "BHD")).isEqualTo(1234L);
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("2.5"), "KWD")).isEqualTo(2500L);
    }

    @Test
    void roundsHalfUpToTheCurrencyScale() {
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("12.345"), "USD")).isEqualTo(1235L);
        assertThat(StripePaymentProvider.toMinorUnits(new BigDecimal("1000.5"), "JPY")).isEqualTo(1001L);
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> StripePaymentProvider.toMinorUnits(new BigDecimal("10.00"), "ZZZ")).isInstanceOf(
            PaymentProviderException.class
        );
    }
}
