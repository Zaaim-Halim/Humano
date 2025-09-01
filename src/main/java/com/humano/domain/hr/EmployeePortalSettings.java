package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
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

    public EmployeePortalSettings employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Boolean getEmailNotifications() {
        return emailNotifications;
    }

    public EmployeePortalSettings emailNotifications(Boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
        return this;
    }

    public void setEmailNotifications(Boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
    }

    public Boolean getSmsNotifications() {
        return smsNotifications;
    }

    public EmployeePortalSettings smsNotifications(Boolean smsNotifications) {
        this.smsNotifications = smsNotifications;
        return this;
    }

    public void setSmsNotifications(Boolean smsNotifications) {
        this.smsNotifications = smsNotifications;
    }

    public String getDashboardLayout() {
        return dashboardLayout;
    }

    public EmployeePortalSettings dashboardLayout(String dashboardLayout) {
        this.dashboardLayout = dashboardLayout;
        return this;
    }

    public void setDashboardLayout(String dashboardLayout) {
        this.dashboardLayout = dashboardLayout;
    }

    public String getTheme() {
        return theme;
    }

    public EmployeePortalSettings theme(String theme) {
        this.theme = theme;
        return this;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    /**
     * Enable all notifications.
     *
     * @return This settings object
     */
    public EmployeePortalSettings enableAllNotifications() {
        this.emailNotifications = true;
        this.smsNotifications = true;
        return this;
    }

    /**
     * Disable all notifications.
     *
     * @return This settings object
     */
    public EmployeePortalSettings disableAllNotifications() {
        this.emailNotifications = false;
        this.smsNotifications = false;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeePortalSettings that = (EmployeePortalSettings) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeePortalSettings{" +
            "id=" + id +
            ", emailNotifications=" + emailNotifications +
            ", smsNotifications=" + smsNotifications +
            ", dashboardLayout='" + dashboardLayout + '\'' +
            ", theme='" + theme + '\'' +
            '}';
    }
}
