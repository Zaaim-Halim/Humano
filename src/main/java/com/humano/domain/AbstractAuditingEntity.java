package com.humano.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base abstract class for entities which will hold definitions for created, last modified, created by,
 * last modified by attributes.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(value = {"audit"}, allowGetters = true)
public abstract class AbstractAuditingEntity<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Embedded
    private AuditMetadata audit = new AuditMetadata();

    public abstract T getId();

    public AuditMetadata getAudit() {
        return audit;
    }

    public void setAudit(AuditMetadata audit) {
        this.audit = audit;
    }

    public String getCreatedBy() { return audit.getCreatedBy(); }
    public void setCreatedBy(String createdBy) { this.audit.setCreatedBy(createdBy); }

    public Instant getCreatedDate() { return audit.getCreatedDate(); }
    public void setCreatedDate(Instant createdDate) { audit.setCreatedDate(createdDate) ; }

    public String getLastModifiedBy() { return audit.getLastModifiedBy(); }
    public void setLastModifiedBy(String lastModifiedBy) { audit.setLastModifiedBy(lastModifiedBy); }

    public Instant getLastModifiedDate() { return audit.getLastModifiedDate(); }
    public void setLastModifiedDate(Instant lastModifiedDate) { audit.setLastModifiedDate(lastModifiedDate) ; }

}
