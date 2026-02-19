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
 * Represents a training program offered to employees, including name, provider, schedule, and related records.
 * <p>
 * Used to manage employee development and training history.
 */
@Entity
@Table(name = "training")
public class Training extends AbstractAuditingEntity<UUID> {

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
     * Name of the training program.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Provider of the training program.
     */
    @Column(name = "provider")
    private String provider;

    /**
     * Start date of the training.
     */
    @Column(name = "start_date")
    private LocalDate startDate;

    /**
     * End date of the training.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Optional description of the training.
     */
    @Column(name = "description")
    private String description;

    /**
     * Location where the training is held.
     */
    @Column(name = "location")
    private String location;

    /**
     * Certificate awarded upon completion, if any.
     */
    @Column(name = "certificate")
    private String certificate;

    /**
     * Employee training records associated with this program.
     */
    @OneToMany(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeTraining> employeeTrainings = new HashSet<>();

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

    public Training name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public Training provider(String provider) {
        this.provider = provider;
        return this;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Training startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Training endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getDescription() {
        return description;
    }

    public Training description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public Training location(String location) {
        this.location = location;
        return this;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCertificate() {
        return certificate;
    }

    public Training certificate(String certificate) {
        this.certificate = certificate;
        return this;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public Set<EmployeeTraining> getEmployeeTrainings() {
        return employeeTrainings;
    }

    public Training employeeTrainings(Set<EmployeeTraining> employeeTrainings) {
        this.employeeTrainings = employeeTrainings;
        return this;
    }

    public void setEmployeeTrainings(Set<EmployeeTraining> employeeTrainings) {
        this.employeeTrainings = employeeTrainings;
    }

    public Training addEmployeeTraining(EmployeeTraining employeeTraining) {
        this.employeeTrainings.add(employeeTraining);
        employeeTraining.setTraining(this);
        return this;
    }

    public Training removeEmployeeTraining(EmployeeTraining employeeTraining) {
        this.employeeTrainings.remove(employeeTraining);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Training training = (Training) o;
        return Objects.equals(id, training.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "Training{" +
            "id=" +
            id +
            ", name='" +
            name +
            '\'' +
            ", provider='" +
            provider +
            '\'' +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", location='" +
            location +
            '\'' +
            '}'
        );
    }
}
