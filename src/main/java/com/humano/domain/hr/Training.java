package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    public UUID getId() {
        return id;
    }

}
