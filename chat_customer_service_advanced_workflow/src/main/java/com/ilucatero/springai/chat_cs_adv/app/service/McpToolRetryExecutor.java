package com.ilucatero.springai.chat_cs_adv.app.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.resilience.annotation.Retryable;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Component
public class McpToolRetryExecutor {

    @Retryable(includes = {
            TimeoutException.class,
            SocketTimeoutException.class,
            ConnectException.class,
            HttpClientErrorException.TooManyRequests.class,
            HttpServerErrorException.BadGateway.class,
            HttpServerErrorException.ServiceUnavailable.class,
            HttpServerErrorException.GatewayTimeout.class
    }, maxRetries = 2, delay = 500, multiplier = 2)
    public <T> T execute(Supplier<T> action) {
        return action.get();
    }

}
