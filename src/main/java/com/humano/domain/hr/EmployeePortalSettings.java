package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * Represents portal settings and preferences for an employee, such as notification preferences and UI layout.
 * <p>
 * Stores settings for email/SMS notifications, dashboard layout, and theme.
 */
@Entity
@Table(name = "employee_portal_settings")
public class EmployeePortalSettings extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The employee these settings belong to.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /**
     * Whether email notifications are enabled.
     */
    @Column(name = "email_notifications")
    private Boolean emailNotifications;

    /**
     * Whether SMS notifications are enabled.
     */
    @Column(name = "sms_notifications")
    private Boolean smsNotifications;

    /**
     * Dashboard layout preference (e.g., GRID, LIST).
     */
    @Column(name = "dashboard_layout")
    private String dashboardLayout;

    /**
     * Theme preference (e.g., LIGHT, DARK).
     */
    @Column(name = "theme")
    private String theme;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
