package com.humano.config;

import com.humano.config.multitenancy.TenantAwareTaskDecorator;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.task.TaskExecutionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tech.jhipster.async.ExceptionHandlingAsyncTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
@Profile("!testdev & !testprod")
public class AsyncConfiguration implements AsyncConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfiguration.class);

    private final TaskExecutionProperties taskExecutionProperties;

    public AsyncConfiguration(TaskExecutionProperties taskExecutionProperties) {
        this.taskExecutionProperties = taskExecutionProperties;
    }

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        LOG.debug("Creating Async Task Executor");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
        executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
        executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
        executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
        // Propagate TenantContext + MDC into @Async workers so tenant-scoped
        // repositories keep routing correctly off the request thread.
        executor.setTaskDecorator(new TenantAwareTaskDecorator());
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * Dedicated scheduler for {@code @Scheduled} ticks. Without an explicit bean
     * Spring falls back to a default single-thread {@code ScheduledExecutorService}
     * that has no {@link TenantAwareTaskDecorator}, so any tenant repo call from
     * a tick would route to master/null. The decorator here covers child tasks
     * the tick spawns (e.g. via {@code TenantIteration}); the tick body itself
     * must still set the tenant explicitly since the scheduler has no submitter
     * context to inherit.
     */
    @Bean(name = "taskScheduler")
    public TaskScheduler taskScheduler() {
        LOG.debug("Creating tenant-aware Task Scheduler");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("humano-sched-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setTaskDecorator(new TenantAwareTaskDecorator());
        return scheduler;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
