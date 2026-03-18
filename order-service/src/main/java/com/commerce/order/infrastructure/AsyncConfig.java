package com.commerce.order.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // Wire all @Scheduled methods to use this dedicated 2-thread pool
        // instead of Spring's default single-threaded scheduler.
        // This prevents the OutboxRelay from blocking other scheduled tasks
        // and prevents background threads from competing with request threads.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("outbox-relay-");
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }
}
