package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Educational qualification of an employee. {@link #documentFileId} is a soft reference to a
 * {@code stored_file} row holding the scanned diploma/transcript.
 */
@Entity
@Table(name = "employee_education")
public class EmployeeEducation extends AbstractEmployeeOwnedEntity {

    @Column(name = "institution")
    private String institution;

    @Column(name = "degree")
    private String degree;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Column(name = "graduation_date")
    private LocalDate graduationDate;

    /** Soft reference to the backing {@code stored_file.id}. */
    @Column(name = "document_file_id")
    private UUID documentFileId;

    public String getInstitution() {
        return institution;
    }

    public EmployeeEducation institution(String institution) {
        this.institution = institution;
        return this;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getDegree() {
        return degree;
    }

    public EmployeeEducation degree(String degree) {
        this.degree = degree;
        return this;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public EmployeeEducation fieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
        return this;
    }

    public void setFieldOfStudy(String fieldOfStudy) {
        this.fieldOfStudy = fieldOfStudy;
    }

    public LocalDate getGraduationDate() {
        return graduationDate;
    }

    public EmployeeEducation graduationDate(LocalDate graduationDate) {
        this.graduationDate = graduationDate;
        return this;
    }

    public void setGraduationDate(LocalDate graduationDate) {
        this.graduationDate = graduationDate;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }

    public EmployeeEducation documentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
        return this;
    }

    public void setDocumentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
    }

    @Override
    public String toString() {
        return "EmployeeEducation{id=" + getId() + ", institution='" + institution + "', degree='" + degree + "'}";
    }
}
