package com.humano.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when an employee leaves — terminated after their last working day, or set to
 * terminated by HR. Lets work the employee was part of (their performance reviews, reviews they
 * were writing, feedback they were asked for) be wound up without coupling the employee lifecycle
 * to it. Listeners run after the leaving commits, so their failure can't undo it.
 *
 * <p>Like {@link TransferExecutedEvent}, the event carries its tenant: listeners set
 * {@code TenantContext} from {@code tenantSubdomain} for their work and restore it afterwards.
 *
 * @param tenantSubdomain subdomain key for the tenant routing datasource
 * @param employeeId      the employee who left
 * @param lastDay         their last working day, when known
 */
public record EmployeeLeftEvent(String tenantSubdomain, UUID employeeId, LocalDate lastDay) {}
