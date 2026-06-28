package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import com.humano.domain.shared.Country;
import jakarta.persistence.*;

/**
 * Postal address belonging to an employee. An employee may have several (e.g. home, mailing),
 * distinguished by {@link #type} with at most one {@link #primary} address.
 */
@Entity
@Table(name = "address")
public class Address extends AbstractEmployeeOwnedEntity {

    /** Address kind, e.g. HOME, MAILING. */
    @Column(name = "type")
    private String type;

    @Column(name = "street")
    private String street;

    @Column(name = "building")
    private String building;

    @Column(name = "apartment")
    private String apartment;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(name = "is_primary", nullable = false)
    private Boolean primary = false;

    public String getType() {
        return type;
    }

    public Address type(String type) {
        this.type = type;
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStreet() {
        return street;
    }

    public Address street(String street) {
        this.street = street;
        return this;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getBuilding() {
        return building;
    }

    public Address building(String building) {
        this.building = building;
        return this;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getApartment() {
        return apartment;
    }

    public Address apartment(String apartment) {
        this.apartment = apartment;
        return this;
    }

    public void setApartment(String apartment) {
        this.apartment = apartment;
    }

    public String getCity() {
        return city;
    }

    public Address city(String city) {
        this.city = city;
        return this;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public Address state(String state) {
        this.state = state;
        return this;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public Address postalCode(String postalCode) {
        this.postalCode = postalCode;
        return this;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public Country getCountry() {
        return country;
    }

    public Address country(Country country) {
        this.country = country;
        return this;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public Address primary(Boolean primary) {
        this.primary = primary;
        return this;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    @Override
    public String toString() {
        return "Address{id=" + getId() + ", type='" + type + "', city='" + city + "', primary=" + primary + '}';
    }
}
