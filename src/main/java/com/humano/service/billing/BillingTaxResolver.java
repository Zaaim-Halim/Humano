package com.humano.service.billing;

import com.humano.domain.billing.CountryTaxRate;
import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.enumeration.CountryCode;
import com.humano.repository.billing.CountryTaxRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the tax rate + amount applicable to a billing invoice (P4.1).
 *
 * <p>v1 contract: <em>per-country flat rate</em>. The resolver looks up the active
 * {@link CountryTaxRate} row for the tenant's country code at {@code asOfDate} and
 * applies its rate to the supplied subtotal. {@link SubscriptionPlan} is accepted in the
 * signature but ignored today; a future revision can branch on it (e.g. enterprise
 * plans exempt, training credits reduced rate).
 *
 * <p>When no rate row exists for the country, returns a zero result rather than failing
 * — the invoice issuance shouldn't bounce just because an operator hasn't seeded the
 * country yet. Operators see a log line on the warn channel so the missing seed is
 * visible without blocking lifecycle progression.
 */
@Service
@Transactional(readOnly = true, transactionManager = "masterTransactionManager")
public class BillingTaxResolver {

    private static final Logger log = LoggerFactory.getLogger(BillingTaxResolver.class);

    /** Scale for invoice money values. Matches {@code Invoice.amount}'s scale=4 column. */
    private static final int MONEY_SCALE = 4;

    private final CountryTaxRateRepository taxRateRepository;

    public BillingTaxResolver(CountryTaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    /**
     * Resolves the tax for {@code (country, plan, subtotal)} at {@code asOfDate}.
     *
     * @param country the tenant's country (required; pulled from {@code Tenant.country}).
     * @param plan the subscription plan being billed. Accepted for future per-plan
     *             differentiation; ignored in v1.
     * @param subtotal the pre-tax amount. Required, non-null.
     * @param asOfDate the date the tax should be effective (typically the invoice
     *                 issue date). When {@code null} defaults to today.
     * @return a {@link TaxResult} carrying the resolved rate + amount + tax name.
     *         When no rate is configured for the country, all fields are zero / empty.
     */
    public TaxResult resolve(CountryCode country, SubscriptionPlan plan, BigDecimal subtotal, LocalDate asOfDate) {
        if (subtotal == null || subtotal.signum() <= 0) {
            return TaxResult.zero();
        }
        if (country == null) {
            log.warn("BillingTaxResolver: country was null; charging zero tax on subtotal {}", subtotal);
            return TaxResult.zero();
        }
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        Optional<CountryTaxRate> rateOpt = taxRateRepository.findActive(country, effectiveDate);
        if (rateOpt.isEmpty()) {
            log.warn(
                "BillingTaxResolver: no active tax rate configured for {} on {}; charging zero tax on subtotal {}",
                country,
                effectiveDate,
                subtotal
            );
            return TaxResult.zero();
        }
        CountryTaxRate row = rateOpt.get();
        BigDecimal rate = row.getTaxRate();
        BigDecimal amount = subtotal.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new TaxResult(rate, amount, row.getTaxName());
    }

    /**
     * Resolved tax snapshot persisted on the invoice. {@code rate} is the decimal ratio
     * (0..1); {@code amount} is subtotal × rate at {@link #MONEY_SCALE}; {@code name} is
     * a human-readable label (e.g. {@code VAT}, {@code GST}) for use on invoice line text.
     */
    public record TaxResult(BigDecimal rate, BigDecimal amount, String name) {
        public static TaxResult zero() {
            return new TaxResult(BigDecimal.ZERO, BigDecimal.ZERO, "");
        }
    }
}
