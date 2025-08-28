package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
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
    public UUID getId() {
        return id;
    }

}
