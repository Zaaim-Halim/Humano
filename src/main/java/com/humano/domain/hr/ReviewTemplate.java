package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * What a review asks beyond the standard form: the competencies the reviewer rates and the
 * questions the employee and the reviewer answer, in order. A cycle picks a template while it is a
 * draft and copies it when it opens ({@link ReviewCycleCompetency}, {@link ReviewCycleQuestion}).
 */
@Entity
@Table(name = "review_template")
public class ReviewTemplate extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /** A retired template stays on the cycles using it but can't be picked for new ones. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToMany
    @JoinTable(
        name = "review_template_competency",
        joinColumns = @JoinColumn(name = "template_id"),
        inverseJoinColumns = @JoinColumn(name = "competency_id")
    )
    @OrderColumn(name = "sort_order")
    private List<Competency> competencies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "review_template_question", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "sort_order")
    private List<ReviewQuestionDefinition> questions = new ArrayList<>();

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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Competency> getCompetencies() {
        return competencies;
    }

    public void setCompetencies(List<Competency> competencies) {
        this.competencies = competencies;
    }

    public List<ReviewQuestionDefinition> getQuestions() {
        return questions;
    }

    public void setQuestions(List<ReviewQuestionDefinition> questions) {
        this.questions = questions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewTemplate that = (ReviewTemplate) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewTemplate{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
