package com.humano.domain.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Persistable;

/**
 * @author halimzaaim
 */
@Entity
@Table(name = "permission")
@SuppressWarnings("common-java:DuplicatedBlocks")
@JsonIgnoreProperties(value = { "new", "id" })
public class Permission implements Serializable, Persistable<String> {

    @NotNull
    @Size(max = 100)
    @Id
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    private Set<Authority> authorities = new HashSet<>();

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public Permission name(String name) {
        this.setName(name);
        return this;
    }

    public Permission description(String description) {
        this.setDescription(description);
        return this;
    }

    public Permission authorities(Set<Authority> authorities) {
        this.setAuthorities(authorities);
        return this;
    }

    @org.springframework.data.annotation.Transient
    @Transient
    private boolean isPersisted;

    @PostLoad
    @PostPersist
    public void updateEntityState() {
        this.setIsPersisted();
    }

    @Override
    public String getId() {
        return this.name;
    }

    @org.springframework.data.annotation.Transient
    @Transient
    @Override
    public boolean isNew() {
        return !this.isPersisted;
    }

    public Permission setIsPersisted() {
        this.isPersisted = true;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Permission)) {
            return false;
        }
        return getName() != null && getName().equals(((Permission) o).getName());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getName());
    }

    @Override
    public String toString() {
        return "Permission{" + "name='" + name + '\'' + ", description='" + description + '\'' + ", authorities=" + authorities + '}';
    }
}
