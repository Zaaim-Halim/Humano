package com.humano.service.hr;

import com.humano.domain.hr.EmploymentType;
import com.humano.repository.hr.EmploymentTypeRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link EmploymentType} reference data.
 */
@Service
public class EmploymentTypeService extends AbstractReferenceDataService<EmploymentType> {

    public EmploymentTypeService(EmploymentTypeRepository repository) {
        super(repository, EmploymentType::new, "EmploymentType");
    }
}
