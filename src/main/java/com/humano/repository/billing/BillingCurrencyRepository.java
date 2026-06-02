package com.humano.repository.billing;

import com.humano.domain.billing.BillingCurrency;
import com.humano.domain.enumeration.CurrencyCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingCurrencyRepository extends JpaRepository<BillingCurrency, UUID> {
    Optional<BillingCurrency> findByCode(CurrencyCode code);
}
