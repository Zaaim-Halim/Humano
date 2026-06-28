package com.humano.service.hr;

import com.humano.domain.hr.JobLevel;
import com.humano.repository.hr.JobLevelRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link JobLevel} reference data.
 */
@Service
public class JobLevelService extends AbstractReferenceDataService<JobLevel> {

    public JobLevelService(JobLevelRepository repository) {
        super(repository, JobLevel::new, "JobLevel");
    }
}
