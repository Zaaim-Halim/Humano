package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.TrainingStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a training record for an employee, including status, description, completion date, and feedback.
 * <p>
 * Links to both the employee and the training program.
 */
@Entity
@Table(name = "employee_training")
public class EmployeeTraining extends AbstractPersistable<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Status of the training (e.g., PLANNED, COMPLETED, CANCELLED).
     */
    @Column(name = "status")
    private TrainingStatus status;

    /**
     * Description of the training.
     */
    @Column(name = "description")
    private String description;

    /**
     * Date the training was completed.
     */
    @Column(name = "completion_date")
    private LocalDate completionDate;

    /**
     * Feedback provided for the training.
     */
    @Column(name = "feedback")
    private String feedback;

    /**
     * The employee who attended the training.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The training program attended.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
