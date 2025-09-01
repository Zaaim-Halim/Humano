package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.EmployeeProcessType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an onboarding or offboarding process for an employee.
 * <p>
 * Tracks the employee lifecycle stages and tasks to complete during hiring or departure.
 */
@Entity
@Table(name = "employee_process")
public class EmployeeProcess extends AbstractAuditingEntity<UUID> {
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
     * The employee this process applies to.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * Type of process (ONBOARDING or OFFBOARDING).
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false)
    private EmployeeProcessType processType;

    /**
     * Current status of the process.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmployeeProcessStatus status;

    /**
     * Start date of the process.
     */
    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Due date for process completion.
     */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /**
     * Actual completion date of the process.
     */
    @Column(name = "completion_date")
    private LocalDate completionDate;

    /**
     * Notes regarding the process.
     */
    @Column(name = "notes", length = 2000)
    private String notes;

    /**
     * Tasks to be completed as part of this process.
     */
    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeProcessTask> tasks = new HashSet<>();

    /**
     * Add a task to this process.
     *
     * @param title       Task title
     * @param description Task description
     * @param dueDate     Task due date
     * @return The created task
     */
    public EmployeeProcessTask addTask(String title, String description, LocalDate dueDate) {
        EmployeeProcessTask task = new EmployeeProcessTask();
        task.setProcess(this);
        task.setTitle(title);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setCompleted(false);
        this.tasks.add(task);
        return task;
    }

    /**
     * Remove a task from this process.
     *
     * @param task The task to remove
     */
    public void removeTask(EmployeeProcessTask task) {
        this.tasks.remove(task);
    }

    /**
     * Calculate the completion percentage of this process.
     *
     * @return Percentage of completed tasks (0-100)
     */
    @Transient
    public int getCompletionPercentage() {
        if (tasks.isEmpty()) {
            return 0;
        }

        long completedTasks = tasks.stream().filter(EmployeeProcessTask::getCompleted).count();
        return (int) ((completedTasks * 100) / tasks.size());
    }

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

    public EmployeeProcess employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public EmployeeProcessType getProcessType() {
        return processType;
    }

    public EmployeeProcess processType(EmployeeProcessType processType) {
        this.processType = processType;
        return this;
    }

    public void setProcessType(EmployeeProcessType processType) {
        this.processType = processType;
    }

    public EmployeeProcessStatus getStatus() {
        return status;
    }

    public EmployeeProcess status(EmployeeProcessStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(EmployeeProcessStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public EmployeeProcess startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public EmployeeProcess dueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public EmployeeProcess completionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
        return this;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
    }

    public String getNotes() {
        return notes;
    }

    public EmployeeProcess notes(String notes) {
        this.notes = notes;
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Set<EmployeeProcessTask> getTasks() {
        return tasks;
    }

    public EmployeeProcess tasks(Set<EmployeeProcessTask> tasks) {
        this.tasks = tasks;
        return this;
    }

    public void setTasks(Set<EmployeeProcessTask> tasks) {
        this.tasks = tasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeProcess that = (EmployeeProcess) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeProcess{" +
            "id=" + id +
            ", processType=" + processType +
            ", status=" + status +
            ", startDate=" + startDate +
            ", dueDate=" + dueDate +
            ", completionDate=" + completionDate +
            ", completionPercentage=" + getCompletionPercentage() +
            '}';
    }
}
