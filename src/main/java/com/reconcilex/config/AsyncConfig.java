package com.reconcilex.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated thread pool for asynchronous reconciliation execution.
 *
 * <p>On Render free tier (0.1 vCPU / 512 MB RAM) a single-threaded executor
 * prevents OOM when multiple runs are triggered concurrently — only one matching
 * engine runs at a time, queuing additional requests.
 *
 * <p>The {@code @Async("reconciliationExecutor")} qualifier in
 * {@link com.reconcilex.service.ReconciliationService} routes matching work here.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "reconciliationExecutor")
    public Executor reconciliationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1 core thread — prevents two 50K matching jobs running simultaneously on free tier
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        // Queue up to 10 waiting runs before rejecting
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("recon-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // Allow up to 5 minutes for an in-progress run to complete on shutdown
        executor.setAwaitTerminationSeconds(300);
        executor.initialize();
        return executor;
    }
}

