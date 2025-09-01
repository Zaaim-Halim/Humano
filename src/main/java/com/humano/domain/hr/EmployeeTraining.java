package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.TrainingStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.springframework.data.jpa.domain.AbstractPersistable;

import java.time.LocalDate;
import java.util.Objects;
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
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public TrainingStatus getStatus() {
        return status;
    }

    public EmployeeTraining status(TrainingStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(TrainingStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public EmployeeTraining description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public EmployeeTraining completionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public String getFeedback() {
        return feedback;
    }

    public EmployeeTraining feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeTraining employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Training getTraining() {
        return training;
    }

    public EmployeeTraining training(Training training) {
        this.training = training;
        return this;
    }

    public void setTraining(Training training) {
        this.training = training;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeTraining that = (EmployeeTraining) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeTraining{" +
            "id=" + id +
            ", status=" + status +
            ", completionDate=" + completionDate +
            '}';
    }
}
