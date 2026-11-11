package com.ilucatero.springai.chat_cs_adv.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkflowConfig {

    @Bean
    public ThreadPoolTaskExecutor workflowExecutor() {

        // Create a ThreadPoolTaskExecutor with a fixed thread pool size and a queue
        // capacity

        // Note : With this configuration, threads are shared with HTTP request. No
        // threads are reserved for the workflow.

        var executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("workflow-");

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy());

        executor.initialize();

        return executor;
    }
}
