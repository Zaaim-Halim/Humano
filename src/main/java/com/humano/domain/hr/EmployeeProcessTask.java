package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a task within an employee onboarding or offboarding process.
 * <p>
 * Used to track the completion of individual steps in the employee lifecycle process.
 */
@Entity
@Table(name = "employee_process_task")
public class EmployeeProcessTask extends AbstractAuditingEntity<UUID> {
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
     * The process this task belongs to.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", nullable = false)
    private EmployeeProcess process;

    /**
     * Title of the task.
     */
    @NotNull
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Description of the task.
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Due date for task completion.
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Date when the task was completed.
     */
    @Column(name = "completion_date")
    private LocalDate completionDate;

    /**
     * Whether the task has been completed.
     */
    @NotNull
    @Column(name = "is_completed", nullable = false)
    private Boolean completed = false;

    /**
     * Person responsible for completing this task.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private Employee assignedTo;

    /**
     * Notes about task completion or issues.
     */
    @Column(name = "completion_notes", length = 1000)
    private String completionNotes;

    /**
     * Mark this task as completed.
     *
     * @param completionNotes Notes about the completion
     * @return This task
     */
    public EmployeeProcessTask complete(String completionNotes) {
        this.completed = true;
        this.completionDate = LocalDate.now();
        this.completionNotes = completionNotes;
        return this;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EmployeeProcess getProcess() {
        return process;
    }

    public EmployeeProcessTask process(EmployeeProcess process) {
        this.process = process;
        return this;
    }

    public void setProcess(EmployeeProcess process) {
        this.process = process;
    }

    public String getTitle() {
        return title;
    }

    public EmployeeProcessTask title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public EmployeeProcessTask description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public EmployeeProcessTask dueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public EmployeeProcessTask completionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public EmployeeProcessTask completed(Boolean completed) {
        this.completed = completed;
        return this;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Employee getAssignedTo() {
        return assignedTo;
    }

    public EmployeeProcessTask assignedTo(Employee assignedTo) {
        this.assignedTo = assignedTo;
        return this;
    }

    public void setAssignedTo(Employee assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getCompletionNotes() {
        return completionNotes;
    }

    public EmployeeProcessTask completionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
        return this;
    }

    public void setCompletionNotes(String completionNotes) {
        this.completionNotes = completionNotes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeProcessTask that = (EmployeeProcessTask) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeProcessTask{" +
            "id=" + id +
            ", title='" + title + '\'' +
            ", dueDate=" + dueDate +
            ", completed=" + completed +
            ", completionDate=" + completionDate +
            '}';
    }
}
