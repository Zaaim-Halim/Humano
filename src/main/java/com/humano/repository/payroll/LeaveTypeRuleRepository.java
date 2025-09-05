package com.humano.repository.payroll;

import com.humano.domain.Country;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeaveTypeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author halimzaaim
 */
@Repository
public interface LeaveTypeRuleRepository extends JpaRepository<LeaveTypeRule, UUID>, JpaSpecificationExecutor<LeaveTypeRule> {
    Optional<LeaveTypeRule> findByLeaveTypeAndCountry(LeaveType leaveType, Country country);
}
