package com.humano.service.billing.entitlement;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.dto.billing.responses.EntitlementsResponse;
import com.humano.dto.billing.responses.EntitlementsResponse.FeatureEntitlement;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.shared.UserRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The current workspace's entitlements with what it uses of the counted ones — for the plan screens
 * (usage meters, locked modules, the downgrade warning). Usage is counted the same way the limits are
 * enforced: people not terminated, active people holding an HR or payroll admin role, and GB stored.
 */
@Service
public class PlanUsageService {

    private final EntitlementService entitlementService;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StorageQuotaService storageQuotaService;

    public PlanUsageService(
        EntitlementService entitlementService,
        EmployeeRepository employeeRepository,
        UserRepository userRepository,
        StorageQuotaService storageQuotaService
    ) {
        this.entitlementService = entitlementService;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.storageQuotaService = storageQuotaService;
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public EntitlementsResponse current() {
        Entitlements e = entitlementService.current();
        List<FeatureEntitlement> features = e
            .catalog()
            .entrySet()
            .stream()
            .map(entry -> {
                String code = entry.getKey();
                Entitlements.Label label = entry.getValue();
                return new FeatureEntitlement(
                    code,
                    label.featureId(),
                    label.name(),
                    label.unit(),
                    e.includes(code),
                    e.includes(code) ? e.limit(code) : null,
                    used(code)
                );
            })
            .sorted(Comparator.comparing(FeatureEntitlement::code))
            .toList();
        return new EntitlementsResponse(e.planName(), features);
    }

    /** In the feature's unit: a count, or GB for storage (two decimals). */
    private Double used(String code) {
        return switch (code) {
            case FeatureCodes.EMPLOYEES -> (double) employeeRepository.countByStatusNot(EmployeeStatus.TERMINATED);
            case FeatureCodes.ADMIN_SEATS -> (double) userRepository.countActivatedHoldingAny(FeatureCodes.SEAT_ROLES);
            case FeatureCodes.STORAGE -> Math.round((storageQuotaService.usedBytes() / StorageQuotaService.BYTES_PER_GB) * 100) / 100d;
            default -> null;
        };
    }
}
