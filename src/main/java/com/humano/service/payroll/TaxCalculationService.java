package com.humano.service.payroll;

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
@Transactional
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

        validateTaxBracket(request);

        TaxBracket bracket = new TaxBracket();
        bracket.setCountry(country);
        bracket.setTaxCode(request.taxCode());
        bracket.setLower(request.lower());
        bracket.setUpper(request.upper());
        bracket.setRate(request.rate());
        bracket.setFixedPart(request.fixedPart() != null ? request.fixedPart() : BigDecimal.ZERO);
        bracket.setValidFrom(request.validFrom());
        bracket.setValidTo(request.validTo());

        bracket = taxBracketRepository.save(bracket);
        log.info("Created tax bracket {} for {} in {}", bracket.getId(), request.taxCode(), country.getName());

        return toResponse(bracket);
    }

    /**
     * Calculates progressive tax for a given taxable income (REST-facing entry point).
     * Looks up the active brackets, delegates the math to {@link #calculateProgressiveTax},
     * and assembles the per-bracket breakdown DTO.
     */
    @Transactional(readOnly = true)
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
     * <p><b>Assumption.</b> The bracket set is contiguous (each bracket's {@code lower}
     * equals the previous bracket's {@code upper}) — the standard taxation convention.
     * Tenants seeding non-contiguous brackets will see income that falls in a "gap"
     * taxed as if it fell in the next bracket up.
     *
     * <p><b>fixedPart usage.</b> The spec's literal recipe adds {@code bracket.fixedPart}
     * once per processed bracket, modelling a per-bracket flat fee. If your jurisdiction
     * uses {@code fixedPart} as "precomputed cumulative tax of all lower brackets"
     * (an optimisation for single-bracket lookup), seed all rows with
     * {@code fixedPart = 0} to avoid double-counting.
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
     * Gets all active tax brackets for a country and tax code.
     */
    @Transactional(readOnly = true)
    public List<TaxBracketResponse> getActiveBrackets(UUID countryId, TaxCode taxCode, LocalDate asOfDate) {
        return getActiveBracketsForCalculation(countryId, taxCode, asOfDate).stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Returns the {@link TaxBracket} entities active for ({@code countryId, taxCode}) on
     * {@code asOfDate} (defaults to today if null). Public so {@link PayrollProcessingService}
     * step 7 can call it without duplicating the spec query.
     */
    @Transactional(readOnly = true)
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
     * Copies tax brackets from one year to another for a country.
     * Useful for annual tax table updates.
     */
    public List<TaxBracketResponse> copyBracketsToNewYear(
        UUID countryId,
        TaxCode taxCode,
        int sourceYear,
        int targetYear,
        BigDecimal adjustmentPercentage
    ) {
        LocalDate sourceStart = LocalDate.of(sourceYear, 1, 1);
        LocalDate sourceEnd = LocalDate.of(sourceYear, 12, 31);
        LocalDate targetStart = LocalDate.of(targetYear, 1, 1);

        List<TaxBracket> sourceBrackets = taxBracketRepository.findAll(
            (Specification<TaxBracket>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("country").get("id"), countryId),
                    cb.equal(root.get("taxCode"), taxCode),
                    cb.lessThanOrEqualTo(root.get("validFrom"), sourceEnd),
                    cb.or(cb.isNull(root.get("validTo")), cb.greaterThanOrEqualTo(root.get("validTo"), sourceStart))
                )
        );

        BigDecimal multiplier = adjustmentPercentage != null
            ? BigDecimal.ONE.add(adjustmentPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
            : BigDecimal.ONE;

        List<TaxBracket> newBrackets = new ArrayList<>();
        for (TaxBracket source : sourceBrackets) {
            TaxBracket newBracket = new TaxBracket();
            newBracket.setCountry(source.getCountry());
            newBracket.setTaxCode(source.getTaxCode());
            newBracket.setLower(source.getLower().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setUpper(source.getUpper().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setRate(source.getRate()); // Rates typically stay the same
            newBracket.setFixedPart(source.getFixedPart().multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            newBracket.setValidFrom(targetStart);
            newBrackets.add(newBracket);
        }

        List<TaxBracket> savedBrackets = taxBracketRepository.saveAll(newBrackets);
        log.info("Copied {} tax brackets from {} to {} for country {}", savedBrackets.size(), sourceYear, targetYear, countryId);

        return savedBrackets.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Gets tax brackets summary for all countries and tax codes.
     */
    @Transactional(readOnly = true)
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

    private void validateTaxBracket(CreateTaxBracketRequest request) {
        if (request.lower().compareTo(request.upper()) >= 0) {
            throw new BusinessRuleViolationException("Lower bound must be less than upper bound");
        }

        if (request.rate().compareTo(BigDecimal.ZERO) < 0 || request.rate().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessRuleViolationException("Rate must be between 0 and 1 (0% to 100%)");
        }

        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new BusinessRuleViolationException("Valid-to date cannot be before valid-from date");
        }
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
            bracket.getValidFrom(),
            bracket.getValidTo(),
            isActive
        );
    }
}
