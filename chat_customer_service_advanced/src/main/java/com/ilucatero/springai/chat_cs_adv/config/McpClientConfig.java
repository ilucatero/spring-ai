package com.ilucatero.springai.chat_cs_adv.config;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class McpClientConfig {

    @Bean
    ToolCallbackProvider mcpTools(SyncMcpToolCallbackProvider delegate) {
        return () -> Arrays.stream(delegate.getToolCallbacks())
                .map(McpClientConfig::withLogging)
                .toArray(ToolCallback[]::new);
    }

    private static ToolCallback withLogging(ToolCallback delegate) {
        return new ToolCallbackWithLoggin(delegate);
    }

    /** Loggin Interceptor for ToolCallback */
    static class ToolCallbackWithLoggin implements ToolCallback {

        private static final Logger log = LoggerFactory.getLogger(ToolCallbackWithLoggin.class);

        private ToolCallback delegate;

        public ToolCallbackWithLoggin(ToolCallback delegate) {
            this.delegate = delegate;
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
            return logCall(delegate, input, null);
        }

        @Override
        public String call(String input, ToolContext context) {
            return logCall(delegate, input, context);
        }

        private String logCall(ToolCallback callback, String input, ToolContext context) {

            var name = callback.getToolDefinition().name();
            var start = System.nanoTime();

            log.info("MCP tool call: name={}, arguments={}", name, input);

            try {
                String result = context == null
                        ? callback.call(input)
                        : callback.call(input, context);

                long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                log.info("MCP tool completed: name={}, durationMs={}", name, elapsedMs);

                return result;

            } catch (RuntimeException e) {
                log.error("MCP tool failed: name={}", name, e);
                throw e;
            }
        }
    };
}
