package com.humano.service.payroll.fx;

import com.humano.config.multitenancy.TenantIteration;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.OrganizationSettings;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.OrganizationSettingsRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.service.payroll.ExchangeRateService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled foreign-exchange rate ingestion (#8).
 * <p>
 * Opt-in: runs only when an {@link FxRateProvider} bean is wired (none by default — see
 * {@code FrankfurterFxRateProvider}). When absent, this no-ops and the manual
 * {@code ExchangeRateService} CRUD plus the {@code getReportingRate} staleness guard remain the only
 * path; nothing here changes existing rows or that guard.
 *
 * <h3>Shape</h3>
 * <ol>
 *   <li><b>Usage-derived pairs.</b> Only the rate pairs a tenant actually needs are fetched —
 *       {@code distinct compensation currency} → {@code (default reporting currency ∪ run reporting
 *       currencies)} — not the whole currency catalog.</li>
 *   <li><b>Fetch once, write per tenant.</b> The union of needed pairs across all tenants is fetched
 *       from the provider a single time (grouped by base, one call per base), then persisted into each
 *       tenant DB via {@link TenantIteration}. Calling the provider per tenant would be N× redundant
 *       and risk a rate-limit ban on a keyless API.</li>
 * </ol>
 *
 * <p><b>Verification note:</b> the live provider call is exercised only when enabled in a real
 * environment; unit tests cover the pure seams (pair selection, response parsing, idempotent upsert),
 * not the HTTP round-trip.
 */
@Service
public class FxRateIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FxRateIngestionService.class);

    private final ObjectProvider<FxRateProvider> fxRateProvider;
    private final TenantIteration tenantIteration;
    private final CompensationRepository compensationRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateService exchangeRateService;

    public FxRateIngestionService(
        ObjectProvider<FxRateProvider> fxRateProvider,
        TenantIteration tenantIteration,
        CompensationRepository compensationRepository,
        PayrollRunRepository payrollRunRepository,
        OrganizationSettingsRepository organizationSettingsRepository,
        CurrencyRepository currencyRepository,
        ExchangeRateService exchangeRateService
    ) {
        this.fxRateProvider = fxRateProvider;
        this.tenantIteration = tenantIteration;
        this.compensationRepository = compensationRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.currencyRepository = currencyRepository;
        this.exchangeRateService = exchangeRateService;
    }

    /** One foreign-exchange pair: {@code rate} stored is units of {@code to} per 1 unit of {@code from}. */
    record CurrencyPair(CurrencyCode from, CurrencyCode to) {}

    /**
     * The rate pairs needed: every source currency crossed with every target currency, minus identity
     * pairs. Pure for unit testing.
     */
    static Set<CurrencyPair> neededPairs(Set<CurrencyCode> sources, Set<CurrencyCode> targets) {
        Set<CurrencyPair> pairs = new HashSet<>();
        for (CurrencyCode from : sources) {
            for (CurrencyCode to : targets) {
                if (from != null && to != null && from != to) {
                    pairs.add(new CurrencyPair(from, to));
                }
            }
        }
        return pairs;
    }

    @Scheduled(cron = "${humano.payroll.fx.cron:0 30 4 * * *}")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void ingestRates() {
        FxRateProvider provider = fxRateProvider.getIfAvailable();
        if (provider == null) {
            log.debug("FX ingestion skipped: no FxRateProvider bean configured");
            return;
        }

        // Pass 1 — union of pairs every active tenant needs (read-only, per-tenant context).
        Set<CurrencyPair> allPairs = ConcurrentHashMap.newKeySet();
        tenantIteration.forEachActiveTenant(subdomain -> allPairs.addAll(currentTenantPairs()));
        if (allPairs.isEmpty()) {
            log.info("FX ingestion: no in-use currency pairs across tenants; nothing to fetch");
            return;
        }

        // Fetch once, outside the tenant loop.
        Map<CurrencyPair, BigDecimal> rates = fetchRates(provider, allPairs);
        if (rates.isEmpty()) {
            log.warn("FX ingestion: provider '{}' returned no rates for {} requested pairs", provider.name(), allPairs.size());
            return;
        }

        // NOTE: server-local date. Tenant-timezone period boundaries are a separate concern (#9).
        LocalDate asOf = LocalDate.now();
        tenantIteration.forEachActiveTenant(subdomain -> persistCurrentTenant(rates, asOf, provider.name()));
        log.info("FX ingestion complete: provider '{}', {} of {} pairs priced", provider.name(), rates.size(), allPairs.size());
    }

    /** Source/target currencies in use for the tenant in the current {@code TenantContext}. */
    private Set<CurrencyPair> currentTenantPairs() {
        Set<CurrencyCode> sources = compensationRepository
            .findDistinctCurrencies()
            .stream()
            .map(Currency::getCode)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<CurrencyCode> targets = new HashSet<>();
        organizationSettingsRepository
            .findAll()
            .stream()
            .findFirst()
            .map(OrganizationSettings::getDefaultCurrency)
            .map(Currency::getCode)
            .ifPresent(targets::add);
        payrollRunRepository
            .findDistinctReportingCurrencies()
            .stream()
            .map(Currency::getCode)
            .filter(Objects::nonNull)
            .forEach(targets::add);

        return neededPairs(sources, targets);
    }

    /**
     * Groups pairs by base currency and asks the provider once per base, keeping the stored key as
     * {@code (from, to)} = base→target so the rate stays "target per 1 base" (matching
     * {@code ExchangeRate.rate}). Pure for unit testing; the one-call-per-base grouping is the
     * rate-limit-safety property.
     */
    static Map<CurrencyPair, BigDecimal> fetchRates(FxRateProvider provider, Set<CurrencyPair> pairs) {
        Map<CurrencyCode, Set<CurrencyCode>> targetsByBase = new HashMap<>();
        for (CurrencyPair pair : pairs) {
            targetsByBase.computeIfAbsent(pair.from(), k -> new HashSet<>()).add(pair.to());
        }
        Map<CurrencyPair, BigDecimal> rates = new HashMap<>();
        targetsByBase.forEach((base, targets) ->
            provider.fetchLatestRates(base, targets).forEach((to, rate) -> rates.put(new CurrencyPair(base, to), rate))
        );
        return rates;
    }

    /** Upserts the rows the current-context tenant needs, skipping pairs the provider didn't price. */
    private void persistCurrentTenant(Map<CurrencyPair, BigDecimal> rates, LocalDate asOf, String source) {
        Map<CurrencyCode, Currency> byCode = new HashMap<>();
        int written = 0;
        for (CurrencyPair pair : currentTenantPairs()) {
            BigDecimal rate = rates.get(pair);
            if (rate == null) {
                continue;
            }
            Currency from = byCode.computeIfAbsent(pair.from(), c -> currencyRepository.findByCode(c).orElse(null));
            Currency to = byCode.computeIfAbsent(pair.to(), c -> currencyRepository.findByCode(c).orElse(null));
            if (from == null || to == null) {
                continue;
            }
            exchangeRateService.upsertProviderRate(from, to, asOf, rate, source);
            written++;
        }
        if (written > 0) {
            log.debug(
                "FX ingestion wrote {} rate(s) for tenant {}",
                written,
                com.humano.config.multitenancy.TenantContext.getCurrentTenant()
            );
        }
    }
}
