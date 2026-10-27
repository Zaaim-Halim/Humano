package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A competency or company value in the performance framework, with what each rating level looks
 * like. Review templates pick competencies; a cycle copies them when it opens, so editing or
 * retiring one never changes a running or closed cycle.
 */
@Entity
@Table(name = "competency")
public class Competency extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private CompetencyCategory category;

    @Embedded
    private RatingAnchors anchors = new RatingAnchors();

    /** A retired competency stays on the templates using it but can't be added to others. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public CompetencyCategory getCategory() {
        return category;
    }

    public void setCategory(CompetencyCategory category) {
        this.category = category;
    }

    public RatingAnchors getAnchors() {
        return anchors != null ? anchors : new RatingAnchors();
    }

    public void setAnchors(RatingAnchors anchors) {
        this.anchors = anchors;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Competency that = (Competency) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Competency{" + "id=" + id + ", name='" + name + '\'' + ", category=" + category + '}';
    }
}
