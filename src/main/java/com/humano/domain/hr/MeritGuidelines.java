package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The merit matrix — a <b>singleton per tenant schema</b>, created on the first save: for each final
 * rating 1–5, the range of base pay increase (in percent) the company suggests. Guidance only: a
 * proposal outside the range is flagged, never blocked, and ratings never change pay by themselves.
 */
@Entity
@Table(name = "merit_guidelines")
public class MeritGuidelines extends AbstractAuditingEntity<UUID> {

    /** The default ranges, rating 1 to 5, as {min, max} percent. */
    public static final BigDecimal[][] DEFAULTS = {
        { new BigDecimal("0"), new BigDecimal("0") },
        { new BigDecimal("0"), new BigDecimal("1") },
        { new BigDecimal("2"), new BigDecimal("3") },
        { new BigDecimal("3"), new BigDecimal("5") },
        { new BigDecimal("5"), new BigDecimal("8") },
    };

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rating_1_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating1Min = DEFAULTS[0][0];

    @Column(name = "rating_1_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating1Max = DEFAULTS[0][1];

    @Column(name = "rating_2_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating2Min = DEFAULTS[1][0];

    @Column(name = "rating_2_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating2Max = DEFAULTS[1][1];

    @Column(name = "rating_3_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating3Min = DEFAULTS[2][0];

    @Column(name = "rating_3_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating3Max = DEFAULTS[2][1];

    @Column(name = "rating_4_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating4Min = DEFAULTS[3][0];

    @Column(name = "rating_4_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating4Max = DEFAULTS[3][1];

    @Column(name = "rating_5_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating5Min = DEFAULTS[4][0];

    @Column(name = "rating_5_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal rating5Max = DEFAULTS[4][1];

    /** The suggested {min, max} increase in percent for a final rating 1–5. */
    public BigDecimal[] rangeFor(int rating) {
        return switch (rating) {
            case 1 -> new BigDecimal[] { rating1Min, rating1Max };
            case 2 -> new BigDecimal[] { rating2Min, rating2Max };
            case 3 -> new BigDecimal[] { rating3Min, rating3Max };
            case 4 -> new BigDecimal[] { rating4Min, rating4Max };
            case 5 -> new BigDecimal[] { rating5Min, rating5Max };
            default -> throw new IllegalArgumentException("Ratings run from 1 to 5");
        };
    }

    /** Sets the {min, max} increase in percent for a final rating 1–5. */
    public void setRange(int rating, BigDecimal min, BigDecimal max) {
        switch (rating) {
            case 1 -> {
                rating1Min = min;
                rating1Max = max;
            }
            case 2 -> {
                rating2Min = min;
                rating2Max = max;
            }
            case 3 -> {
                rating3Min = min;
                rating3Max = max;
            }
            case 4 -> {
                rating4Min = min;
                rating4Max = max;
            }
            case 5 -> {
                rating5Min = min;
                rating5Max = max;
            }
            default -> throw new IllegalArgumentException("Ratings run from 1 to 5");
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeritGuidelines that = (MeritGuidelines) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
