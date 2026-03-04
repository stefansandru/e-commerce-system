package com.commerce.order.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "outboxScheduler")
    public ThreadPoolTaskScheduler outboxScheduler() {
        // Dedicated thread pool for the outbox relay to prevent background tasks
        // from exhausting the default pool or competing with request threads
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("outbox-relay-");
        scheduler.initialize();
        return scheduler;
    }
}
