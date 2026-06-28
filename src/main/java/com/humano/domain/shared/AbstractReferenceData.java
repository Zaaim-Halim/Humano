package com.humano.domain.shared;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base class for tenant-configurable reference (lookup) data — small lists that ship with seeded
 * defaults but that tenants may extend (e.g. employment types, job grades, marital statuses).
 * <p>
 * Each row carries a stable, unique {@code code} used by application logic, a human-readable
 * {@code name} for display, and an {@code active} flag so values can be retired without deleting
 * historical references.
 */
@MappedSuperclass
public abstract class AbstractReferenceData extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Stable, unique identifier used by application logic (e.g. "FULL_TIME").
     */
    @NotNull
    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /**
     * Human-readable label for display (e.g. "Full time").
     */
    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Whether this value is selectable; retired values are kept for historical references.
     */
    @NotNull
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * Optional free-text notes / description for this value.
     */
    @Column(name = "notes", length = 2000)
    private String notes;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractReferenceData that = (AbstractReferenceData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", code='" + code + "', name='" + name + "', active=" + active + '}';
    }
}
