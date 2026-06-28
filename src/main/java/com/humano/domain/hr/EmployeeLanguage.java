package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;

/**
 * A language an employee speaks, with self-reported proficiency per skill.
 */
@Entity
@Table(name = "employee_language")
public class EmployeeLanguage extends AbstractEmployeeOwnedEntity {

    @Column(name = "language")
    private String language;

    /** Proficiency levels, e.g. BASIC, INTERMEDIATE, FLUENT, NATIVE. */
    @Column(name = "reading")
    private String reading;

    @Column(name = "writing")
    private String writing;

    @Column(name = "speaking")
    private String speaking;

    public String getLanguage() {
        return language;
    }

    public EmployeeLanguage language(String language) {
        this.language = language;
        return this;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getReading() {
        return reading;
    }

    public EmployeeLanguage reading(String reading) {
        this.reading = reading;
        return this;
    }

    public void setReading(String reading) {
        this.reading = reading;
    }

    public String getWriting() {
        return writing;
    }

    public EmployeeLanguage writing(String writing) {
        this.writing = writing;
        return this;
    }

    public void setWriting(String writing) {
        this.writing = writing;
    }

    public String getSpeaking() {
        return speaking;
    }

    public EmployeeLanguage speaking(String speaking) {
        this.speaking = speaking;
        return this;
    }

    public void setSpeaking(String speaking) {
        this.speaking = speaking;
    }

    @Override
    public String toString() {
        return "EmployeeLanguage{id=" + getId() + ", language='" + language + "'}";
    }
}
