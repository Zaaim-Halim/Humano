package com.humano.repository.payroll;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import per.hzaaim.empmanagement.payroll.domain.TaxBracket;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaxBracketRepository extends Repository<TaxBracket, UUID> {
    @Query("""
        SELECT t
        FROM TaxBracket t
        WHERE t.country.id = :countryId
          AND t.taxCode = :taxCode
          AND :date BETWEEN t.validFrom AND COALESCE(t.validTo, :date)
        ORDER BY t.lower ASC
    """)
    List<TaxBracket> findValidTaxBrackets(UUID countryId, String taxCode, LocalDate date);
}
