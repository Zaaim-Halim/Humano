package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.TaxBracketBasis;
import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.domain.payroll.TaxBracket;
import com.humano.domain.shared.Country;
import com.humano.dto.payroll.request.CreateTaxBracketRequest;
import com.humano.dto.payroll.response.TaxBracketResponse;
import com.humano.dto.payroll.response.TaxCalculationResponse;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.TaxBracketRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tax brackets and calculating progressive taxes.
 * Supports multiple countries and tax codes with date-effective brackets.
 */
@Service
@Transactional("tenantTransactionManager")
public class TaxCalculationService {

    private static final Logger log = LoggerFactory.getLogger(TaxCalculationService.class);

    private final TaxBracketRepository taxBracketRepository;
    private final CountryRepository countryRepository;

    public TaxCalculationService(TaxBracketRepository taxBracketRepository, CountryRepository countryRepository) {
        this.taxBracketRepository = taxBracketRepository;
        this.countryRepository = countryRepository;
    }

    /**
     * Creates a new tax bracket.
     */
    public TaxBracketResponse createTaxBracket(CreateTaxBracketRequest request) {
        log.debug("Creating tax bracket for country: {}, code: {}", request.countryId(), request.taxCode());

        Country country = countryRepository
            .findById(request.countryId())
            .orElseThrow(() -> new EntityNotFoundException("Country", request.countryId()));

        validateBounds(request.lower(), request.upper(), request.rate());
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new BusinessRuleViolationException("Valid-to date cannot be before valid-from date");
        }

        TaxBracket bracket = new TaxBracket();
        bracket.setCountry(country);
        bracket.setTaxCode(request.taxCode());
        bracket.setLower(request.lower());
        bracket.setUpper(request.upper());
        bracket.setRate(request.rate());
        bracket.setFixedPart(request.fixedPart() != null ? request.fixedPart() : BigDecimal.ZERO);
        bracket.setBasis(request.basis() != null ? request.basis() : TaxBracketBasis.PER_PERIOD);
        bracket.setValidFrom(request.validFrom());
        bracket.setValidTo(request.validTo());
        assertNoConflicts(bracket);

        bracket = taxBracketRepository.save(bracket);
        log.info("Created tax bracket {} for {} in {}", bracket.getId(), request.taxCode(), country.getName());

