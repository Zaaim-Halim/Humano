package com.humano.service.payroll.template.model;

/** Who a template line falls on: withheld from the employee, paid by the employer, or tax withheld. */
public enum LineSide {
    EMPLOYEE,
    EMPLOYER,
    TAX,
}
