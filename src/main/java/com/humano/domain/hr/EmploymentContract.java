package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An employment contract for an employee. Employees may have several over time.
 */
@Entity
@Table(name = "employment_contract")
public class EmploymentContract extends AbstractEmployeeOwnedEntity {

    @Column(name = "contract_number")
    private String contractNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Contract type, e.g. PERMANENT, FIXED_TERM. */
    @Column(name = "contract_type")
    private String contractType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "working_hours", precision = 5, scale = 2)
    private BigDecimal workingHours;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** Contract status, e.g. DRAFT, ACTIVE, EXPIRED, TERMINATED. */
    @Column(name = "status")
    private String status;

    public String getContractNumber() {
        return contractNumber;
    }

    public EmploymentContract contractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
        return this;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public EmploymentContract startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public EmploymentContract endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getContractType() {
        return contractType;
    }

    public EmploymentContract contractType(String contractType) {
        this.contractType = contractType;
        return this;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public Position getPosition() {
        return position;
    }

    public EmploymentContract position(Position position) {
        this.position = position;
        return this;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Department getDepartment() {
        return department;
    }

    public EmploymentContract department(Department department) {
        this.department = department;
        return this;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public BigDecimal getWorkingHours() {
        return workingHours;
    }

    public EmploymentContract workingHours(BigDecimal workingHours) {
        this.workingHours = workingHours;
        return this;
    }

    public void setWorkingHours(BigDecimal workingHours) {
        this.workingHours = workingHours;
    }

    public LocalDate getSignedDate() {
        return signedDate;
    }

    public EmploymentContract signedDate(LocalDate signedDate) {
        this.signedDate = signedDate;
        return this;
    }

    public void setSignedDate(LocalDate signedDate) {
        this.signedDate = signedDate;
    }

    public String getStatus() {
        return status;
    }

    public EmploymentContract status(String status) {
        this.status = status;
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "EmploymentContract{id=" + getId() + ", contractNumber='" + contractNumber + "', status='" + status + "'}";
    }
}
