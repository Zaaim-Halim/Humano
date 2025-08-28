package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a project within the organization, including name, description, and time frame.
 * <p>
 * Used to track and manage company projects and their schedules.
 */
@Entity
@Table(name = "project")
public class Project extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the project.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Optional description of the project.
     */
    @Column(name = "description")
    private String description;

    /**
     * Start time of the project.
     */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /**
     * End time of the project.
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
