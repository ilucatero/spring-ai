package com.ilucatero.springai.chat_cs_adv.config;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.HttpStatusCodeException;

import io.modelcontextprotocol.client.McpSyncClient;

public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private static final List<Integer> RETRIYABLE_HTTP_ERRORS = List.of(429, 502, 503, 504);

    private final List<McpSyncClient> clients;

    /** Cache discovered MCPTools of clients to avoir initialize it on each call. */
    private final Map<McpSyncClient, ToolCallback[]> cachedCallbacks = new ConcurrentHashMap<>();

    public McpToolRegistry(List<McpSyncClient> clients) {
        this.clients = clients;
    }

    public ToolCallback[] getToolCallbacks() {
        return clients.stream()
                .map(client -> {
                    try {
                        return cachedCallbacks.computeIfAbsent(client, this::discoverCallbacks);
                    } catch (RuntimeException e) {
                        log.warn("MCP client unavailable; will retry later");
                        return new ToolCallback[] {};
                    }
                })
                .flatMap(Arrays::stream)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback[] discoverCallbacks(McpSyncClient client) {
        var tools = initializeClient(client)
                .stream()
                .map(callback -> withLogging(client, callback))
                .toArray(ToolCallback[]::new);

        log.info("MCP Tools discovered {} items for client '{}'", tools.length, client.getClientInfo().name());
        return tools;
    }

    private List<ToolCallback> initializeClient(McpSyncClient client) {
        return executeWithRetry(() -> {
            if (client.getCurrentInitializationResult() == null) {
                client.initialize();
            }

            return SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
        },
                McpToolRegistry::isRetryable);
    }

    private ToolCallback withLogging(McpSyncClient client, ToolCallback delegate) {
        return new ToolCallbackWithLogging(
                delegate,
                () -> invalidate(client));
    }

    private void invalidate(McpSyncClient client) {
        cachedCallbacks.remove(client);
        log.warn("MCP callbacks invalidated; client will be rediscovered");
    }

    private static <T> T executeWithRetry(Supplier<T> action, Predicate<Throwable> isRetryable) {
        var retryCount = 0;
        while (true) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (!isRetryable.test(e) || retryCount >= 2) {
                    throw e;
                }
                retryCount++;

                long delay = 500L * retryCount;

                // logs cause only on last attempt
                if (retryCount < 2) {
                    log.warn("Retrying MCP operation: attempt={}, delayMs={}", retryCount + 1, delay);
                } else {
                    log.warn("Retrying MCP operation: attempt={}, delayMs={}", retryCount + 1, delay, e);
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("MCP retry interrupted", interrupted);
                }

            }
        }
    }

    private static boolean isRetryable(Throwable exception) {
        // read the cause chain to determine if the error is retryable. For example, if
        // the exception is a network timeout or a service unavailable error
        Throwable cause = exception;
        while (cause != null) {
            log.info("Checking exception type: {}", cause.getClass().getName());
            if (cause instanceof TimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || (cause instanceof HttpStatusCodeException httpException
                            && RETRIYABLE_HTTP_ERRORS.contains(httpException.getStatusCode().value()))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /** Loggin Interceptor for ToolCallback */
    private static class ToolCallbackWithLogging implements ToolCallback {

        private static final Logger log = LoggerFactory.getLogger(ToolCallbackWithLogging.class);

        private final ToolCallback delegate;
        private final Runnable invalidateCache;

        ToolCallbackWithLogging(ToolCallback delegate, Runnable invalidateCache) {
            this.delegate = delegate;
            this.invalidateCache = invalidateCache;
        }

        @Override
        public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String input) {
            return execute(() -> delegate.call(input));
        }

        @Override
        public String call(String input, ToolContext context) {
            return execute(() -> delegate.call(input, context));
        }

        private String execute(Supplier<String> action) {
            var name = delegate.getToolDefinition().name();
            var start = System.nanoTime();

            log.debug("MCP tool call: name={}", name);

            try {
                var result = executeWithRetry(action, McpToolRegistry::isRetryable);

                var elapsedMs = (System.nanoTime() - start) / 1_000_000;
                log.info("MCP tool completed: name={}, durationMs={}", name, elapsedMs);

                return result;

            } catch (RuntimeException e) {
                log.error("MCP tool failed: name={}. Client's cache will be invalidated.", name, e);

                invalidateCache.run();

                throw e;
            }
        }

    };
}
