package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a custom attribute for an employee, stored as a key-value pair.
 * <p>
 * Used to extend employee records with additional, flexible information.
 */
@Entity
@Table(name = "employee_attribute")
public class EmployeeAttribute extends AbstractAuditingEntity<UUID> {

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
     * Key for the attribute (e.g., "language", "certification").
     */
    @Column(name = "attribute_key", nullable = false, length = 255)
    private String key;

    /**
     * Value for the attribute.
     */
    @Column(name = "attribute_value", nullable = false, length = 1000 )
    private String value;

    /**
     * The employee this attribute belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public EmployeeAttribute key(String key) {
        this.key = key;
        return this;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public EmployeeAttribute value(String value) {
        this.value = value;
        return this;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeAttribute employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeAttribute that = (EmployeeAttribute) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeAttribute{" +
            "id=" + id +
            ", key='" + key + '\'' +
            ", value='" + value + '\'' +
            '}';
    }
}
