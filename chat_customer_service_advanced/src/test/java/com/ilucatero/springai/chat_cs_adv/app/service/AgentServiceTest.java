package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

class AgentServiceTest {

    private AgentService agentService;

    private ChatClientRequestSpec chatClientRequest;
    private CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        var chatClientBuilder = mock(ChatClient.Builder.class, RETURNS_SELF);
        var orderRepository = mock(OrderRepository.class, RETURNS_SELF);
        var toolCallingManager = mock(ToolCallingManager.class, RETURNS_SELF);

        var chatClient = mock(ChatClient.class);
        chatClientRequest = mock(ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(chatClientRequest);
        when(chatClientRequest.system(any(String.class))).thenReturn(chatClientRequest);
        when(chatClientRequest.messages(anyList())).thenReturn(chatClientRequest);
        when(chatClientRequest.tools(any(Object[].class))).thenReturn(chatClientRequest);
        when(chatClientRequest.advisors(any(Advisor.class))).thenReturn(chatClientRequest);
        when(chatClientRequest.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenReturn(chatClientRequest);

        responseSpec = mock(CallResponseSpec.class);
        when(chatClientRequest.call()).thenReturn(responseSpec);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(orderRepository.get(anyString())).thenReturn(new Order("123", Order.Status.SHIPPED, null, null));

        agentService = new AgentService(toolCallingManager, chatClientBuilder, mock(ChatMemory.class), orderRepository,
                mock(DeliveryAssessmentService.class));
    }

    @Test
    void shouldOnlyCallOrderStatusToolWhenAskingForOrderStatus() {
        // Given
        var request = new AgentRequest(
                "Quel est le statut de la commande 123 ?",
                "session-123456489");

        when(responseSpec.content()).thenReturn("La commande 123 est en cours de traitement.");

        // When
        var response = agentService.executeTask(request);

        // Then
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer()).isEqualTo("La commande 123 est en cours de traitement.");
        assertThat(response.sessionId()).isEqualTo("session-123456489");
    }

    @Test
    void confirmationWithoutPreparedCancellationExpires() {
        var confirmed = agentService.confirmCancellation("session-1", "invalid-confirmation-id");

        assertThat(confirmed.status()).isEqualTo(AgentResponse.Status.CONFIRMATION_EXPIRED);
        assertThat(confirmed.toolExecutions()).isEmpty();
        assertThat(confirmed.sessionId()).isEqualTo("session-1");
    }
}
