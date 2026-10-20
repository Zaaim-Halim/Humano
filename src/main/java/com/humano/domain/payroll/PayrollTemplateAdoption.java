package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A tenant's adoption of a shipped statutory payroll template. Only the reference and the tenant's
 * own employer settings are stored — the template's rules and values are always read from the
 * shipped catalog, so updated templates reach every adopter.
 */
@Entity
@Table(name = "payroll_template_adoption")
public class PayrollTemplateAdoption extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_id", nullable = false, unique = true, length = 80)
    private String templateId;

    /** The template's jurisdiction when adopted (ISO 3166-1 alpha-2). */
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    /** The template's ISO 3166-2 subdivision when adopted; null for national templates. */
    @Column(name = "subdivision", length = 10)
    private String subdivision;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Employer settings the template declares, validated against it on save. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings")
    private Map<String, String> settings = new HashMap<>();

    /** The template version an admin last reviewed; a newer shipped version is flagged until acknowledged. */
    @Column(name = "acknowledged_version", length = 40)
    private String acknowledgedVersion;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getSubdivision() {
        return subdivision;
    }

    public void setSubdivision(String subdivision) {
        this.subdivision = subdivision;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getSettings() {
        return settings != null ? settings : Map.of();
    }

    public void setSettings(Map<String, String> settings) {
        this.settings = settings != null ? new HashMap<>(settings) : new HashMap<>();
    }

    public String getAcknowledgedVersion() {
        return acknowledgedVersion;
    }

    public void setAcknowledgedVersion(String acknowledgedVersion) {
        this.acknowledgedVersion = acknowledgedVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayrollTemplateAdoption that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PayrollTemplateAdoption{id=" + id + ", templateId=" + templateId + ", enabled=" + enabled + '}';
    }
}
