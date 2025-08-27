package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_event")
public class AttendanceEvent extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @Column(name = "event_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    @Column(name = "event_action", nullable = false)
    @Enumerated(EnumType.STRING)
    private EventAction eventAction; // CHECK_IN, CHECK_OUT, BREAK_START, BREAK_END, etc.

    @Column(name = "description")
    private String description;

    @Override
    public UUID getId() {
        return id;
    }

    // Getters and setters
}
