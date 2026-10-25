package com.ilucatero.springai.chat_cs_adv.app.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentServiceIT {

    private static final WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    @Autowired
    private AgentService agentService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMock.start();

        registry.add(
                "spring.ai.openai.base-url",
                () -> "http://localhost:" + wireMock.port());
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add(
                "spring.ai.openai.chat.model",
                () -> "gpt-4o-mini");
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void configureModelResponses() {
        wireMock.resetAll();

        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .inScenario("order-status")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("after-tool")
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-test-1",
                          "object": "chat.completion",
                          "created": 1760000000,
                          "model": "gpt-4o-mini",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": null,
                              "tool_calls": [{
                                "id": "call_status_1",
                                "type": "function",
                                "function": {
                                  "name": "%s",
                                  "arguments": "{\\"orderId\\":\\"123\\"}"
                                }
                              }]
                            },
                            "finish_reason": "tool_calls"
                          }]
                        }
                        """.formatted(OrderStatusTool.TOOL_NAME_GET_ORDER))));

        wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                .inScenario("order-status")
                .whenScenarioStateIs("after-tool")
                .willReturn(okJson("""
                        {
                          "id": "chatcmpl-test-2",
                          "object": "chat.completion",
                          "created": 1760000001,
                          "model": "gpt-4o-mini",
                          "choices": [{
                            "index": 0,
                            "message": {
                              "role": "assistant",
                              "content": "Le statut de la commande 123 est PROCESSING."
                            },
                            "finish_reason": "stop"
                          }]
                        }
                        """)));
    }

    @Test
    void shouldOnlyCallOrderStatusToolWhenAskingForStatus() {
        // Given
        var request = new AgentRequest(
                "Quel est le statut de la commande 123 ?",
                null);

        // When
        var response = agentService.executeTask(request);

        System.out.println("STATUS = " + response.status());
        System.out.println("MESSAGE = " + response.answer());
        System.out.println("WIREMOCK EVENTS = " + wireMock.getAllServeEvents().size());
        wireMock.getAllServeEvents().forEach(event -> {
            System.out.println("URL: " + event.getRequest().getUrl());
            System.out.println("METHOD: " + event.getRequest().getMethod());
            System.out.println("BODY: " + event.getRequest().getBodyAsString());
            System.out.println("STATUS: " + event.getResponse().getStatus());
            System.out.println("UNMATCHED: " + event.getWasMatched());
        });
        System.out.println("UNMATCHED REQUESTS:");
        wireMock.findAllUnmatchedRequests().forEach(System.out::println);

        // Then
        assertThat(response.status())
                .isEqualTo(AgentResponse.Status.COMPLETED);

        assertThat(response.toolExecutions())
                .extracting("toolName")
                .containsExactly(OrderStatusTool.TOOL_NAME_GET_ORDER);

        assertThat(response.toolExecutions().get(0).arguments())
                .anyMatch(arg -> arg.contains("123"));

        // Vérifie que le serveur simulé a reçu deux requêtes :
        // une pour demander le tool, une pour produire la réponse finale.
        wireMock.verify(2, postRequestedFor(
                urlPathEqualTo("/chat/completions")));
    }
}
