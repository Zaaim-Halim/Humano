package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an event within an employee's attendance record, such as check-in, check-out, or breaks.
 * <p>
 * Each event is linked to an Attendance record and contains details about the type, time, and action.
 */
@Entity
@Table(name = "attendance_event")
public class AttendanceEvent extends AbstractAuditingEntity<UUID> {
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
     * The attendance record this event belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    /**
     * Type of event (e.g., CHECK_IN, BREAK_START).
     */
    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    /**
     * Time the event occurred.
     */
    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    /**
     * Action performed during the event (e.g., CHECK_IN, BREAK_END).
     */
    @Column(name = "event_action", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventAction eventAction;

    /**
     * Optional description of the event.
     */
    @Column(name = "description")
    private String description;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Attendance getAttendance() {
        return attendance;
    }

    public AttendanceEvent attendance(Attendance attendance) {
        this.attendance = attendance;
        return this;
    }

    public void setAttendance(Attendance attendance) {
        this.attendance = attendance;
    }

    public EventType getEventType() {
        return eventType;
    }

    public AttendanceEvent eventType(EventType eventType) {
        this.eventType = eventType;
        return this;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public LocalTime getEventTime() {
        return eventTime;
    }

    public AttendanceEvent eventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
        return this;
    }

    public void setEventTime(LocalTime eventTime) {
        this.eventTime = eventTime;
    }

    public EventAction getEventAction() {
        return eventAction;
    }

    public AttendanceEvent eventAction(EventAction eventAction) {
        this.eventAction = eventAction;
        return this;
    }

    public void setEventAction(EventAction eventAction) {
        this.eventAction = eventAction;
    }

    public String getDescription() {
        return description;
    }

    public AttendanceEvent description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttendanceEvent that = (AttendanceEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AttendanceEvent{" +
            "id=" + id +
            ", eventType=" + eventType +
            ", eventTime=" + eventTime +
            ", eventAction=" + eventAction +
            ", description='" + description + '\'' +
            '}';
    }
}
