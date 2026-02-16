package com.humano.domain.enumeration.hr;

/**
 * Represents the type of approver in an approval chain.
 */
public enum ApproverType {
    /**
     * Direct manager of the employee.
     */
    DIRECT_MANAGER,

    /**
     * Department head.
     */
    DEPARTMENT_HEAD,

    /**
     * HR representative.
     */
    HR,

    /**
     * Finance department.
     */
    FINANCE,

    /**
     * Executive level approver.
     */
    EXECUTIVE,

    /**
     * Specific employee designated as approver.
     */
    SPECIFIC_EMPLOYEE,
}
