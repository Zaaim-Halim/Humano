package com.humano.repository.payroll;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.payroll.Currency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
    Optional<Currency> findByCode(CurrencyCode code);
}
