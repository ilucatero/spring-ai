package com.ilucatero.springai.chat_cs_adv.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.client.McpSyncClient;

@Configuration(proxyBeanMethods = false)
class McpClientConfig {

    @Bean
    McpToolRegistry mcpToolRegistry(List<McpSyncClient> clients) {
        var mcpToolRegistry = new McpToolRegistry(clients);

        // initialize with client discovery
        mcpToolRegistry.getToolCallbacks();

        return mcpToolRegistry;
    }

}
