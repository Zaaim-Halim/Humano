package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @author halimzaaim
 *
 **/

@Entity
@Table(name = "exchange_rate")
public class ExchangeRate extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "from_currency_id", nullable = false)
    private Currency fromCcy;

    @ManyToOne
    @JoinColumn(name = "to_currency_id", nullable = false)
    private Currency toCcy;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal rate; // mid or fix

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
