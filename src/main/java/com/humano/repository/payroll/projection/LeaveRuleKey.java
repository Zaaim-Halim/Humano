package com.humano.repository.payroll.projection;

import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.hr.LeaveType;

/** The country and leave type a workspace leave pay rule covers. */
public record LeaveRuleKey(CountryCode country, LeaveType leaveType) {}
