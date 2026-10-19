package com.humano.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

@Configuration
public class AsyncSyncConfiguration {

    /**
     * Runs "async" work inline so tests stay deterministic. Exposed as an
     * {@link AsyncTaskExecutor} because beans such as {@code TenantIteration}
     * inject it by that type and call {@code submit}.
     */
    @Bean(name = "taskExecutor")
    public AsyncTaskExecutor taskExecutor() {
        return new TaskExecutorAdapter(new SyncTaskExecutor());
    }
}
