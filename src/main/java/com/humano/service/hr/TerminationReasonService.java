package com.humano.service.hr;

import com.humano.domain.hr.TerminationReason;
import com.humano.repository.hr.TerminationReasonRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link TerminationReason} reference data.
 */
@Service
public class TerminationReasonService extends AbstractReferenceDataService<TerminationReason> {

    public TerminationReasonService(TerminationReasonRepository repository) {
        super(repository, TerminationReason::new, "TerminationReason");
    }
}
