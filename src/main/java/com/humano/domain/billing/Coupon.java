package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.annotations.GenericGenerator;


import java.time.Instant;
import java.util.UUID;

@Entity
public class Coupon extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "discount", nullable = false)
    private Double discount;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Override
    public UUID getId() {
        return id;
    }

}

