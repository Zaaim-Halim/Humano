package com.humano.service.payroll;

import com.humano.domain.Currency;
import com.humano.domain.payroll.ExchangeRate;
import com.humano.dto.payroll.request.CreateExchangeRateRequest;
import com.humano.dto.payroll.response.CurrencyConversionResponse;
import com.humano.dto.payroll.response.ExchangeRateResponse;
import com.humano.repository.CurrencyRepository;
import com.humano.repository.payroll.ExchangeRateRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing exchange rates and currency conversions.
 * Supports multi-currency payroll calculations and financial reporting.
 */
@Service
@Transactional
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final int RATE_SCALE = 6;

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRepository currencyRepository;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository, CurrencyRepository currencyRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyRepository = currencyRepository;
    }

    /**
     * Creates a new exchange rate.
     */
    public ExchangeRateResponse createExchangeRate(CreateExchangeRateRequest request) {
        log.debug("Creating exchange rate from {} to {} on {}", request.fromCurrencyId(), request.toCurrencyId(), request.date());

        Currency fromCurrency = currencyRepository
            .findById(request.fromCurrencyId())
            .orElseThrow(() -> new EntityNotFoundException("Currency (from)", request.fromCurrencyId()));

        Currency toCurrency = currencyRepository
            .findById(request.toCurrencyId())
            .orElseThrow(() -> new EntityNotFoundException("Currency (to)", request.toCurrencyId()));

        if (fromCurrency.getId().equals(toCurrency.getId())) {
            throw new BusinessRuleViolationException("Source and target currencies must be different");
        }

        if (request.rate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Exchange rate must be positive");
        }

        // Check for existing rate on same date
        Optional<ExchangeRate> existing = findExactRate(fromCurrency.getId(), toCurrency.getId(), request.date());

        ExchangeRate rate;
        if (existing.isPresent()) {
            if (!request.replaceExisting()) {
                throw new BusinessRuleViolationException("Exchange rate already exists for this currency pair on " + request.date());
            }
            rate = existing.get();
            log.debug("Updating existing exchange rate: {}", rate.getId());
        } else {
            rate = new ExchangeRate();
            rate.setFromCcy(fromCurrency);
            rate.setToCcy(toCurrency);
            rate.setDate(request.date());
        }

        rate.setRate(request.rate().setScale(RATE_SCALE, RoundingMode.HALF_UP));

        rate = exchangeRateRepository.save(rate);
        log.info("Created exchange rate {} -> {} = {} on {}", fromCurrency.getCode(), toCurrency.getCode(), rate.getRate(), rate.getDate());

        return toResponse(rate);
    }

    /**
     * Creates exchange rates in bulk (e.g., daily rate imports).
     */
    public List<ExchangeRateResponse> createBulkRates(List<CreateExchangeRateRequest> requests) {
        log.info("Creating {} bulk exchange rates", requests.size());

        List<ExchangeRate> rates = new ArrayList<>();

        for (CreateExchangeRateRequest request : requests) {
            try {
                Currency fromCurrency = currencyRepository
                    .findById(request.fromCurrencyId())
                    .orElseThrow(() -> new EntityNotFoundException("Currency", request.fromCurrencyId()));

                Currency toCurrency = currencyRepository
                    .findById(request.toCurrencyId())
                    .orElseThrow(() -> new EntityNotFoundException("Currency", request.toCurrencyId()));

                ExchangeRate rate = new ExchangeRate();
                rate.setFromCcy(fromCurrency);
                rate.setToCcy(toCurrency);
                rate.setDate(request.date());
                rate.setRate(request.rate().setScale(RATE_SCALE, RoundingMode.HALF_UP));

                rates.add(rate);
            } catch (Exception e) {
                log.warn("Failed to process rate: {}", e.getMessage());
            }
        }

        List<ExchangeRate> savedRates = exchangeRateRepository.saveAll(rates);
        log.info("Created {} exchange rates", savedRates.size());

        return savedRates.stream().map(this::toResponse).toList();
    }

    /**
     * Gets the exchange rate for a currency pair on a specific date.
     * Falls back to the most recent available rate if no exact match.
     */
    @Transactional(readOnly = true)
    public ExchangeRateResponse getRate(UUID fromCurrencyId, UUID toCurrencyId, LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        // Try exact match first
        Optional<ExchangeRate> exactRate = findExactRate(fromCurrencyId, toCurrencyId, effectiveDate);
        if (exactRate.isPresent()) {
            return toResponse(exactRate.get());
        }

        // Fall back to most recent rate before the date
        Optional<ExchangeRate> recentRate = findMostRecentRate(fromCurrencyId, toCurrencyId, effectiveDate);
        if (recentRate.isPresent()) {
            return toResponse(recentRate.get());
        }

        // Try reverse rate
        Optional<ExchangeRate> reverseRate = findMostRecentRate(toCurrencyId, fromCurrencyId, effectiveDate);
        if (reverseRate.isPresent()) {
            ExchangeRate rate = reverseRate.get();
            // Return inverted rate
            return new ExchangeRateResponse(
                rate.getId(),
                rate.getToCcy().getId(),
                rate.getToCcy().getCode(),
                rate.getToCcy().getName(),
                rate.getFromCcy().getId(),
                rate.getFromCcy().getCode(),
                rate.getFromCcy().getName(),
                BigDecimal.ONE.divide(rate.getRate(), RATE_SCALE, RoundingMode.HALF_UP),
                rate.getRate(),
                rate.getDate()
            );
        }

        throw new EntityNotFoundException("No exchange rate found for currency pair on or before " + effectiveDate);
    }

    /**
     * Converts an amount from one currency to another.
     */
    @Transactional(readOnly = true)
    public CurrencyConversionResponse convert(BigDecimal amount, UUID fromCurrencyId, UUID toCurrencyId, LocalDate date) {
        if (fromCurrencyId.equals(toCurrencyId)) {
            Currency currency = currencyRepository
                .findById(fromCurrencyId)
                .orElseThrow(() -> new EntityNotFoundException("Currency", fromCurrencyId));
            return new CurrencyConversionResponse(
                amount,
                currency.getCode(),
                amount,
                currency.getCode(),
                BigDecimal.ONE,
                date != null ? date : LocalDate.now(),
                null
            );
        }

        ExchangeRateResponse rate = getRate(fromCurrencyId, toCurrencyId, date);
        BigDecimal convertedAmount = amount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP);

        return new CurrencyConversionResponse(
            amount,
            rate.fromCurrencyCode(),
            convertedAmount,
            rate.toCurrencyCode(),
            rate.rate(),
            rate.date(),
            rate.id()
        );
    }

    /**
     * Converts amounts to a base currency for reporting.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> convertToBaseCurrency(Map<UUID, BigDecimal> amounts, UUID baseCurrencyId, LocalDate date) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal> entry : amounts.entrySet()) {
            UUID currencyId = entry.getKey();
            BigDecimal amount = entry.getValue();

            CurrencyConversionResponse conversion = convert(amount, currencyId, baseCurrencyId, date);
            Currency currency = currencyRepository
                .findById(currencyId)
                .orElseThrow(() -> new EntityNotFoundException("Currency", currencyId));

            result.put(currency.getCode(), conversion.convertedAmount());
            total = total.add(conversion.convertedAmount());
        }

        result.put("TOTAL", total);
        return result;
    }

    /**
     * Gets historical exchange rates for a currency pair.
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getHistoricalRates(UUID fromCurrencyId, UUID toCurrencyId, LocalDate startDate, LocalDate endDate) {
        return exchangeRateRepository
            .findAll(
                (Specification<ExchangeRate>) (root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("fromCcy").get("id"), fromCurrencyId));
                    predicates.add(cb.equal(root.get("toCcy").get("id"), toCurrencyId));
                    predicates.add(cb.between(root.get("date"), startDate, endDate));

                    if (query != null) {
                        query.orderBy(cb.asc(root.get("date")));
                    }

                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                }
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets all exchange rates for a specific date.
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getRatesForDate(LocalDate date) {
        return exchangeRateRepository
            .findAll((Specification<ExchangeRate>) (root, query, cb) -> cb.equal(root.get("date"), date))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Gets the latest exchange rates for all currency pairs.
     */
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getLatestRates() {
        // Get all unique currency pairs and their latest rates
        List<ExchangeRate> allRates = exchangeRateRepository.findAll(
            (Specification<ExchangeRate>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.desc(root.get("date")));
                }
                return cb.conjunction();
            }
        );

        // Group by currency pair and get latest for each
        Map<String, ExchangeRate> latestByPair = new LinkedHashMap<>();
        for (ExchangeRate rate : allRates) {
            String pairKey = rate.getFromCcy().getId() + "-" + rate.getToCcy().getId();
            if (!latestByPair.containsKey(pairKey)) {
                latestByPair.put(pairKey, rate);
            }
        }

        return latestByPair.values().stream().map(this::toResponse).toList();
    }

    /**
     * Gets exchange rates with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ExchangeRateResponse> getRates(
        UUID fromCurrencyId,
        UUID toCurrencyId,
        LocalDate fromDate,
        LocalDate toDate,
        Pageable pageable
    ) {
        return exchangeRateRepository
            .findAll(
                (Specification<ExchangeRate>) (root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

                    if (fromCurrencyId != null) {
                        predicates.add(cb.equal(root.get("fromCcy").get("id"), fromCurrencyId));
                    }
                    if (toCurrencyId != null) {
                        predicates.add(cb.equal(root.get("toCcy").get("id"), toCurrencyId));
                    }
                    if (fromDate != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("date"), fromDate));
                    }
                    if (toDate != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("date"), toDate));
                    }

                    return predicates.isEmpty()
                        ? cb.conjunction()
                        : cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                },
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Deletes an exchange rate.
     */
    public void deleteRate(UUID rateId) {
        ExchangeRate rate = exchangeRateRepository.findById(rateId).orElseThrow(() -> new EntityNotFoundException("ExchangeRate", rateId));

        exchangeRateRepository.delete(rate);
        log.info("Deleted exchange rate {}", rateId);
    }

    /**
     * Gets exchange rate statistics for a currency pair.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRateStatistics(UUID fromCurrencyId, UUID toCurrencyId, LocalDate startDate, LocalDate endDate) {
        List<ExchangeRate> rates = exchangeRateRepository.findAll(
            (Specification<ExchangeRate>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("fromCcy").get("id"), fromCurrencyId),
                    cb.equal(root.get("toCcy").get("id"), toCurrencyId),
                    cb.between(root.get("date"), startDate, endDate)
                )
        );

        if (rates.isEmpty()) {
            return Map.of("message", "No rates found for the specified period");
        }

        List<BigDecimal> rateValues = rates.stream().map(ExchangeRate::getRate).sorted().toList();

        BigDecimal min = rateValues.get(0);
        BigDecimal max = rateValues.get(rateValues.size() - 1);
        BigDecimal sum = rateValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(rates.size()), RATE_SCALE, RoundingMode.HALF_UP);

        // Calculate volatility (standard deviation)
        BigDecimal variance = rateValues
            .stream()
            .map(r -> r.subtract(avg).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(rates.size()), RATE_SCALE, RoundingMode.HALF_UP);

        double stdDev = Math.sqrt(variance.doubleValue());

        Currency fromCurrency = rates.get(0).getFromCcy();
        Currency toCurrency = rates.get(0).getToCcy();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("fromCurrency", fromCurrency.getCode());
        stats.put("toCurrency", toCurrency.getCode());
        stats.put("startDate", startDate);
        stats.put("endDate", endDate);
        stats.put("dataPoints", rates.size());
        stats.put("minRate", min);
        stats.put("maxRate", max);
        stats.put("avgRate", avg);
        stats.put("volatility", BigDecimal.valueOf(stdDev).setScale(RATE_SCALE, RoundingMode.HALF_UP));
        stats.put("range", max.subtract(min));

        // First and last rates for trend
        ExchangeRate first = rates.stream().min(Comparator.comparing(ExchangeRate::getDate)).orElse(null);
        ExchangeRate last = rates.stream().max(Comparator.comparing(ExchangeRate::getDate)).orElse(null);

        if (first != null && last != null) {
            BigDecimal change = last.getRate().subtract(first.getRate());
            BigDecimal changePct = first.getRate().compareTo(BigDecimal.ZERO) > 0
                ? change.divide(first.getRate(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            stats.put("periodChange", change);
            stats.put("periodChangePct", changePct);
        }

        return stats;
    }

    private Optional<ExchangeRate> findExactRate(UUID fromCurrencyId, UUID toCurrencyId, LocalDate date) {
        return exchangeRateRepository
            .findAll(
                (Specification<ExchangeRate>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("fromCcy").get("id"), fromCurrencyId),
                        cb.equal(root.get("toCcy").get("id"), toCurrencyId),
                        cb.equal(root.get("date"), date)
                    )
            )
            .stream()
            .findFirst();
    }

    private Optional<ExchangeRate> findMostRecentRate(UUID fromCurrencyId, UUID toCurrencyId, LocalDate beforeDate) {
        return exchangeRateRepository
            .findAll(
                (Specification<ExchangeRate>) (root, query, cb) -> {
                    if (query != null) {
                        query.orderBy(cb.desc(root.get("date")));
                    }
                    return cb.and(
                        cb.equal(root.get("fromCcy").get("id"), fromCurrencyId),
                        cb.equal(root.get("toCcy").get("id"), toCurrencyId),
                        cb.lessThanOrEqualTo(root.get("date"), beforeDate)
                    );
                }
            )
            .stream()
            .findFirst();
    }

    private ExchangeRateResponse toResponse(ExchangeRate rate) {
        Currency fromCurrency = rate.getFromCcy();
        Currency toCurrency = rate.getToCcy();

        BigDecimal inverseRate = rate.getRate().compareTo(BigDecimal.ZERO) > 0
            ? BigDecimal.ONE.divide(rate.getRate(), RATE_SCALE, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new ExchangeRateResponse(
            rate.getId(),
            fromCurrency.getId(),
            fromCurrency.getCode(),
            fromCurrency.getName(),
            toCurrency.getId(),
            toCurrency.getCode(),
            toCurrency.getName(),
            rate.getRate(),
            inverseRate,
            rate.getDate()
        );
    }
}
