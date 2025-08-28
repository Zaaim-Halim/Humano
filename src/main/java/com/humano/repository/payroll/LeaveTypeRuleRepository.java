package com.humano.repository.payroll;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeaveTypeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author halimzaaim
 */
@Repository
public interface LeaveTypeRuleRepository extends JpaRepository<LeaveTypeRule, UUID> {
    Optional<LeaveTypeRule> findByLeaveTypeAndCountry(LeaveType leaveType, per.hzaaim.empmanagement.core.domain.Country country);
}
