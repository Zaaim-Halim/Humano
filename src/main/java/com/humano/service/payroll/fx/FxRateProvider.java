package com.humano.service.payroll.fx;

import com.humano.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Pluggable source of foreign-exchange rates for {@code FxRateIngestionService}.
 * <p>
 * Implementations are optional Spring beans (see {@code FrankfurterFxRateProvider}, gated behind
 * {@code @ConditionalOnProperty} and disabled by default), mirroring the {@code PaymentProvider}
 * pattern: when no implementation is wired the scheduled ingestion no-ops and the manual
 * {@code ExchangeRateService} CRUD plus the {@code getReportingRate} staleness guard remain the only
 * path. Swapping providers (keyless majors vs a paid full-coverage API) is an implementation choice
 * behind this interface.
 */
public interface FxRateProvider {
    /**
     * Short stable identifier recorded as {@code ExchangeRate.source} (e.g. {@code "frankfurter"}).
     */
    String name();

    /**
     * Latest available rates expressed as <em>units of each requested target per 1 unit of
     * {@code base}</em> — matching {@code ExchangeRate.rate} semantics (toCcy per 1 fromCcy when
     * fromCcy = base). Targets the provider does not support are omitted from the result; the
     * implementation returns an empty map (never throws) when the upstream call fails, so a provider
     * outage degrades to "no new rates" rather than aborting the ingestion run.
     *
     * @param base    the base currency (1 unit)
     * @param targets the quote currencies to price against the base
     * @return rate per target; empty if unavailable
     */
    Map<CurrencyCode, BigDecimal> fetchLatestRates(CurrencyCode base, Set<CurrencyCode> targets);
}
