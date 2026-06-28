package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Company asset assigned to an employee (e.g. laptop, phone, access card).
 */
@Entity
@Table(name = "employee_asset")
public class EmployeeAsset extends AbstractEmployeeOwnedEntity {

    /** Asset type, e.g. LAPTOP, PHONE, ACCESS_CARD, VEHICLE. */
    @Column(name = "type")
    private String type;

    /** Serial number / inventory tag. */
    @Column(name = "identifier")
    private String identifier;

    @Column(name = "assigned_date")
    private LocalDate assignedDate;

    @Column(name = "returned_date")
    private LocalDate returnedDate;

    public String getType() {
        return type;
    }

    public EmployeeAsset type(String type) {
        this.type = type;
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public EmployeeAsset identifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public EmployeeAsset assignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
        return this;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public LocalDate getReturnedDate() {
        return returnedDate;
    }

    public EmployeeAsset returnedDate(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
        return this;
    }

    public void setReturnedDate(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }

    @Override
    public String toString() {
        return "EmployeeAsset{id=" + getId() + ", type='" + type + "', identifier='" + identifier + "'}";
    }
}
