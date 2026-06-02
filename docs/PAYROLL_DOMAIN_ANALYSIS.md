# Payroll Domain Analysis & Workflow Documentation

## Table of Contents

1. [Overview](#overview)
2. [Domain Entity Analysis](#domain-entity-analysis)
3. [Entity Relationships Diagram](#entity-relationships-diagram)
4. [Payroll Processing Workflows](#payroll-processing-workflows)
5. [Proposed Manager Classes](#proposed-manager-classes)
6. [Service Layer Architecture](#service-layer-architecture)
7. [Implementation Recommendations](#implementation-recommendations)

---

## Overview

The `com.humano.domain.payroll` package contains **18 domain entities** that form a comprehensive payroll management system. The domain supports:

- **Multi-currency payroll processing**
- **Configurable pay components and rules**
- **Progressive tax calculations with brackets**
- **Benefits and deductions management**
- **Leave impact on payroll**
- **Complete payroll run lifecycle (Draft → Calculated → Approved → Posted)**
- **Payslip generation and storage**

---

## Domain Entity Analysis

### Core Payroll Entities

| Entity              | Purpose                                         | Key Relationships                                  |
| ------------------- | ----------------------------------------------- | -------------------------------------------------- |
| **PayrollCalendar** | Defines payroll schedules (frequency, timezone) | Parent of PayrollPeriod                            |
| **PayrollPeriod**   | Single payroll cycle with dates                 | Links to PayrollCalendar, referenced by PayrollRun |
| **PayrollRun**      | Execution of payroll processing                 | Links to PayrollPeriod, contains PayrollResults    |
| **PayrollResult**   | Aggregated payroll outcome per employee         | Links to PayrollRun, Employee, Currency            |
| **PayrollLine**     | Individual calculated payroll item              | Links to PayrollResult, PayComponent               |
| **Payslip**         | Final payroll document artifact                 | Links to PayrollResult                             |

### Configuration Entities

| Entity            | Purpose                               | Key Attributes                                                   |
| ----------------- | ------------------------------------- | ---------------------------------------------------------------- |
| **PayComponent**  | Defines earnings/deductions structure | code, kind (EARNING/DEDUCTION/EMPLOYER_CHARGE), measure, taxable |
| **PayRule**       | Calculation logic for components      | formula (SpEL), priority, effectiveFrom/To                       |
| **TaxBracket**    | Progressive tax rate definitions      | lower, upper, rate, fixedPart, country-specific                  |
| **LeaveTypeRule** | Leave impact on payroll               | deductionPercentage, affectsTaxableSalary, country-specific      |

### Employee-Specific Entities

| Entity              | Purpose                        | Key Attributes                                            |
| ------------------- | ------------------------------ | --------------------------------------------------------- |
| **Compensation**    | Base salary/wage arrangements  | baseAmount, basis (MONTHLY/ANNUAL/HOURLY), effectiveDates |
| **Bonus**           | Additional compensation awards | type, amount, awardDate, isPaid, isTaxable                |
| **Deduction**       | Amounts deducted from pay      | type, amount/percentage, isPreTax                         |
| **EmployeeBenefit** | Benefits with costs            | type, employerCost, employeeCost, status                  |
| **TaxWithholding**  | Tax-specific deductions        | type, rate, taxAuthority, yearToDateAmount                |

### Financial Entities

| Entity           | Purpose                   | Key Attributes                           |
| ---------------- | ------------------------- | ---------------------------------------- |
| **Currency**     | Monetary units            | code (ISO 4217), name, symbol            |
| **ExchangeRate** | Currency conversion rates | fromCcy, toCcy, date, rate               |
| **PayrollInput** | Variable payroll inputs   | quantity, rate, amount, source, metaJson |

---

## Entity Relationships Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PAYROLL DOMAIN MODEL                                │
└─────────────────────────────────────────────────────────────────────────────────┘

                         ┌──────────────────┐
                         │ PayrollCalendar  │
                         │  - name          │
                         │  - frequency     │
                         │  - timezone      │
                         └────────┬─────────┘
                                  │ 1:N
                                  ▼
                         ┌──────────────────┐
                         │  PayrollPeriod   │
                         │  - startDate     │
                         │  - endDate       │
                         │  - paymentDate   │
                         │  - closed        │
                         └────────┬─────────┘
                                  │ 1:N
                                  ▼
┌──────────────┐         ┌──────────────────┐         ┌──────────────┐
│   Employee   │◄────────│   PayrollRun     │────────►│  RunStatus   │
│   (shared)   │         │  - scope         │         │  - DRAFT     │
└──────────────┘         │  - status        │         │  - CALCULATED│
       │                 │  - approvedAt    │         │  - APPROVED  │
       │                 │  - hash          │         │  - POSTED    │
       │                 └────────┬─────────┘         └──────────────┘
       │                          │ 1:N
       │                          ▼
       │                 ┌──────────────────┐         ┌──────────────┐
       │                 │  PayrollResult   │────────►│   Currency   │
       └────────────────►│  - gross         │         └──────────────┘
                         │  - totalDeductions│                │
                         │  - net           │                 │
                         │  - employerCost  │                 ▼
                         └────────┬─────────┘         ┌──────────────┐
                                  │ 1:N               │ ExchangeRate │
                                  ▼                   └──────────────┘
                         ┌──────────────────┐
                         │   PayrollLine    │◄────────┌──────────────┐
                         │  - quantity      │         │ PayComponent │
                         │  - rate          │         │  - code      │
                         │  - amount        │         │  - kind      │
                         │  - explain       │         │  - measure   │
                         └────────┬─────────┘         │  - taxable   │
                                  │                   └──────┬───────┘
                                  │                          │ 1:N
                         ┌────────▼─────────┐         ┌──────▼───────┐
                         │     Payslip      │         │   PayRule    │
                         │  - number        │         │  - formula   │
                         │  - pdfUrl        │         │  - priority  │
                         └──────────────────┘         └──────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                           EMPLOYEE-LINKED ENTITIES                               │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐
    │   Employee   │
    └──────┬───────┘
           │
           ├───────────────┬───────────────┬───────────────┬───────────────┐
           │               │               │               │               │
           ▼               ▼               ▼               ▼               ▼
    ┌──────────────┐ ┌──────────┐ ┌───────────────┐ ┌──────────────┐ ┌─────────────┐
    │ Compensation │ │  Bonus   │ │   Deduction   │ │EmployeeBenefit│ │TaxWithholding│
    │  - baseAmount│ │  - type  │ │  - type       │ │  - type      │ │  - type     │
    │  - basis     │ │  - amount│ │  - amount/%   │ │  - employer$ │ │  - rate     │
    │  - currency  │ │  - isPaid│ │  - isPreTax   │ │  - employee$ │ │  - authority│
    └──────────────┘ └──────────┘ └───────────────┘ └──────────────┘ └─────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                         COUNTRY-SPECIFIC CONFIGURATION                           │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐
    │   Country    │
    └──────┬───────┘
           │
           ├───────────────────────────────┐
           │                               │
           ▼                               ▼
    ┌──────────────┐                ┌──────────────┐
    │  TaxBracket  │                │LeaveTypeRule │
    │  - lower     │                │  - leaveType │
    │  - upper     │                │  - deduction%│
    │  - rate      │                │  - affects   │
    │  - taxCode   │                │    TaxableSal│
    └──────────────┘                └──────────────┘
```

---

## Payroll Processing Workflows

### Workflow 1: End-to-End Payroll Processing

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        MAIN PAYROLL PROCESSING WORKFLOW                          │
└─────────────────────────────────────────────────────────────────────────────────┘

Phase 1: SETUP & INITIALIZATION
─────────────────────────────────
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ 1. Select   │────►│ 2. Validate │────►│ 3. Create   │
    │   Period    │     │   Period    │     │   PayrollRun│
    │             │     │   (not      │     │   (DRAFT)   │
    └─────────────┘     │   closed)   │     └──────┬──────┘
                        └─────────────┘            │
                                                   ▼
Phase 2: DATA COLLECTION
─────────────────────────────────
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ 4. Load     │────►│ 5. Collect  │────►│ 6. Collect  │
    │   Employee  │     │   Payroll   │     │   Attendance│
    │   Compensa- │     │   Inputs    │     │   & Leave   │
    │   tions     │     │   (OT, etc) │     │   Data      │
    └─────────────┘     └─────────────┘     └──────┬──────┘
                                                   │
                                                   ▼
Phase 3: CALCULATION
─────────────────────────────────
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ 7. Apply    │────►│ 8. Calculate│────►│ 9. Calculate│
    │   PayRules  │     │   Gross Pay │     │   Deductions│
    │   & Formulas│     │   (Earnings)│     │   & Taxes   │
    └─────────────┘     └─────────────┘     └──────┬──────┘
                                                   │
    ┌─────────────┐     ┌─────────────┐            │
    │ 11. Create  │◄────│ 10. Compute │◄───────────┘
    │   Payroll   │     │   Net Pay & │
    │   Lines     │     │   Employer  │
    │             │     │   Cost      │
    └──────┬──────┘     └─────────────┘
           │
           ▼
    ┌─────────────┐
    │ 12. Create  │
    │   Payroll   │
    │   Result    │
    │  (CALCULATED│
    └──────┬──────┘
           │
           ▼
Phase 4: APPROVAL & FINALIZATION
─────────────────────────────────
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ 13. Review  │────►│ 14. Approve │────►│ 15. Generate│
    │   & Validate│     │   PayrollRun│     │   Payslips  │
    │             │     │  (APPROVED) │     │   & PDFs    │
    └─────────────┘     └─────────────┘     └──────┬──────┘
                                                   │
                                                   ▼
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │ 18. Close   │◄────│ 17. Post to │◄────│ 16. Execute │
    │   Period    │     │   Accounting│     │   Payments  │
    │             │     │  (POSTED)   │     │             │
    └─────────────┘     └─────────────┘     └─────────────┘
```

### Workflow 2: Gross Pay Calculation Detail

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         GROSS PAY CALCULATION WORKFLOW                           │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │  Start Calculation │
    │  for Employee      │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 1. Get Active      │
    │    Compensation    │
    │    (base salary,   │
    │    basis, currency)│
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 2. Calculate Base  │     │  BASIC Component   │
    │    Salary for      │────►│  PayrollLine       │
    │    Period          │     │  (Kind: EARNING)   │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 3. Process Payroll │     │  OT Component      │
    │    Inputs          │────►│  PayrollLine       │
    │    (OT hours, etc) │     │  (Kind: EARNING)   │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 4. Apply Bonuses   │     │  BONUS Component   │
    │    (paid this      │────►│  PayrollLine       │
    │    period)         │     │  (Kind: EARNING)   │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 5. Apply Leave     │     │  Leave Adjustment  │
    │    Deductions      │────►│  PayrollLine       │
    │    (LeaveTypeRule) │     │  (negative amount) │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐
    │ 6. Sum All         │
    │    EARNING Lines   │
    │    = GROSS PAY     │
    └────────────────────┘
```

### Workflow 3: Deductions & Net Pay Calculation

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      DEDUCTIONS & NET PAY CALCULATION                            │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │  GROSS PAY         │
    │  (from previous)   │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 1. Apply Pre-Tax   │
    │    Deductions      │
    │    (Deduction      │
    │    isPreTax=true)  │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 2. Calculate       │
    │    TAXABLE INCOME  │
    │    (Gross - PreTax)│
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 3. Calculate       │     │  For each bracket: │
    │    Income Tax      │◄────│  tax = (amount in  │
    │    (TaxBracket)    │     │  bracket) × rate + │
    │                    │     │  fixedPart         │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐
    │ 4. Apply Employee  │
    │    TaxWithholdings │
    │    (Social Security│
    │    Medicare, etc)  │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 5. Apply Employee  │
    │    Benefits Cost   │
    │    (EmployeeBenefit│
    │    .employeeCost)  │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 6. Apply Post-Tax  │
    │    Deductions      │
    │    (Deduction      │
    │    isPreTax=false) │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 7. Calculate       │
    │    NET PAY         │
    │    (Gross - All    │
    │    Deductions)     │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 8. Calculate       │
    │    EMPLOYER COST   │
    │    (Gross +        │
    │    EmployerCharges │
    │    + Benefits)     │
    └────────────────────┘
```

### Workflow 4: PayRule Evaluation

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          PAYRULE EVALUATION WORKFLOW                             │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │  For PayComponent  │
    │  (e.g., OT, TAX)   │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 1. Load All Active │
    │    PayRules for    │
    │    Component       │
    │    (active=true,   │
    │    date in range)  │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 2. Filter by Scope │
    │    Applicability   │
    │    - Employee      │
    │    - Position      │
    │    - Unit          │
    │    - Country       │
    │    - Global        │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 3. Sort by         │
    │    Priority        │
    │    (higher wins)   │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 4. Select Highest  │
    │    Priority Rule   │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 5. Evaluate        │     │  Context Variables:│
    │    Formula         │◄────│  - basicSalary     │
    │    (SpEL Engine)   │     │  - overtime        │
    │                    │     │  - grossPay        │
    │                    │     │  - taxableIncome   │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐
    │ 6. Return          │
    │    Calculated      │
    │    Amount          │
    └────────────────────┘
```

### Workflow 5: Bonus Management

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           BONUS MANAGEMENT WORKFLOW                              │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │ 1. Create Bonus    │
    │    Request         │
    │    - employee      │
    │    - type          │
    │    - amount        │
    │    - awardDate     │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 2. Validate &      │
    │    Approve Bonus   │
    │    (based on type) │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 3. Save Bonus      │
    │    (isPaid=false)  │
    └─────────┬──────────┘
              │
              ▼
    ┌─────────────────────────────────────────────┐
    │          During Payroll Processing          │
    └─────────────────────────────────────────────┘
              │
              ▼
    ┌────────────────────┐
    │ 4. Query Unpaid    │
    │    Bonuses for     │
    │    Period          │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐     ┌────────────────────┐
    │ 5. Include in      │     │  If isTaxable:     │
    │    Gross Pay       │────►│  Add to taxable    │
    │    Calculation     │     │  income            │
    └─────────┬──────────┘     └────────────────────┘
              │
              ▼
    ┌────────────────────┐
    │ 6. Mark Bonus as   │
    │    Paid            │
    │    (isPaid=true,   │
    │    paymentDate)    │
    └────────────────────┘
```

### Workflow 6: Multi-Currency Processing

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        MULTI-CURRENCY PAYROLL WORKFLOW                           │
└─────────────────────────────────────────────────────────────────────────────────┘

    ┌────────────────────┐
    │ 1. Determine       │
    │    Employee's      │
    │    Compensation    │
    │    Currency        │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 2. Determine       │
    │    Target Payroll  │
    │    Currency        │
    │    (may be diff)   │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 3. Load Exchange   │
    │    Rate for        │
    │    Period Date     │
    │    (ExchangeRate)  │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 4. Convert All     │
    │    Amounts to      │
    │    Target Currency │
    └─────────┬──────────┘
              │
              ▼
    ┌────────────────────┐
    │ 5. Store Result    │
    │    with Currency   │
    │    Reference       │
    └────────────────────┘
```

---

## Proposed Manager Classes

Based on the domain analysis, here are the recommended manager/orchestrator classes:

### 1. PayrollProcessingManager

**Purpose:** Orchestrates the entire payroll processing lifecycle.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         PayrollProcessingManager                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Initiate payroll runs                                                       │
│   - Coordinate data collection                                                  │
│   - Manage workflow state transitions (DRAFT→CALCULATED→APPROVED→POSTED)        │
│   - Handle batch processing for multiple employees                              │
│   - Ensure idempotency using hash                                               │
│                                                                                 │
│ Key Methods:                                                                    │
│   + initiatePayrollRun(periodId, scope) → PayrollRun                           │
│   + calculatePayroll(runId) → List<PayrollResult>                              │
│   + approvePayrollRun(runId, approverId) → PayrollRun                          │
│   + postPayrollRun(runId) → PayrollRun                                         │
│   + rollbackPayrollRun(runId) → void                                           │
│   + getPayrollRunStatus(runId) → RunStatus                                     │
│                                                                                 │
│ Dependencies:                                                                   │
│   - PayrollCalculationManager                                                   │
│   - PayrollDataCollectionManager                                                │
│   - PayslipGenerationManager                                                    │
│   - PayrollPeriodRepository                                                     │
│   - PayrollRunRepository                                                        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2. PayrollCalculationManager

**Purpose:** Handles all payroll calculation logic.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         PayrollCalculationManager                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Calculate gross pay from compensation, inputs, and bonuses                  │
│   - Apply pay rules and formulas (SpEL evaluation)                              │
│   - Calculate deductions (pre-tax and post-tax)                                 │
│   - Calculate taxes using tax brackets                                          │
│   - Compute net pay and employer cost                                           │
│   - Generate payroll lines with explanations                                    │
│                                                                                 │
│ Key Methods:                                                                    │
│   + calculateForEmployee(employee, period, run) → PayrollResult                │
│   + calculateGrossPay(employee, period) → BigDecimal                           │
│   + calculateDeductions(employee, grossPay, period) → BigDecimal               │
│   + calculateTax(taxableIncome, country, date) → BigDecimal                    │
│   + calculateNetPay(gross, deductions) → BigDecimal                            │
│   + calculateEmployerCost(gross, benefits, charges) → BigDecimal               │
│   + evaluatePayRule(rule, context) → BigDecimal                                │
│                                                                                 │
│ Dependencies:                                                                   │
│   - PayRuleEvaluationEngine                                                     │
│   - TaxCalculationManager                                                       │
│   - CompensationRepository                                                      │
│   - PayComponentRepository                                                      │
│   - PayRuleRepository                                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3. TaxCalculationManager

**Purpose:** Manages all tax-related calculations.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           TaxCalculationManager                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Progressive tax calculation using tax brackets                              │
│   - Apply tax withholdings (income, social security, etc.)                      │
│   - Track year-to-date tax amounts                                              │
│   - Handle country-specific tax rules                                           │
│   - Support multiple tax codes (PIT, VAT, etc.)                                 │
│                                                                                 │
│ Key Methods:                                                                    │
│   + calculateIncomeTax(taxableIncome, country, date) → BigDecimal              │
│   + getApplicableTaxBrackets(country, taxCode, date) → List<TaxBracket>        │
│   + applyTaxWithholdings(employee, grossPay) → List<TaxWithholdingResult>      │
│   + updateYearToDateAmount(employee, taxType, amount) → void                   │
│   + calculateProgressiveTax(income, brackets) → BigDecimal                     │
│                                                                                 │
│ Dependencies:                                                                   │
│   - TaxBracketRepository                                                        │
│   - TaxWithholdingRepository                                                    │
│   - CountryRepository                                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4. CompensationManager

**Purpose:** Manages employee compensation configurations.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CompensationManager                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Manage compensation records (create, update, history)                       │
│   - Find active compensation for payroll period                                 │
│   - Convert compensation basis (hourly→monthly, annual→monthly)                 │
│   - Handle salary changes and effective dates                                   │
│   - Multi-currency support                                                      │
│                                                                                 │
│ Key Methods:                                                                    │
│   + getActiveCompensation(employee, date) → Compensation                       │
│   + createCompensation(employee, dto) → Compensation                           │
│   + updateCompensation(id, dto) → Compensation                                 │
│   + terminateCompensation(id, effectiveDate) → void                            │
│   + getCompensationHistory(employee) → List<Compensation>                      │
│   + convertToMonthlyAmount(compensation) → BigDecimal                          │
│                                                                                 │
│ Dependencies:                                                                   │
│   - CompensationRepository                                                      │
│   - CurrencyManager                                                             │
│   - PositionRepository                                                          │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5. BonusManager

**Purpose:** Handles bonus lifecycle and processing.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               BonusManager                                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Create and approve bonuses                                                  │
│   - Track bonus payment status                                                  │
│   - Query unpaid bonuses for payroll period                                     │
│   - Mark bonuses as paid after payroll processing                               │
│   - Handle different bonus types and their rules                                │
│                                                                                 │
│ Key Methods:                                                                    │
│   + createBonus(employee, dto) → Bonus                                         │
│   + approveBonus(bonusId) → Bonus                                              │
│   + getUnpaidBonuses(employee, period) → List<Bonus>                           │
│   + markAsPaid(bonusId, paymentDate) → void                                    │
│   + getBonusesByType(employee, type) → List<Bonus>                             │
│   + getTotalBonusesForPeriod(employee, period) → BigDecimal                    │
│                                                                                 │
│ Dependencies:                                                                   │
│   - BonusRepository                                                             │
│   - CurrencyManager                                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6. DeductionManager

**Purpose:** Manages employee deductions.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             DeductionManager                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Manage deduction records                                                    │
│   - Query active deductions for payroll period                                  │
│   - Calculate deduction amounts (fixed or percentage-based)                     │
│   - Separate pre-tax and post-tax deductions                                    │
│   - Handle deduction effective dates                                            │
│                                                                                 │
│ Key Methods:                                                                    │
│   + getActiveDeductions(employee, date) → List<Deduction>                      │
│   + getPreTaxDeductions(employee, date) → List<Deduction>                      │
│   + getPostTaxDeductions(employee, date) → List<Deduction>                     │
│   + calculateDeductionAmount(deduction, basePay) → BigDecimal                  │
│   + createDeduction(employee, dto) → Deduction                                 │
│   + terminateDeduction(id, effectiveDate) → void                               │
│                                                                                 │
│ Dependencies:                                                                   │
│   - DeductionRepository                                                         │
│   - CurrencyManager                                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 7. BenefitManager

**Purpose:** Manages employee benefits and their costs.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              BenefitManager                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Manage employee benefit enrollments                                         │
│   - Track benefit costs (employer and employee portions)                        │
│   - Handle benefit status transitions                                           │
│   - Query active benefits for payroll                                           │
│   - Calculate total benefit deductions and employer charges                     │
│                                                                                 │
│ Key Methods:                                                                    │
│   + enrollEmployeeInBenefit(employee, dto) → EmployeeBenefit                   │
│   + getActiveBenefits(employee, date) → List<EmployeeBenefit>                  │
│   + calculateEmployeeBenefitCost(employee, period) → BigDecimal                │
│   + calculateEmployerBenefitCost(employee, period) → BigDecimal                │
│   + updateBenefitStatus(benefitId, status) → EmployeeBenefit                   │
│   + terminateBenefit(benefitId, effectiveDate) → void                          │
│                                                                                 │
│ Dependencies:                                                                   │
│   - EmployeeBenefitRepository                                                   │
│   - CurrencyManager                                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 8. PayrollCalendarManager

**Purpose:** Manages payroll calendars and periods.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          PayrollCalendarManager                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Create and manage payroll calendars                                         │
│   - Generate payroll periods based on calendar frequency                        │
│   - Handle period open/close operations                                         │
│   - Validate period dates and sequences                                         │
│   - Timezone-aware date calculations                                            │
│                                                                                 │
│ Key Methods:                                                                    │
│   + createCalendar(dto) → PayrollCalendar                                      │
│   + generatePeriods(calendarId, year) → List<PayrollPeriod>                    │
│   + getActivePeriod(calendarId, date) → PayrollPeriod                          │
│   + closePeriod(periodId) → PayrollPeriod                                      │
│   + reopenPeriod(periodId) → PayrollPeriod                                     │
│   + getUpcomingPaymentDates(calendarId) → List<LocalDate>                      │
│                                                                                 │
│ Dependencies:                                                                   │
│   - PayrollCalendarRepository                                                   │
│   - PayrollPeriodRepository                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 9. PayRuleManager

**Purpose:** Manages pay component rules and formula evaluation.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PayRuleManager                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Manage pay components and their rules                                       │
│   - Evaluate SpEL formulas for calculations                                     │
│   - Resolve rule conflicts using priority                                       │
│   - Handle rule effective dates                                                 │
│   - Validate formula syntax and dependencies                                    │
│                                                                                 │
│ Key Methods:                                                                    │
│   + getApplicableRule(component, context, date) → PayRule                      │
│   + evaluateFormula(formula, context) → BigDecimal                             │
│   + createPayRule(componentId, dto) → PayRule                                  │
│   + updatePayRule(ruleId, dto) → PayRule                                       │
│   + validateFormula(formula) → ValidationResult                                │
│   + getComponentRules(componentId) → List<PayRule>                             │
│                                                                                 │
│ Dependencies:                                                                   │
│   - PayComponentRepository                                                      │
│   - PayRuleRepository                                                           │
│   - SpEL ExpressionParser                                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 10. PayslipManager

**Purpose:** Handles payslip generation and distribution.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              PayslipManager                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Generate payslips from payroll results                                      │
│   - Create PDF documents                                                        │
│   - Manage payslip numbering                                                    │
│   - Store and retrieve payslip artifacts                                        │
│   - Employee self-service access                                                │
│                                                                                 │
│ Key Methods:                                                                    │
│   + generatePayslip(resultId) → Payslip                                        │
│   + generatePayslipPDF(payslipId) → byte[]                                     │
│   + getEmployeePayslips(employee, year) → List<Payslip>                        │
│   + getPayslipByNumber(number) → Payslip                                       │
│   + generateBatchPayslips(runId) → List<Payslip>                               │
│   + sendPayslipNotification(payslipId) → void                                  │
│                                                                                 │
│ Dependencies:                                                                   │
│   - PayslipRepository                                                           │
│   - PayrollResultRepository                                                     │
│   - PDFGeneratorService                                                         │
│   - NotificationService                                                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 11. CurrencyManager

**Purpose:** Manages currencies and exchange rate conversions.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              CurrencyManager                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Manage currency definitions                                                 │
│   - Handle exchange rate lookups                                                │
│   - Perform currency conversions                                                │
│   - Load/update exchange rates                                                  │
│   - Support historical rate queries                                             │
│                                                                                 │
│ Key Methods:                                                                    │
│   + convert(amount, fromCurrency, toCurrency, date) → BigDecimal               │
│   + getExchangeRate(fromCurrency, toCurrency, date) → ExchangeRate             │
│   + updateExchangeRate(fromCurrency, toCurrency, rate, date) → ExchangeRate    │
│   + loadExchangeRates(date) → void                                             │
│   + getCurrency(code) → Currency                                               │
│                                                                                 │
│ Dependencies:                                                                   │
│   - CurrencyRepository                                                          │
│   - ExchangeRateRepository                                                      │
│   - ExternalExchangeRateService (optional)                                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 12. LeaveImpactManager

**Purpose:** Handles leave impact on payroll calculations.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            LeaveImpactManager                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Responsibilities:                                                                │
│   - Calculate leave-related salary deductions                                   │
│   - Apply country-specific leave rules                                          │
│   - Determine taxable salary impact                                             │
│   - Query leave data for payroll period                                         │
│   - Handle different leave types                                                │
│                                                                                 │
│ Key Methods:                                                                    │
│   + calculateLeaveDeduction(employee, period) → BigDecimal                     │
│   + getLeaveRule(leaveType, country) → LeaveTypeRule                           │
│   + applyLeaveRule(leaveDays, dailyRate, rule) → BigDecimal                    │
│   + affectsTaxableSalary(leaveType, country) → boolean                         │
│   + getEmployeeLeaves(employee, period) → List<Leave>                          │
│                                                                                 │
│ Dependencies:                                                                   │
│   - LeaveTypeRuleRepository                                                     │
│   - LeaveRepository (from HR domain)                                            │
│   - CountryRepository                                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Service Layer Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           SERVICE LAYER ARCHITECTURE                             │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API / CONTROLLER LAYER                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│  PayrollController  │  BonusController  │  CompensationController  │  etc.      │
└────────────┬────────────────────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           ORCHESTRATION / MANAGER LAYER                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │ PayrollProcessingManager│────►│ PayrollCalculationManager│                   │
│  │       (Orchestrator)    │     └────────────┬────────────┘                   │
│  └────────────┬────────────┘                  │                                 │
│               │                               │                                 │
│               ▼                               ▼                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │ PayrollCalendarManager  │     │   TaxCalculationManager │                   │
│  └─────────────────────────┘     └─────────────────────────┘                   │
│                                                                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │   CompensationManager   │     │      BonusManager       │                   │
│  └─────────────────────────┘     └─────────────────────────┘                   │
│                                                                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │    DeductionManager     │     │     BenefitManager      │                   │
│  └─────────────────────────┘     └─────────────────────────┘                   │
│                                                                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │     PayRuleManager      │     │     PayslipManager      │                   │
│  └─────────────────────────┘     └─────────────────────────┘                   │
│                                                                                 │
│  ┌─────────────────────────┐     ┌─────────────────────────┐                   │
│  │    CurrencyManager      │     │   LeaveImpactManager    │                   │
│  └─────────────────────────┘     └─────────────────────────┘                   │
│                                                                                 │
└────────────────────────────────────┬────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              REPOSITORY LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│  PayrollRunRepository      │  PayrollResultRepository  │  PayrollLineRepository │
│  PayrollPeriodRepository   │  PayrollCalendarRepository│  PayslipRepository     │
│  CompensationRepository    │  BonusRepository          │  DeductionRepository   │
│  EmployeeBenefitRepository │  TaxBracketRepository     │  TaxWithholdingRepo    │
│  PayComponentRepository    │  PayRuleRepository        │  PayrollInputRepository│
│  CurrencyRepository        │  ExchangeRateRepository   │  LeaveTypeRuleRepo     │
└─────────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DOMAIN / ENTITY LAYER                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                        com.humano.domain.payroll.*                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Recommendations

### 1. Transaction Management

- Use `@Transactional` at manager level for write operations
- Consider saga pattern for distributed transactions across domains
- Implement compensating transactions for rollback scenarios

### 2. Calculation Order

Ensure payroll calculations follow this order:

1. Base compensation (BASIC)
2. Earnings additions (OT, BONUS, ALLOWANCE)
3. Leave deductions
4. Pre-tax deductions
5. Tax calculations (using TaxBracket)
6. Post-tax deductions
7. Net pay calculation
8. Employer cost calculation

### 3. Audit Trail

- Leverage `AbstractAuditingEntity` for all modifications
- Store calculation explanations in `PayrollLine.explain`
- Maintain version history for compensation changes

### 4. Idempotency

- Use `PayrollRun.hash` to prevent duplicate processing
- Generate hash from: periodId + scope + timestamp
- Check hash before processing

### 5. Performance Considerations

- Batch process employees in chunks (e.g., 100 at a time)
- Use async processing for large payroll runs
- Cache tax brackets and pay rules during calculation

### 6. Error Handling

- Implement partial failure handling (some employees fail, others succeed)
- Store calculation errors per employee
- Allow reprocessing of failed employees only

### 7. Integration Points

- HR Domain: Employee, Leave, Position
- Accounting Domain: Post payroll results to GL
- Banking Domain: Payment file generation
- Notification Domain: Payslip distribution

---

## Summary

The payroll domain is well-structured with clear separation of:

- **Configuration entities** (PayComponent, PayRule, TaxBracket, LeaveTypeRule)
- **Transaction entities** (PayrollRun, PayrollResult, PayrollLine, Payslip)
- **Employee-specific entities** (Compensation, Bonus, Deduction, EmployeeBenefit, TaxWithholding)
- **Supporting entities** (Currency, ExchangeRate, PayrollCalendar, PayrollPeriod, PayrollInput)

The proposed manager classes provide a clean abstraction layer for:

- Orchestrating complex payroll workflows
- Encapsulating business logic
- Ensuring consistent processing across the application
- Supporting extensibility for future requirements

---

_Document generated: February 2026_
_Domain package: `com.humano.domain.payroll`_
