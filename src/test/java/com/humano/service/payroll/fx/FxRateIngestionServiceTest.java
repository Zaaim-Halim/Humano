package com.humano.service.payroll.fx;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.service.payroll.fx.FxRateIngestionService.CurrencyPair;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FxRateIngestionService#neededPairs} — the usage-derived pair selection that
 * keeps ingestion to the few pairs a tenant actually uses rather than the full currency catalog (#8).
 */
class FxRateIngestionServiceTest {

    @Test
    void crossesEverySourceWithEveryTargetExcludingIdentityPairs() {
        Set<CurrencyPair> pairs = FxRateIngestionService.neededPairs(
            Set.of(CurrencyCode.EUR, CurrencyCode.GBP),
            Set.of(CurrencyCode.USD, CurrencyCode.EUR)
        );

        // EUR->USD, GBP->USD, GBP->EUR. EUR->EUR is dropped (identity); EUR appears as both source and target.
        assertThat(pairs).containsExactlyInAnyOrder(
            new CurrencyPair(CurrencyCode.EUR, CurrencyCode.USD),
            new CurrencyPair(CurrencyCode.GBP, CurrencyCode.USD),
            new CurrencyPair(CurrencyCode.GBP, CurrencyCode.EUR)
        );
    }

    @Test
    void singleCurrencyTenantNeedsNoPairs() {
        assertThat(FxRateIngestionService.neededPairs(Set.of(CurrencyCode.USD), Set.of(CurrencyCode.USD))).isEmpty();
    }

    @Test
    void emptyInputsYieldNoPairs() {
        assertThat(FxRateIngestionService.neededPairs(Set.of(), Set.of(CurrencyCode.USD))).isEmpty();
        assertThat(FxRateIngestionService.neededPairs(Set.of(CurrencyCode.USD), Set.of())).isEmpty();
    }

    /** Records each call's base so the test can assert one-call-per-base and base=from. */
    private static final class RecordingProvider implements FxRateProvider {

        private final Map<CurrencyCode, Map<CurrencyCode, BigDecimal>> ratesByBase;
        final List<CurrencyCode> basesCalled = new ArrayList<>();

        RecordingProvider(Map<CurrencyCode, Map<CurrencyCode, BigDecimal>> ratesByBase) {
            this.ratesByBase = ratesByBase;
        }

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public Map<CurrencyCode, BigDecimal> fetchLatestRates(CurrencyCode base, Set<CurrencyCode> targets) {
            basesCalled.add(base);
            return ratesByBase.getOrDefault(base, Map.of());
        }
    }

    @Test
    void fetchesOncePerBaseKeepingDirectionBaseToTarget() {
        RecordingProvider provider = new RecordingProvider(
            Map.of(
                CurrencyCode.EUR,
                Map.of(CurrencyCode.USD, new BigDecimal("1.10"), CurrencyCode.GBP, new BigDecimal("0.86")),
                CurrencyCode.JPY,
                Map.of(CurrencyCode.USD, new BigDecimal("0.0064"))
            )
        );
        // Two pairs share base EUR -> must collapse to a single EUR call.
        Set<CurrencyPair> pairs = Set.of(
            new CurrencyPair(CurrencyCode.EUR, CurrencyCode.USD),
            new CurrencyPair(CurrencyCode.EUR, CurrencyCode.GBP),
            new CurrencyPair(CurrencyCode.JPY, CurrencyCode.USD)
        );

        Map<CurrencyPair, BigDecimal> rates = FxRateIngestionService.fetchRates(provider, pairs);

        // One call per distinct base (rate-limit safety), and the base is the pair's `from`.
        assertThat(provider.basesCalled).containsExactlyInAnyOrder(CurrencyCode.EUR, CurrencyCode.JPY);
        // Stored key is (from=base, to=target) with the provider's base->target value (direction preserved).
        assertThat(rates.get(new CurrencyPair(CurrencyCode.EUR, CurrencyCode.USD))).isEqualByComparingTo("1.10");
        assertThat(rates.get(new CurrencyPair(CurrencyCode.EUR, CurrencyCode.GBP))).isEqualByComparingTo("0.86");
        assertThat(rates.get(new CurrencyPair(CurrencyCode.JPY, CurrencyCode.USD))).isEqualByComparingTo("0.0064");
    }
}
