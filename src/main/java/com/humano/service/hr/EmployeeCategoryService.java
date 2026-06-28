package com.humano.service.hr;

import com.humano.domain.hr.EmployeeCategory;
import com.humano.repository.hr.EmployeeCategoryRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link EmployeeCategory} reference data.
 */
@Service
public class EmployeeCategoryService extends AbstractReferenceDataService<EmployeeCategory> {

    public EmployeeCategoryService(EmployeeCategoryRepository repository) {
        super(repository, EmployeeCategory::new, "EmployeeCategory");
    }
}
