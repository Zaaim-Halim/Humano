package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalTime;
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

    // Getters and setters
}
