package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Country;
import com.humano.domain.enumeration.payroll.TaxCode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a tax bracket for a specific tax code and country.
 * <p>
 * - `lower`: Minimum income for this bracket.
 * - `upper`: Maximum income for this bracket.
 * - `rate`: Tax rate applied to the taxable amount above `lower`.
 * - `fixedPart`: Fixed tax amount from previous brackets.
 * - `taxCode`: Identifier for the tax type (e.g., PIT, VAT, Corporate Tax).
 * <p>
 * Each bracket is valid for a specific period and country.
 *
 * @author halimzaaim
 */
@Entity
@Table(name = "tax_bracket")
public class TaxBracket extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "lower", nullable = false, precision = 19, scale = 6)
    private BigDecimal lower;

    @Column(name = "upper", nullable = false, precision = 19, scale = 6)
    private BigDecimal upper;

    @Column(name = "rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal rate;

    @Column(name = "fixed_part", precision = 19, scale = 6)
    private BigDecimal fixedPart;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_code", nullable = false)
    private TaxCode taxCode;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
