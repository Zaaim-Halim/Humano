package com.humano.service.hr;

import com.humano.domain.hr.MaritalStatus;
import com.humano.repository.hr.MaritalStatusRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link MaritalStatus} reference data.
 */
@Service
public class MaritalStatusService extends AbstractReferenceDataService<MaritalStatus> {

    public MaritalStatusService(MaritalStatusRepository repository) {
        super(repository, MaritalStatus::new, "MaritalStatus");
    }
}
