package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.AttendanceStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    /**
     * Events related to this attendance record (e.g., breaks, check-ins).
     */
    @OneToMany(mappedBy = "attendance", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<AttendanceEvent> events = new HashSet<>();

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

    public Attendance employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getDate() {
        return date;
    }

    public Attendance date(LocalDate date) {
        this.date = date;
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getCheckIn() {
        return checkIn;
    }

    public Attendance checkIn(LocalTime checkIn) {
        this.checkIn = checkIn;
        return this;
    }

    public void setCheckIn(LocalTime checkIn) {
        this.checkIn = checkIn;
    }

    public LocalTime getCheckOut() {
        return checkOut;
    }

    public Attendance checkOut(LocalTime checkOut) {
        this.checkOut = checkOut;
        return this;
    }

    public void setCheckOut(LocalTime checkOut) {
        this.checkOut = checkOut;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public Attendance status(AttendanceStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public Set<AttendanceEvent> getEvents() {
        return events;
    }

    public Attendance events(Set<AttendanceEvent> events) {
        this.events = events;
        return this;
    }

    public void setEvents(Set<AttendanceEvent> events) {
        this.events = events;
    }

    public Attendance addEvent(AttendanceEvent event) {
        this.events.add(event);
        event.setAttendance(this);
        return this;
    }

    public Attendance removeEvent(AttendanceEvent event) {
        this.events.remove(event);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attendance attendance = (Attendance) o;
        return Objects.equals(id, attendance.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Attendance{" +
            "id=" + id +
            ", date=" + date +
            ", checkIn=" + checkIn +
            ", checkOut=" + checkOut +
            ", status=" + status +
            '}';
    }
}
