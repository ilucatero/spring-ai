package com.ilucatero.springai.chat_cs_adv.app;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.client.McpSyncClient;

@Disabled("This test allows to manually verify the server exposed tools for each configured connection")
@AutoConfigureTestRestTemplate
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIT {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Test
    void shouldDiscoverMcpTools() {
        assertFalse(mcpSyncClients.isEmpty());

        System.out.println("===========\nMCP serve exposed tools:");
        mcpSyncClients
                .forEach(client -> client.listTools().tools().forEach(tool -> System.out.println("-" + tool.name())));
        System.out.println("===========");
    }
}
