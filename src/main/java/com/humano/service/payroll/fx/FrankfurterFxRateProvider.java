package com.humano.service.payroll.fx;

import com.humano.domain.enumeration.CurrencyCode;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Keyless {@link FxRateProvider} backed by the ECB-sourced Frankfurter API
 * (<a href="https://www.frankfurter.app">frankfurter.app</a>).
 * <p>
 * Activated only when {@code humano.payroll.fx.frankfurter.enabled=true} (via
 * {@link ConditionalOnProperty}), so the app — and CI — never make a live HTTP call by default; FX
 * ingestion stays opt-in. Coverage is the ~30 major currencies the ECB publishes; pairs outside that
 * set are simply omitted (the ingestion loop keeps any rate it does get and leaves the rest to the
 * staleness guard). For broader coverage, swap in a paid provider behind {@link FxRateProvider}.
 */
@Component
@ConditionalOnProperty(name = "humano.payroll.fx.frankfurter.enabled", havingValue = "true")
public class FrankfurterFxRateProvider implements FxRateProvider {

    private static final Logger LOG = LoggerFactory.getLogger(FrankfurterFxRateProvider.class);

    private final RestClient restClient;

    public FrankfurterFxRateProvider(@Value("${humano.payroll.fx.frankfurter.base-url:https://api.frankfurter.app}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public String name() {
        return "frankfurter";
    }

    @Override
    public Map<CurrencyCode, BigDecimal> fetchLatestRates(CurrencyCode base, Set<CurrencyCode> targets) {
        if (base == null || targets == null || targets.isEmpty()) {
            return Map.of();
        }
        String symbols = targets.stream().map(Enum::name).collect(Collectors.joining(","));
        try {
            FrankfurterResponse response = restClient
                .get()
                .uri(uri -> uri.path("/latest").queryParam("from", base.name()).queryParam("to", symbols).build())
                .retrieve()
                .body(FrankfurterResponse.class);
            return response == null ? Map.of() : parseRates(response.rates());
        } catch (RuntimeException e) {
            LOG.warn("Frankfurter rate fetch failed for base {} ({} targets): {}", base, targets.size(), e.getMessage());
            return Map.of();
        }
    }

    /**
     * Maps the provider's {@code {code -> rate}} payload to {@link CurrencyCode} keys, dropping codes
     * this app doesn't model and non-positive rates. Package-private and pure for unit testing.
     */
    static Map<CurrencyCode, BigDecimal> parseRates(Map<String, BigDecimal> rates) {
        if (rates == null || rates.isEmpty()) {
            return Map.of();
        }
        Map<CurrencyCode, BigDecimal> out = new HashMap<>(rates.size());
        for (Map.Entry<String, BigDecimal> e : rates.entrySet()) {
            CurrencyCode code = CurrencyCode.fromCode(e.getKey());
            if (code != null && e.getValue() != null && e.getValue().signum() > 0) {
                out.put(code, e.getValue());
            }
        }
        return out;
    }

    /** Subset of the Frankfurter {@code /latest} response we consume. */
    record FrankfurterResponse(BigDecimal amount, String base, String date, Map<String, BigDecimal> rates) {}
}
