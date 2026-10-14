package com.humano.dto.hr.responses;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One absence on the shared team calendar: who is off and when. Deliberately carries no reason,
 * comments or approver; {@code leaveType} is null unless the viewer may see it (their own entry,
 * or people who manage leave), so a colleague's sick leave shows only as "time off".
 */
public record LeaveCalendarEntry(
    UUID id,
    UUID employeeId,
    String employeeName,
    String imageUrl,
    String departmentName,
    LocalDate startDate,
    LocalDate endDate,
    Integer daysCount,
    LeaveStatus status,
    LeaveType leaveType
) {}
