package com.humano.service.payroll.template.model;

/**
 * How a template's results have been checked. Shown to tenants in the catalog.
 * <ul>
 *   <li>OFFICIAL_VECTORS — its test cases reproduce worked examples or calculator results published
 *   by the tax or social-security authority.</li>
 *   <li>LAW_DERIVED — every value is cited to the law, and the test cases were derived by hand from
 *   that law; no official worked example was available to compare against.</li>
 *   <li>DRAFT — incomplete or unchecked; not offered for adoption.</li>
 * </ul>
 */
public enum TemplateStatus {
    OFFICIAL_VECTORS,
    LAW_DERIVED,
    DRAFT,
}
