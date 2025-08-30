package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an employee's daily attendance record, including check-in and check-out times, status, and related events.
 * <p>
 * Used to track presence, absence, and leave for HR management and reporting.
 */
@Entity
@Table(name = "attendance")
public class Attendance extends AbstractAuditingEntity<UUID> {
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
     * The employee associated with this attendance record.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The date of the attendance record.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * The time the employee checked in.
     */
    @Column(name = "check_in")
    private LocalTime checkIn;

    /**
     * The time the employee checked out.
     */
    @Column(name = "check_out")
    private LocalTime checkOut;

    /**
     * Attendance status (e.g., PRESENT, ABSENT, LEAVE).
     */
    @Column(name = "status", nullable = false)
    private String status;

    /**
     * Events related to this attendance record (e.g., breaks, check-ins).
     */
    @OneToMany(mappedBy = "attendance", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AttendanceEvent> events = new HashSet<>();

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
