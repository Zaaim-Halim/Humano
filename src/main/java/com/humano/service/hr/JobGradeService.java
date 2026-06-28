package com.humano.service.hr;

import com.humano.domain.hr.JobGrade;
import com.humano.repository.hr.JobGradeRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link JobGrade} reference data.
 */
@Service
public class JobGradeService extends AbstractReferenceDataService<JobGrade> {

    public JobGradeService(JobGradeRepository repository) {
        super(repository, JobGrade::new, "JobGrade");
    }
}
