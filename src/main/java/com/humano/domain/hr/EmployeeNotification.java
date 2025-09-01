package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a notification sent to an employee, such as alerts, reminders, or messages.
 * <p>
 * Stores the message, read status, timestamp, and the related employee.
 */
@Entity
@Table(name = "employee_notification")
public class EmployeeNotification extends AbstractAuditingEntity<UUID> {

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
     * The employee who receives the notification.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The notification message.
     */
    @Column(name = "message", nullable = false)
    private String message;

    /**
     * Whether the notification has been read.
     */
    @Column(name = "read", nullable = false)
    private Boolean read;

    /**
     * Timestamp when the notification was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Getters and setters
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeNotification employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getMessage() {
        return message;
    }

    public EmployeeNotification message(String message) {
        this.message = message;
        return this;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getRead() {
        return read;
    }

    public EmployeeNotification read(Boolean read) {
        this.read = read;
        return this;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public EmployeeNotification createdAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Mark the notification as read.
     *
     * @return This notification
     */
    public EmployeeNotification markAsRead() {
        this.read = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeNotification that = (EmployeeNotification) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeNotification{" +
            "id=" + id +
            ", message='" + (message != null && message.length() > 30
                           ? message.substring(0, 27) + "..."
                           : message) + '\'' +
            ", read=" + read +
            ", createdAt=" + createdAt +
            '}';
    }
}
