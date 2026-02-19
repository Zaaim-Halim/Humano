package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Represents a survey conducted within the organization, including title, description, date range, and responses.
 * <p>
 * Used to collect feedback or data from employees.
 */
@Entity
@Table(name = "survey")
public class Survey extends AbstractAuditingEntity<UUID> {

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
     * Title of the survey.
     */
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Optional description of the survey.
     */
    @Column(name = "description")
    private String description;

    /**
     * Start date of the survey.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * End date of the survey.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Responses submitted for this survey.
     */
    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SurveyResponse> responses = new HashSet<>();

    // Getters and setters
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public Survey title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public Survey description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Survey startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Survey endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Set<SurveyResponse> getResponses() {
        return responses;
    }

    public Survey responses(Set<SurveyResponse> responses) {
        this.responses = responses;
        return this;
    }

    public void setResponses(Set<SurveyResponse> responses) {
        this.responses = responses;
    }

    public Survey addResponse(SurveyResponse response) {
        this.responses.add(response);
        response.setSurvey(this);
        return this;
    }

    public Survey removeResponse(SurveyResponse response) {
        this.responses.remove(response);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Survey survey = (Survey) o;
        return Objects.equals(id, survey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "Survey{" +
            "id=" +
            id +
            ", title='" +
            title +
            '\'' +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", responseCount=" +
            (responses != null ? responses.size() : 0) +
            '}'
        );
    }
}
