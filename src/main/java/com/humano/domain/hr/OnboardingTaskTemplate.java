package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.OnboardingTaskLink;
import com.humano.domain.enumeration.hr.OnboardingTaskOwner;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One task of the company onboarding checklist. Every new onboarding copies
 * these into its own {@link EmployeeProcessTask}s; editing the checklist never
 * changes onboardings already started.
 */
@Entity
@Table(name = "onboarding_task_template")
public class OnboardingTaskTemplate extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Days after the start date the task is due, on a default-length onboarding
     * ({@link OnboardingSettings#getDefaultDays()}); scaled for other lengths.
     */
    @Column(name = "due_day", nullable = false)
    private int dueDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner", nullable = false, length = 20)
    private OnboardingTaskOwner owner;

    /** Position in the checklist (0-based). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Employee record that shows the task is done; null when not linked. */
    @Enumerated(EnumType.STRING)
    @Column(name = "link", length = 30)
    private OnboardingTaskLink link;

    public OnboardingTaskLink getLink() {
        return link;
    }

    public void setLink(OnboardingTaskLink link) {
        this.link = link;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        this.dueDay = dueDay;
    }

    public OnboardingTaskOwner getOwner() {
        return owner;
    }

    public void setOwner(OnboardingTaskOwner owner) {
        this.owner = owner;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && Objects.equals(id, ((OnboardingTaskTemplate) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "OnboardingTaskTemplate{id=" + id + ", title='" + title + "', dueDay=" + dueDay + ", owner=" + owner + "}";
    }
}
