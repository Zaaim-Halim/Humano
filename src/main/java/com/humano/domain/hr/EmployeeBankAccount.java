package com.humano.domain.hr;

import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;

/**
 * Bank account belonging to an employee. An employee may have several, with at most one
 * {@link #primary} account used for payroll.
 */
@Entity
@Table(name = "employee_bank_account")
public class EmployeeBankAccount extends AbstractEmployeeOwnedEntity {

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "iban")
    private String iban;

    @Column(name = "swift")
    private String swift;

    @Column(name = "account_holder")
    private String accountHolder;

    @Column(name = "currency", length = 3)
    @Enumerated(EnumType.STRING)
    private CurrencyCode currency;

    @Column(name = "is_primary", nullable = false)
    private Boolean primary = false;

    public String getBankName() {
        return bankName;
    }

    public EmployeeBankAccount bankName(String bankName) {
        this.bankName = bankName;
        return this;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIban() {
        return iban;
    }

    public EmployeeBankAccount iban(String iban) {
        this.iban = iban;
        return this;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getSwift() {
        return swift;
    }

    public EmployeeBankAccount swift(String swift) {
        this.swift = swift;
        return this;
    }

    public void setSwift(String swift) {
        this.swift = swift;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public EmployeeBankAccount accountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
        return this;
    }

    public void setAccountHolder(String accountHolder) {
        this.accountHolder = accountHolder;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public EmployeeBankAccount currency(CurrencyCode currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public EmployeeBankAccount primary(Boolean primary) {
        this.primary = primary;
        return this;
    }

    public void setPrimary(Boolean primary) {
        this.primary = primary;
    }

    @Override
    public String toString() {
        return "EmployeeBankAccount{id=" + getId() + ", bankName='" + bankName + "', primary=" + primary + '}';
    }
}
