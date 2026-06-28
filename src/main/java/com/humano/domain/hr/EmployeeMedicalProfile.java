package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;

/**
 * Minimal medical information for an employee — store only what local regulations require.
 * One profile per employee (enforced by a unique constraint on {@code employee_id}).
 * <p>
 * High-sensitivity PII: encryption-at-rest / field-level access control are deferred (see docs §6);
 * fields carry a TODO.
 */
@Entity
@Table(name = "employee_medical_profile")
public class EmployeeMedicalProfile extends AbstractEmployeeOwnedEntity {

    /** Blood type, e.g. "O+". Sensitive — encryption deferred (TODO: §6). */
    @Column(name = "blood_type")
    private String bloodType;

    /** Known allergies. Sensitive — encryption deferred (TODO: §6). */
    @Column(name = "allergies", length = 2000)
    private String allergies;

    /** Free-text emergency medical notes. Sensitive — encryption deferred (TODO: §6). */
    @Column(name = "emergency_notes", length = 2000)
    private String emergencyNotes;

    public String getBloodType() {
        return bloodType;
    }

    public EmployeeMedicalProfile bloodType(String bloodType) {
        this.bloodType = bloodType;
        return this;
    }

    public void setBloodType(String bloodType) {
        this.bloodType = bloodType;
    }

    public String getAllergies() {
        return allergies;
    }

    public EmployeeMedicalProfile allergies(String allergies) {
        this.allergies = allergies;
        return this;
    }

    public void setAllergies(String allergies) {
        this.allergies = allergies;
    }

    public String getEmergencyNotes() {
        return emergencyNotes;
    }

    public EmployeeMedicalProfile emergencyNotes(String emergencyNotes) {
        this.emergencyNotes = emergencyNotes;
        return this;
    }

    public void setEmergencyNotes(String emergencyNotes) {
        this.emergencyNotes = emergencyNotes;
    }

    @Override
    public String toString() {
        return "EmployeeMedicalProfile{id=" + getId() + '}';
    }
}
