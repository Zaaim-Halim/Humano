package com.humano.domain.enumeration.payroll;

public enum TaxCode {
    PIT, // Personal Income Tax (national)
    STATE_PIT, // State / provincial income tax (progressive brackets, beyond national PIT)
    LOCAL_PIT, // Local / municipal income tax (progressive brackets, beyond national PIT)
    SSC, // Social Security Contribution
    VAT, // Value Added Tax
    MED, // Medicare Tax (if needed)
    CIT, // Corporate Income Tax
}
