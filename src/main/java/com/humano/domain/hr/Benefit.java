package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

@Entity
@Table(name = "benefit")
public class Benefit extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name; // e.g., Health Insurance, Transport Allowance

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @ManyToMany
    @JoinTable(
        name = "employee_benefit",
        joinColumns = @JoinColumn(name = "benefit_id"),
        inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    private java.util.Set<Employee> employees = new HashSet<>();

    // Getters and setters

    @Override
    public UUID getId() {
        return id;
    }
}
