package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.TrainingStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_training")
public class EmployeeTraining extends AbstractPersistable<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status")
    private TrainingStatus status; // PLANNED, COMPLETED, CANCELLED

    @Column(name = "description")
    private String description;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "feedback")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}

