package com.ilucatero.springai.chat_cs_adv_mcp.app;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/** Test to validate that exposed MCPTools are available */
@AutoConfigureTestRestTemplate
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerIT {

    private static final String MCP_PATH = "/mcp";

    @Autowired
    private TestRestTemplate http;

    @Test
    void shouldExposeAndExecuteReturnPolicyTool() {
        // 1. Initialiser la session MCP
        ResponseEntity<String> init = post(
                """
                        {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "method": "initialize",
                        "params": {
                        "protocolVersion": "2025-06-18",
                        "capabilities": {},
                        "clientInfo": {
                        "name": "integration-test",
                        "version": "1.0"
                        }
                        }
                        }
                        """,
                null);

        assertTrue(init.getStatusCode().is2xxSuccessful(), "L'initialisation MCP doit réussir");

        String sessionId = init.getHeaders().getFirst("Mcp-Session-Id");

        assertNotNull(sessionId, "Le serveur doit fournir un identifiant de session");

        // 2. Finaliser l'initialisation
        ResponseEntity<String> initialized = post(
                """
                        {
                        "jsonrpc": "2.0",
                        "method": "notifications/initialized"
                        }
                        """,
                sessionId);

        assertTrue(initialized.getStatusCode().is2xxSuccessful());

        // 3. Vérifier que l'outil est publié
        ResponseEntity<String> tools = post(
                """
                        {
                        "jsonrpc": "2.0",
                        "id": 2,
                        "method": "tools/list",
                        "params": {}
                        }
                        """,
                sessionId);

        String toolsBody = responseJson(tools.getBody());

        assertTrue(tools.getStatusCode().is2xxSuccessful());
        assertTrue(toolsBody.contains("getReturnPolicy"), "getReturnPolicy doit être exposé");

        // 4. Appeler l'outil
        ResponseEntity<String> result = post(
                """
                        {
                        "jsonrpc": "2.0",
                        "id": 3,
                        "method": "tools/call",
                        "params": {
                        "name": "getReturnPolicy",
                        "arguments": {
                        "category": "electronics"
                        }
                        }
                        }
                        """,
                sessionId);

        String resultBody = responseJson(result.getBody());

        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertTrue(resultBody.contains("30"),
                "La durée de retour attendue est de 30 jours");
        assertTrue(resultBody.contains("good condition"),
                "Les conditions de retour doivent être présentes");
    }

    private ResponseEntity<String> post(String body, String sessionId) {

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.parseMediaType("text/event-stream")));

        if (sessionId != null) {
            headers.set("Mcp-Session-Id", sessionId);
        }

        return http.exchange(
                MCP_PATH,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private String responseJson(String body) {
        assertNotNull(body, "La réponse MCP ne doit pas être vide");

        // Accepte une réponse JSON directe ou une réponse SSE.
        return body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .findFirst()
                .orElse(body.trim());
    }
}
