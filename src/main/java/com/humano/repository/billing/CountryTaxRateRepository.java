package com.humano.repository.billing;

import com.humano.domain.billing.CountryTaxRate;
import com.humano.domain.enumeration.CountryCode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Master-DB repository for {@link CountryTaxRate}. Read path is one query per invoice
 * issuance via {@link #findActive}.
 */
@Repository
public interface CountryTaxRateRepository extends JpaRepository<CountryTaxRate, UUID>, JpaSpecificationExecutor<CountryTaxRate> {
    /**
     * Returns the rate active for {@code country} on {@code asOfDate}, picking the row
     * with the highest {@code validFrom} that is &le; {@code asOfDate} AND whose
     * {@code validTo} is either {@code null} or &ge; {@code asOfDate}. The unique
     * constraint on {@code (country_code, valid_from)} guarantees the result is unique
     * for any well-formed row set.
     */
    @Query(
        "SELECT r FROM CountryTaxRate r " +
        "WHERE r.countryCode = :country " +
        "AND r.validFrom <= :asOfDate " +
        "AND (r.validTo IS NULL OR r.validTo >= :asOfDate) " +
        "ORDER BY r.validFrom DESC"
    )
    java.util.List<CountryTaxRate> findActiveCandidates(CountryCode country, LocalDate asOfDate);

    default Optional<CountryTaxRate> findActive(CountryCode country, LocalDate asOfDate) {
        return findActiveCandidates(country, asOfDate).stream().findFirst();
    }
}
