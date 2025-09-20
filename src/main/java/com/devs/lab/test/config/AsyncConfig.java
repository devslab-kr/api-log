package com.devs.lab.test.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    @ConditionalOnProperty(value = "spring.threads.virtual.enabled", havingValue = "true")
    public TaskExecutor virtualThreadTaskExecutor() {
        return new VirtualThreadTaskExecutor("AsyncEvent-");
    }

    @Bean
    @ConditionalOnProperty(value = "spring.threads.virtual.enabled", havingValue = "false", matchIfMissing = true)
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AsyncEvent-");
        executor.initialize();
        return executor;
    }
}