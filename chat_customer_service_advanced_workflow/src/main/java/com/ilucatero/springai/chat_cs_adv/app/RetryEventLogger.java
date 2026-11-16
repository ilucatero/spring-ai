package com.ilucatero.springai.chat_cs_adv.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import org.springframework.resilience.retry.MethodRetryEvent;
import org.springframework.stereotype.Component;

import com.ilucatero.springai.chat_cs_adv.app.service.McpToolRetryExecutor;

@Component
public class RetryEventLogger {
    private static final Logger log = LoggerFactory.getLogger(McpToolRetryExecutor.class);

    @EventListener
    public void onRetryEvent(MethodRetryEvent event) {

        var method = event.getMethod().getName();
        var failure = event.getFailure();

        if (event.isRetryAborted()) {
            // log message only on last retry
            log.error("Retry exhausted or aborted: method={}, exception={}, message={}",
                    method,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
        } else {
            log.warn("Retry attempt failed: method={}, exception={}", method, failure.getClass().getSimpleName());
        }
    }
}
