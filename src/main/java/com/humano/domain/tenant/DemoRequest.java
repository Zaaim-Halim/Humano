package com.humano.domain.tenant;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A "Book a demo" request from the public site. The platform team works it from the console:
 * marks it contacted or declined, or sets up a demo workspace for it ({@link #tenant}).
 */
@Entity
@Table(name = "demo_request")
public class DemoRequest extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String fullName;

    @Column(name = "work_email", nullable = false, length = 254)
    @NotBlank
    @Email
    @Size(max = 254)
    private String workEmail;

    @Column(name = "company_name", nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String companyName;

    /** Headcount band as the visitor picked it, e.g. {@code 51-200}. */
    @Column(name = "company_size", length = 20)
    @Size(max = 20)
    private String companySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "country", length = 2)
    private CountryCode country;

    @Column(name = "phone", length = 30)
    @Size(max = 30)
    private String phone;

    @Column(name = "message", length = 2000)
    @Size(max = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    private DemoRequestStatus status = DemoRequestStatus.NEW;

    /** The platform team's internal notes. */
    @Column(name = "notes", length = 1000)
    @Size(max = 1000)
    private String notes;

    /** The demo workspace set up for this request, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public void setWorkEmail(String workEmail) {
        this.workEmail = workEmail;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public CountryCode getCountry() {
        return country;
    }

    public void setCountry(CountryCode country) {
        this.country = country;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DemoRequestStatus getStatus() {
        return status;
    }

    public void setStatus(DemoRequestStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof DemoRequest other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DemoRequest{id=" + id + ", companyName='" + companyName + "', status=" + status + "}";
    }
}
