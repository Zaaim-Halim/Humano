package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDateTime;
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
    public UUID getId() {
        return id;
    }

}
