package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A progress update on a goal, by the employee or their manager. Append-only: the goal's history.
 */
@Entity
@Table(name = "goal_check_in", indexes = @Index(name = "idx_goal_check_in_goal", columnList = "goal_id, checked_in_at"))
public class GoalCheckIn extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false, updatable = false)
    private Goal goal;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    private Employee author;

    @Column(name = "progress", nullable = false, updatable = false)
    private int progress;

    @Column(name = "comment", length = 2000, updatable = false)
    private String comment;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    public GoalCheckIn() {}

    public GoalCheckIn(Goal goal, Employee author, int progress, String comment, Instant checkedInAt) {
        this.goal = goal;
        this.author = author;
        this.progress = progress;
        this.comment = comment;
        this.checkedInAt = checkedInAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Goal getGoal() {
        return goal;
    }

    public Employee getAuthor() {
        return author;
    }

    public int getProgress() {
        return progress;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoalCheckIn that = (GoalCheckIn) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "GoalCheckIn{" + "id=" + id + ", progress=" + progress + '}';
    }
}
