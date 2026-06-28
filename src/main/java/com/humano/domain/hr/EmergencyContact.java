package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;

/**
 * Emergency contact for an employee. An employee may have several.
 */
@Entity
@Table(name = "emergency_contact")
public class EmergencyContact extends AbstractEmployeeOwnedEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "relationship")
    private String relationship;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    public String getName() {
        return name;
    }

    public EmergencyContact name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationship() {
        return relationship;
    }

    public EmergencyContact relationship(String relationship) {
        this.relationship = relationship;
        return this;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getPhone() {
        return phone;
    }

    public EmergencyContact phone(String phone) {
        this.phone = phone;
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public EmergencyContact email(String email) {
        this.email = email;
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "EmergencyContact{id=" + getId() + ", name='" + name + "', relationship='" + relationship + "'}";
    }
}
