package com.humano.domain.enumeration.hr;

/**
 * The kind of a competency in the company's performance framework.
 */
public enum CompetencyCategory {
    /**
     * A company value everyone is assessed against (e.g. "Customer first").
     */
    VALUE,

    /**
     * A core competency expected of every role (e.g. "Communication").
     */
    CORE,

    /**
     * A competency expected of people who lead others (e.g. "Developing people").
     */
    LEADERSHIP,

    /**
     * A role or function-specific competency (e.g. "Financial analysis").
     */
    FUNCTIONAL,
}
