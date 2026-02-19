package com.humano.domain.shared;

import com.humano.domain.enumeration.CountryCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Country entity represents a geographical nation or territory.
 * <p>
 * This entity stores information about different countries where the organization operates,
 * including their ISO codes and names. It is used for regional settings, tax regulations,
 * localization, and country-specific business rules throughout the system.
 * <ul>
 *   <li><b>code</b>: The ISO 3166-1 alpha-2 country code (e.g., "US", "FR").</li>
 *   <li><b>name</b>: The full name of the country in English (e.g., "United States", "France").</li>
 * </ul>
 * <p>
 * Country is a foundational entity for international operations and is referenced by
 * various entities such as Employee, TaxBracket, and LeaveTypeRule to support country-specific
 * business logic and compliance requirements.
 */
@Entity
@Table(name = "country")
public class Country extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The ISO 3166-1 alpha-2 country code.
     * <p>
     * A two-letter code that uniquely identifies the country (e.g., "US", "FR").
     * This is the standard international identifier for the country.
     */
    @Column(name = "code", nullable = false, unique = true, length = 3)
    @NotNull(message = "Country code is required")
    @Enumerated(EnumType.STRING)
    private CountryCode code;

    /**
     * The full name of the country.
     * <p>
     * The official name of the country in English (e.g., "United States", "France").
     * Used for display and reporting purposes.
     */
    @Column(name = "name", nullable = false)
    @NotNull(message = "Country name is required")
    @Size(min = 2, max = 100, message = "Country name must be between 2 and 100 characters")
    private String name;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CountryCode getCode() {
        return code;
    }

    public Country code(CountryCode code) {
        this.code = code;
        return this;
    }

    public void setCode(CountryCode code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public Country name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Country country = (Country) o;
        return Objects.equals(id, country.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Country{" + "id=" + id + ", code='" + code + '\'' + ", name='" + name + '\'' + '}';
    }
}
