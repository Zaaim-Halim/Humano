package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Timesheets associated with this project.
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Timesheet> timesheets = new HashSet<>();

    // Getters and setters
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

    public Project name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public Project description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public Project startTime(LocalDateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public Project endTime(LocalDateTime endTime) {
        this.endTime = endTime;
        return this;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Set<Timesheet> getTimesheets() {
        return timesheets;
    }

    public Project timesheets(Set<Timesheet> timesheets) {
        this.timesheets = timesheets;
        return this;
    }

    public void setTimesheets(Set<Timesheet> timesheets) {
        this.timesheets = timesheets;
    }

    public Project addTimesheet(Timesheet timesheet) {
        this.timesheets.add(timesheet);
        timesheet.setProject(this);
        return this;
    }

    public Project removeTimesheet(Timesheet timesheet) {
        this.timesheets.remove(timesheet);
        timesheet.setProject(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Project{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            '}';
    }
}