        return toResponse(bracket);
    }

    /**
     * Calculates progressive tax for a given taxable income (REST-facing entry point).
     * Looks up the active brackets, delegates the math to {@link #calculateProgressiveTax},
     * and assembles the per-bracket breakdown DTO. {@code taxableIncome} is read in the
     * brackets' own {@link TaxBracketBasis} — an annual amount for ANNUAL brackets, a
     * per-period amount for PER_PERIOD ones.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public TaxCalculationResponse calculateTax(
        UUID countryId,
        TaxCode taxCode,
        BigDecimal taxableIncome,
        LocalDate asOfDate,
        UUID employeeId
    ) {
        log.debug("Calculating {} tax for income {} in country {}", taxCode, taxableIncome, countryId);

        List<TaxBracket> brackets = getActiveBracketsForCalculation(countryId, taxCode, asOfDate);
        if (brackets.isEmpty()) {
            throw new BusinessRuleViolationException("No active tax brackets found for " + taxCode + " in country " + countryId);
        }
        validateBracketSet(brackets);

        BigDecimal totalTax = calculateProgressiveTax(taxableIncome, brackets);

        // Per-bracket breakdown for the REST response — mirrors the slicing in
        // calculateProgressiveTax so the response matches the total.
        List<TaxCalculationResponse.TaxBracketApplication> bracketBreakdown = new ArrayList<>();
        BigDecimal remaining = taxableIncome == null ? BigDecimal.ZERO : taxableIncome;
        for (TaxBracket bracket : brackets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal width = bracket.getUpper().subtract(bracket.getLower());
            BigDecimal slice = remaining.min(width);
            if (slice.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal sliceTax = slice
                .multiply(bracket.getRate())
                .add(bracket.getFixedPart() == null ? BigDecimal.ZERO : bracket.getFixedPart())
                .setScale(2, RoundingMode.HALF_UP);
            bracketBreakdown.add(
                new TaxCalculationResponse.TaxBracketApplication(
                    bracket.getLower(),
                    bracket.getUpper(),
                    bracket.getRate().multiply(BigDecimal.valueOf(100)),
                    slice,
                    sliceTax
                )
            );
            remaining = remaining.subtract(slice);
        }

        BigDecimal effectiveTaxRate = taxableIncome != null && taxableIncome.compareTo(BigDecimal.ZERO) > 0
            ? totalTax.divide(taxableIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return new TaxCalculationResponse(
            employeeId,
            taxableIncome,
            totalTax,
            effectiveTaxRate,
            null, // Currency would come from context
            bracketBreakdown,
            Collections.emptyList() // Deductions would be added by caller
        );
    }

    /**
     * Pure progressive-tax math: given a taxable income and a set of brackets, slice the
     * income across the brackets in ascending lower-bound order and accumulate the tax.
     *
     * <p>Per the §P3.3 spec:
     * <pre>
     *   sort brackets by lower
     *   remaining = taxableIncome
     *   tax = 0
     *   for each bracket:
     *     if remaining <= 0: break
     *     slice = min(remaining, bracket.upper - bracket.lower)
     *     tax += slice * bracket.rate + bracket.fixedPart
     *     remaining -= slice
     *   return tax
     * </pre>
     *
     * <p><b>Assumption.</b> The bracket set starts at 0 and is contiguous (each bracket's
     * {@code lower} equals the previous bracket's {@code upper}). The slicing uses bracket
     * widths, not positions, so a set that skips the 0% band, has a gap, or overlaps is
     * mis-sliced — callers enforce the shape with {@link #validateBracketSet} first.
     *
     * <p><b>fixedPart usage.</b> {@code bracket.fixedPart} is added once per processed
     * bracket, modelling a per-bracket flat amount. It is NOT the cumulative tax of the lower
     * brackets (the slices already sum those); tables published in that form must be seeded
     * with {@code fixedPart = 0}.
     *
     * <p>Thresholds and income are in the same unit; annualisation for
     * {@link TaxBracketBasis#ANNUAL} sets happens in {@link #calculatePeriodTax}.
     *
     * <p>Returns 0 for null or non-positive {@code taxableIncome}, and for an empty
     * bracket list. Result is scaled to 2 decimals (HALF_UP).
     */
    public BigDecimal calculateProgressiveTax(BigDecimal taxableIncome, List<TaxBracket> brackets) {
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0 || brackets == null || brackets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<TaxBracket> sorted = new ArrayList<>(brackets);
        sorted.sort(Comparator.comparing(TaxBracket::getLower));
        BigDecimal remaining = taxableIncome;
        BigDecimal tax = BigDecimal.ZERO;
        for (TaxBracket bracket : sorted) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal width = bracket.getUpper().subtract(bracket.getLower());
            BigDecimal slice = remaining.min(width);
            BigDecimal sliceTax = slice
                .multiply(bracket.getRate())
                .add(bracket.getFixedPart() == null ? BigDecimal.ZERO : bracket.getFixedPart());
            tax = tax.add(sliceTax);
            remaining = remaining.subtract(slice);
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Tax for one pay period on {@code taxableIncome} (a per-period amount), honouring the set's
     * {@link TaxBracketBasis}:
     * <ul>
     *   <li>PER_PERIOD — brackets are applied to the period income as-is.</li>
     *   <li>ANNUAL — the income is annualised ({@code × periodsPerYear}), the brackets applied, and
     *   the annual tax divided back by {@code periodsPerYear} (2 decimals, HALF_UP).</li>
     * </ul>
     * Validates the set's shape first, so a malformed set fails loudly instead of being mis-sliced.
     *
     * @throws BusinessRuleViolationException if the set fails {@link #validateBracketSet}
     */
    public BigDecimal calculatePeriodTax(BigDecimal taxableIncome, List<TaxBracket> brackets, int periodsPerYear) {
        validateBracketSet(brackets);
        if (brackets == null || brackets.isEmpty() || brackets.get(0).getBasis() != TaxBracketBasis.ANNUAL) {
            return calculateProgressiveTax(taxableIncome, brackets);
        }
        if (taxableIncome == null || taxableIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal periods = BigDecimal.valueOf(periodsPerYear);
        BigDecimal annualTax = calculateProgressiveTax(taxableIncome.multiply(periods), brackets);
        return annualTax.divide(periods, 2, RoundingMode.HALF_UP);
    }

    /**
     * Checks that a set of brackets active together (one country, tax code and date) can be sliced
     * correctly: the lowest bracket starts at 0, each bracket starts where the previous one ends (no
     * gaps, no overlaps — e.g. two years' rows left active together), and all share one basis.
     * An empty or null set is valid (nothing to tax).
     *
     * @throws BusinessRuleViolationException describing the first problem found
     */
    public void validateBracketSet(List<TaxBracket> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            return;
        }
        List<TaxBracket> sorted = new ArrayList<>(brackets);
        sorted.sort(Comparator.comparing(TaxBracket::getLower));
        TaxBracket first = sorted.get(0);
        String scope = first.getTaxCode() + " brackets" + (first.getCountry() != null ? " for " + first.getCountry().getCode() : "");
        if (first.getLower().signum() != 0) {
            throw new BusinessRuleViolationException(
                scope + " must start at 0 but the lowest starts at " + first.getLower() + "; add a 0% bracket for the tax-free band"
            );
        }
        for (int i = 1; i < sorted.size(); i++) {
            TaxBracket prev = sorted.get(i - 1);
            TaxBracket cur = sorted.get(i);
            int cmp = cur.getLower().compareTo(prev.getUpper());
            if (cmp < 0) {
                throw new BusinessRuleViolationException(
                    scope + " overlap: " + range(prev) + " and " + range(cur) + " are active at the same time"
                );
            }
            if (cmp > 0) {
                throw new BusinessRuleViolationException(scope + " have a gap between " + prev.getUpper() + " and " + cur.getLower());
            }
            if (cur.getBasis() != first.getBasis()) {
                throw new BusinessRuleViolationException(scope + " mix " + first.getBasis() + " and " + cur.getBasis() + " thresholds");
            }
        }
    }

    /**
     * Gets all active tax brackets for a country and tax code.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<TaxBracketResponse> getActiveBrackets(UUID countryId, TaxCode taxCode, LocalDate asOfDate) {
        return getActiveBracketsForCalculation(countryId, taxCode, asOfDate).stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Returns the {@link TaxBracket} entities active for ({@code countryId, taxCode}) on
     * {@code asOfDate} (defaults to today if null). Public so {@link PayrollProcessingService}
     * step 7 can call it without duplicating the spec query.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<TaxBracket> getActiveBracketsForCalculation(UUID countryId, TaxCode taxCode, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        return taxBracketRepository.findAll(
            (Specification<TaxBracket>) (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("country").get("id"), countryId));
                predicates.add(cb.equal(root.get("taxCode"), taxCode));
                predicates.add(cb.lessThanOrEqualTo(root.get("validFrom"), effectiveDate));
                predicates.add(cb.or(cb.isNull(root.get("validTo")), cb.greaterThanOrEqualTo(root.get("validTo"), effectiveDate)));
                if (query != null) {
                    query.orderBy(cb.asc(root.get("lower")));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }
        );
    }

    /**
     * Updates a tax bracket's rates or bounds.
     */
    public TaxBracketResponse updateTaxBracket(
        UUID bracketId,
        BigDecimal newLower,
        BigDecimal newUpper,
        BigDecimal newRate,
        BigDecimal newFixedPart
    ) {
        TaxBracket bracket = taxBracketRepository
            .findById(bracketId)
            .orElseThrow(() -> new EntityNotFoundException("TaxBracket", bracketId));

        if (newLower != null) bracket.setLower(newLower);
        if (newUpper != null) bracket.setUpper(newUpper);
        if (newRate != null) bracket.setRate(newRate);
        if (newFixedPart != null) bracket.setFixedPart(newFixedPart);
        validateBounds(bracket.getLower(), bracket.getUpper(), bracket.getRate());
        assertNoConflicts(bracket);

        bracket = taxBracketRepository.save(bracket);
        log.info("Updated tax bracket {}", bracketId);

        return toResponse(bracket);
    }

    /**
     * Expires a tax bracket by setting its valid-to date.
     */
    public TaxBracketResponse expireTaxBracket(UUID bracketId, LocalDate expirationDate) {
        TaxBracket bracket = taxBracketRepository
            .findById(bracketId)
            .orElseThrow(() -> new EntityNotFoundException("TaxBracket", bracketId));

        bracket.setValidTo(expirationDate);
        bracket = taxBracketRepository.save(bracket);

        log.info("Expired tax bracket {} as of {}", bracketId, expirationDate);
        return toResponse(bracket);
    }

    /**
     * Copies the bracket set in force at the end of {@code sourceYear} into a new set starting
     * Jan 1 of {@code targetYear}, scaling thresholds by {@code adjustmentPercentage}. The source
     * rows are closed the day before the new set starts, so the two sets are never active together.
     * Refuses when the target year already has brackets of its own.
     */
    public List<TaxBracketResponse> copyBracketsToNewYear(
        UUID countryId,
        TaxCode taxCode,
        int sourceYear,
        int targetYear,
        BigDecimal adjustmentPercentage
    ) {
        if (targetYear <= sourceYear) {
            throw new BusinessRuleViolationException("Target year must be after the source year");
        }
        LocalDate sourceEnd = LocalDate.of(sourceYear, 12, 31);
        LocalDate targetStart = LocalDate.of(targetYear, 1, 1);

        // The set in force on Dec 31 — not every row that touched the source year, which would pull in
        // brackets superseded mid-year and duplicate income ranges in the new set.
        List<TaxBracket> sourceBrackets = getActiveBracketsForCalculation(countryId, taxCode, sourceEnd);
        if (sourceBrackets.isEmpty()) {
            log.info("No {} brackets in force on {} for country {}; nothing to copy", taxCode, sourceEnd, countryId);
            return List.of();
        }
        validateBracketSet(sourceBrackets);

        Set<UUID> sourceIds = sourceBrackets.stream().map(TaxBracket::getId).collect(Collectors.toSet());
        boolean targetTaken = findWithOverlappingValidity(countryId, taxCode, targetStart, null)
            .stream()
            .anyMatch(b -> !sourceIds.contains(b.getId()));
        if (targetTaken) {
            throw new BusinessRuleViolationException(
                taxCode + " brackets already exist from " + targetStart + "; expire them before copying " + sourceYear
            );
        }

        LocalDate sourceClose = targetStart.minusDays(1);
        for (TaxBracket source : sourceBrackets) {
            if (source.getValidTo() == null || source.getValidTo().isAfter(sourceClose)) {
                source.setValidTo(sourceClose);
            }
        }
        taxBracketRepository.saveAll(sourceBrackets);

        BigDecimal multiplier = adjustmentPercentage != null
            ? BigDecimal.ONE.add(adjustmentPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
            : BigDecimal.ONE;

        List<TaxBracket> newBrackets = new ArrayList<>();
        for (TaxBracket source : sourceBrackets) {
            BigDecimal fixedPart = source.getFixedPart() != null ? source.getFixedPart() : BigDecimal.ZERO;
            TaxBracket newBracket = new TaxBracket();
            newBracket.setCountry(source.getCountry());
            newBracket.setTaxCode(source.getTaxCode());
            newBracket.setBasis(source.getBasis());
            newBracket.setLower(source.getLower().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setUpper(source.getUpper().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setRate(source.getRate()); // Rates typically stay the same
            newBracket.setFixedPart(fixedPart.multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setValidFrom(targetStart);
            newBrackets.add(newBracket);
        }

        List<TaxBracket> savedBrackets = taxBracketRepository.saveAll(newBrackets);
        log.info(
            "Copied {} tax brackets from {} to {} for country {}; source rows closed on {}",
            savedBrackets.size(),
            sourceYear,
            targetYear,
            countryId,
            sourceClose
        );

        return savedBrackets.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Gets tax brackets summary for all countries and tax codes.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Map<String, Object> getTaxBracketsSummary(LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        List<TaxBracket> allActiveBrackets = taxBracketRepository.findAll(
            (Specification<TaxBracket>) (root, query, cb) ->
                cb.and(
                    cb.lessThanOrEqualTo(root.get("validFrom"), effectiveDate),
                    cb.or(cb.isNull(root.get("validTo")), cb.greaterThanOrEqualTo(root.get("validTo"), effectiveDate))
                )
        );

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalActiveBrackets", allActiveBrackets.size());

        // Group by country
        Map<String, Long> byCountry = allActiveBrackets
            .stream()
            .collect(Collectors.groupingBy(b -> b.getCountry().getName(), Collectors.counting()));
        summary.put("byCountry", byCountry);

        // Group by tax code
        Map<String, Long> byTaxCode = allActiveBrackets
            .stream()
            .collect(Collectors.groupingBy(b -> b.getTaxCode().name(), Collectors.counting()));
        summary.put("byTaxCode", byTaxCode);

        return summary;
    }

    private void validateBounds(BigDecimal lower, BigDecimal upper, BigDecimal rate) {
        if (lower.compareTo(upper) >= 0) {
            throw new BusinessRuleViolationException("Lower bound must be less than upper bound");
        }

        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessRuleViolationException("Rate must be between 0 and 1 (0% to 100%)");
        }

        // The column holds 6 decimals; anything finer would be rounded silently by the database.
        if (rate.stripTrailingZeros().scale() > 6) {
            throw new BusinessRuleViolationException("Rate can have at most 6 decimals (e.g. 0.03078 for 3.078%)");
        }
    }

    /**
     * Rejects {@code candidate} if, during any part of its validity window, another bracket for the same
     * country and tax code covers an overlapping income range or uses a different basis. Gaps aren't
     * checked here because sets are built one bracket at a time; {@link #validateBracketSet} catches
     * them at calculation time.
     */
    private void assertNoConflicts(TaxBracket candidate) {
        List<TaxBracket> concurrent = findWithOverlappingValidity(
            candidate.getCountry().getId(),
            candidate.getTaxCode(),
            candidate.getValidFrom(),
            candidate.getValidTo()
        );
        for (TaxBracket other : concurrent) {
            if (other.getId() != null && other.getId().equals(candidate.getId())) {
                continue;
            }
            if (other.getBasis() != candidate.getBasis()) {
                throw new BusinessRuleViolationException(
                    "A " +
                    other.getBasis() +
                    " " +
                    candidate.getTaxCode() +
                    " bracket set is already active in this period; " +
                    "a set can't mix ANNUAL and PER_PERIOD thresholds"
                );
            }
            if (candidate.getLower().compareTo(other.getUpper()) < 0 && other.getLower().compareTo(candidate.getUpper()) < 0) {
                throw new BusinessRuleViolationException(
                    "Bracket " + range(candidate) + " overlaps existing bracket " + range(other) + " valid from " + other.getValidFrom()
                );
            }
        }
    }

    /** Brackets for (country, tax code) whose validity window overlaps [{@code from}, {@code to}]; a null {@code to} is open-ended. */
    private List<TaxBracket> findWithOverlappingValidity(UUID countryId, TaxCode taxCode, LocalDate from, LocalDate to) {
        return taxBracketRepository.findAll(
            (Specification<TaxBracket>) (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("country").get("id"), countryId));
                predicates.add(cb.equal(root.get("taxCode"), taxCode));
                predicates.add(cb.or(cb.isNull(root.get("validTo")), cb.greaterThanOrEqualTo(root.get("validTo"), from)));
                if (to != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("validFrom"), to));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }
        );
    }

    private static String range(TaxBracket bracket) {
        return "[" + bracket.getLower() + ", " + bracket.getUpper() + ")";
    }

    private TaxBracketResponse toResponse(TaxBracket bracket) {
        boolean isActive = bracket.getValidTo() == null || !bracket.getValidTo().isBefore(LocalDate.now());

        return new TaxBracketResponse(
            bracket.getId(),
            bracket.getCountry().getId(),
            bracket.getCountry().getName(),
            bracket.getTaxCode(),
            bracket.getLower(),
            bracket.getUpper(),
            bracket.getRate(),
            bracket.getRate().multiply(BigDecimal.valueOf(100)),
            bracket.getFixedPart(),
            bracket.getBasis(),
            bracket.getValidFrom(),
            bracket.getValidTo(),
            isActive
        );
    }
}
