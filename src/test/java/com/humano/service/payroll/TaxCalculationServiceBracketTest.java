package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.TaxBracketBasis;
import com.humano.domain.enumeration.payroll.TaxCode;
import com.humano.domain.payroll.TaxBracket;
import com.humano.domain.shared.Country;
import com.humano.dto.payroll.request.CreateTaxBracketRequest;
import com.humano.dto.payroll.response.TaxBracketResponse;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.TaxBracketRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link TaxCalculationService}'s bracket handling: annualisation of ANNUAL sets,
 * bracket-set shape validation, conflict checks on create, and the year-to-year copy closing the
 * source set.
 *
 * <p>The sample table is annual: 0–12,000 at 0%, 12,000–48,000 at 20%, 48,000+ at 40%. Tax on
 * 60,000 a year is 36,000 × 0.20 + 12,000 × 0.40 = 12,000.
 */
@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceBracketTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final String TOP = "999999999";

    @Mock
    private TaxBracketRepository taxBracketRepository;

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private TaxCalculationService service;

    private final Country country = country();

    private static Country country() {
        Country c = new Country();
        c.setId(UUID.randomUUID());
        c.setCode(CountryCode.GB);
        c.setName("United Kingdom");
        return c;
    }

    private TaxBracket bracket(String lower, String upper, String rate, TaxBracketBasis basis) {
        TaxBracket b = new TaxBracket();
        b.setId(UUID.randomUUID());
        b.setCountry(country);
        b.setTaxCode(TaxCode.PIT);
        b.setLower(new BigDecimal(lower));
        b.setUpper(new BigDecimal(upper));
        b.setRate(new BigDecimal(rate));
        b.setFixedPart(BigDecimal.ZERO);
        b.setBasis(basis);
        b.setValidFrom(FROM);
        return b;
    }

    private List<TaxBracket> annualTable() {
        return List.of(
            bracket("0", "12000", "0", TaxBracketBasis.ANNUAL),
            bracket("12000", "48000", "0.20", TaxBracketBasis.ANNUAL),
            bracket("48000", TOP, "0.40", TaxBracketBasis.ANNUAL)
        );
    }

    private CreateTaxBracketRequest createRequest(String lower, String upper, String rate, TaxBracketBasis basis) {
        return new CreateTaxBracketRequest(
            country.getId(),
            TaxCode.PIT,
            new BigDecimal(lower),
            new BigDecimal(upper),
            new BigDecimal(rate),
            null,
            basis,
            FROM,
            null
        );
    }

    // ---------------------------------------------------------------- annualisation

    @Test
    void annualSetGivesTheSameYearlyTaxForEveryPayFrequency() {
        BigDecimal monthly = service.calculatePeriodTax(new BigDecimal("5000"), annualTable(), Frequency.MONTHLY.periodsPerYear());
        BigDecimal semiMonthly = service.calculatePeriodTax(new BigDecimal("2500"), annualTable(), Frequency.SEMIMONTHLY.periodsPerYear());
        BigDecimal weekly = service.calculatePeriodTax(new BigDecimal("1153.85"), annualTable(), Frequency.WEEKLY.periodsPerYear());

        assertThat(monthly).isEqualByComparingTo("1000.00");
        assertThat(semiMonthly).isEqualByComparingTo("500.00");
        // 1,153.85 × 52 = 60,000.20 → annual tax 12,000.08 → 230.77 a week.
        assertThat(weekly).isEqualByComparingTo("230.77");
    }

    @Test
    void biweeklyEmployeeOnAnAnnualSetIsNoLongerUnderTaxed() {
        // 2,307.69 × 26 = 59,999.94 → annual tax 11,999.98 → 461.54 per period (≈ 12,000 a year;
        // before annualisation a biweekly employee on a monthly-converted table paid ≈ 6,800).
        BigDecimal biweekly = service.calculatePeriodTax(new BigDecimal("2307.69"), annualTable(), Frequency.BIWEEKLY.periodsPerYear());

        assertThat(biweekly).isEqualByComparingTo("461.54");
    }

    @Test
    void perPeriodSetIsAppliedAsIsSoExistingTenantsSeeNoChange() {
        List<TaxBracket> monthlyTable = List.of(
            bracket("0", "1000", "0", TaxBracketBasis.PER_PERIOD),
            bracket("1000", "4000", "0.20", TaxBracketBasis.PER_PERIOD),
            bracket("4000", TOP, "0.40", TaxBracketBasis.PER_PERIOD)
        );

        BigDecimal tax = service.calculatePeriodTax(new BigDecimal("5000"), monthlyTable, Frequency.BIWEEKLY.periodsPerYear());

        assertThat(tax).isEqualByComparingTo(service.calculateProgressiveTax(new BigDecimal("5000"), monthlyTable));
        assertThat(tax).isEqualByComparingTo("1000.00");
    }

    @Test
    void zeroOrMissingIncomeOnAnAnnualSetIsZeroTax() {
        assertThat(service.calculatePeriodTax(BigDecimal.ZERO, annualTable(), 12)).isEqualByComparingTo("0");
        assertThat(service.calculatePeriodTax(null, annualTable(), 12)).isEqualByComparingTo("0");
        assertThat(service.calculatePeriodTax(new BigDecimal("5000"), List.of(), 12)).isEqualByComparingTo("0");
    }

    @Test
    void frequenciesMapToTheirPeriodsPerYear() {
        assertThat(Frequency.MONTHLY.periodsPerYear()).isEqualTo(12);
        assertThat(Frequency.SEMIMONTHLY.periodsPerYear()).isEqualTo(24);
        assertThat(Frequency.BIWEEKLY.periodsPerYear()).isEqualTo(26);
        assertThat(Frequency.WEEKLY.periodsPerYear()).isEqualTo(52);
    }

    // ---------------------------------------------------------------- set validation

    @Test
    void wellFormedSetPassesValidation() {
        service.validateBracketSet(annualTable());
        service.validateBracketSet(List.of());
        service.validateBracketSet(null);
    }

    @Test
    void setThatSkipsTheZeroBandIsRejectedInsteadOfMisSliced() {
        List<TaxBracket> noZeroBand = List.of(
            bracket("12000", "48000", "0.20", TaxBracketBasis.ANNUAL),
            bracket("48000", TOP, "0.40", TaxBracketBasis.ANNUAL)
        );

        assertThatThrownBy(() -> service.calculatePeriodTax(new BigDecimal("20000"), noZeroBand, 1))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("must start at 0");
    }

    @Test
    void setWithAGapIsRejected() {
        List<TaxBracket> gap = List.of(
            bracket("0", "12000", "0", TaxBracketBasis.ANNUAL),
            bracket("20000", TOP, "0.20", TaxBracketBasis.ANNUAL)
        );

        assertThatThrownBy(() -> service.validateBracketSet(gap))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("gap between 12000 and 20000");
    }

    @Test
    void twoSetsActiveTogetherAreRejectedAsOverlapping() {
        List<TaxBracket> doubled = new ArrayList<>(annualTable());
        doubled.addAll(annualTable());

        assertThatThrownBy(() -> service.validateBracketSet(doubled))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("overlap");
    }

    @Test
    void setMixingAnnualAndPerPeriodThresholdsIsRejected() {
        List<TaxBracket> mixed = List.of(
            bracket("0", "12000", "0", TaxBracketBasis.ANNUAL),
            bracket("12000", TOP, "0.20", TaxBracketBasis.PER_PERIOD)
        );

        assertThatThrownBy(() -> service.validateBracketSet(mixed))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("mix ANNUAL and PER_PERIOD");
    }

    // ---------------------------------------------------------------- create

    @Test
    @SuppressWarnings("unchecked")
    void createDefaultsBasisToPerPeriodAndKeepsSixDecimalRates() {
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(taxBracketRepository.save(any(TaxBracket.class))).thenAnswer(inv -> inv.getArgument(0));

        TaxBracketResponse response = service.createTaxBracket(createRequest("0", "12000", "0.03078", null));

        assertThat(response.basis()).isEqualTo(TaxBracketBasis.PER_PERIOD);
        assertThat(response.rate()).isEqualByComparingTo("0.03078");
    }

    @Test
    void createRejectsRatesFinerThanSixDecimals() {
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));

        assertThatThrownBy(() -> service.createTaxBracket(createRequest("0", "12000", "0.0307812", TaxBracketBasis.ANNUAL)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("at most 6 decimals");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRejectsAnIncomeRangeOverlappingAnActiveBracket() {
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(annualTable());

        assertThatThrownBy(() -> service.createTaxBracket(createRequest("40000", "60000", "0.30", TaxBracketBasis.ANNUAL)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("overlaps existing bracket");
        verify(taxBracketRepository, never()).save(any(TaxBracket.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createRejectsABasisDifferentFromTheActiveSet() {
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(
            List.of(bracket("0", "12000", "0", TaxBracketBasis.ANNUAL))
        );

        assertThatThrownBy(() -> service.createTaxBracket(createRequest("12000", "48000", "0.20", TaxBracketBasis.PER_PERIOD)))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("can't mix ANNUAL and PER_PERIOD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createAcceptsABracketThatStartsWhereTheLastOneEnds() {
        when(countryRepository.findById(country.getId())).thenReturn(Optional.of(country));
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(
            List.of(bracket("0", "12000", "0", TaxBracketBasis.ANNUAL))
        );
        when(taxBracketRepository.save(any(TaxBracket.class))).thenAnswer(inv -> inv.getArgument(0));

        TaxBracketResponse response = service.createTaxBracket(createRequest("12000", "48000", "0.20", TaxBracketBasis.ANNUAL));

        assertThat(response.lower()).isEqualByComparingTo("12000");
    }

    // ---------------------------------------------------------------- copy to new year

    @Test
    @SuppressWarnings("unchecked")
    void copyClosesTheSourceSetAndCarriesTheBasis() {
        List<TaxBracket> source = annualTable();
        // 1st findAll: the set in force on Dec 31; 2nd: everything valid from the target year (only the sources).
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(source, source);
        when(taxBracketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TaxBracketResponse> copied = service.copyBracketsToNewYear(country.getId(), TaxCode.PIT, 2026, 2027, new BigDecimal("5"));

        assertThat(source).allSatisfy(b -> assertThat(b.getValidTo()).isEqualTo(LocalDate.of(2026, 12, 31)));
        assertThat(copied).hasSize(3);
        assertThat(copied).allSatisfy(b -> {
            assertThat(b.basis()).isEqualTo(TaxBracketBasis.ANNUAL);
            assertThat(b.validFrom()).isEqualTo(LocalDate.of(2027, 1, 1));
        });
        assertThat(copied.get(1).lower()).isEqualByComparingTo("12600.00");
        assertThat(copied.get(1).upper()).isEqualByComparingTo("50400.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void copyRefusesWhenTheTargetYearAlreadyHasBrackets() {
        List<TaxBracket> source = annualTable();
        TaxBracket existing2027 = bracket("0", "13000", "0", TaxBracketBasis.ANNUAL);
        existing2027.setValidFrom(LocalDate.of(2027, 1, 1));
        List<TaxBracket> fromTarget = new ArrayList<>(source);
        fromTarget.add(existing2027);
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(source, fromTarget);

        assertThatThrownBy(() -> service.copyBracketsToNewYear(country.getId(), TaxCode.PIT, 2026, 2027, null))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already exist from 2027-01-01");
        assertThat(source).allSatisfy(b -> assertThat(b.getValidTo()).isNull());
        verify(taxBracketRepository, never()).saveAll(any());
    }

    @Test
    void copyRejectsATargetYearThatIsNotAfterTheSource() {
        assertThatThrownBy(() -> service.copyBracketsToNewYear(country.getId(), TaxCode.PIT, 2026, 2026, null))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("after the source year");
    }

    @Test
    @SuppressWarnings("unchecked")
    void copiedSetIsWellFormedAndTaxesLikeTheSource() {
        List<TaxBracket> source = annualTable();
        when(taxBracketRepository.findAll(any(Specification.class))).thenReturn(source, source);
        when(taxBracketRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.copyBracketsToNewYear(country.getId(), TaxCode.PIT, 2026, 2027, null);

        ArgumentCaptor<List<TaxBracket>> saved = ArgumentCaptor.forClass(List.class);
        verify(taxBracketRepository, org.mockito.Mockito.times(2)).saveAll(saved.capture());
        List<TaxBracket> newSet = saved.getAllValues().get(1);
        // The new set on its own is well-formed and taxes 60,000 at 12,000, as the source did.
        assertThat(service.calculatePeriodTax(new BigDecimal("5000"), newSet, 12)).isEqualByComparingTo("1000.00");
    }
}
