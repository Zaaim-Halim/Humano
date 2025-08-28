package com.humano.domain;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "currency")
public class Currency extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 3)
    private String code; // e.g., "EUR", "USD"

    @Column(name = "name", nullable = false)
    private String name; // e.g., "Euro", "US Dollar"

    @Column(name = "symbol", length = 5)
    private String symbol; // e.g., "€", "$"

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}

