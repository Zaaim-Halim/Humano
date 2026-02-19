package com.humano.repository.payroll;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.payroll.LeaveTypeRule;
import com.humano.domain.shared.Country;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * @author halimzaaim
 */
@Repository
public interface LeaveTypeRuleRepository extends JpaRepository<LeaveTypeRule, UUID>, JpaSpecificationExecutor<LeaveTypeRule> {
    Optional<LeaveTypeRule> findByLeaveTypeAndCountry(LeaveType leaveType, Country country);
}
