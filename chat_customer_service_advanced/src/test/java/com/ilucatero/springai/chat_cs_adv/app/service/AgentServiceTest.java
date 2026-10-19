package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class AgentServiceTest {

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        var chatClientBuilder = mock(ChatClient.Builder.class, RETURNS_SELF);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        agentService = new AgentService(chatClientBuilder, mock(ChatMemory.class));
    }

    @Test
    void advisorInterceptsCancellationToolCallAndWaitsForExplicitConfirmation() {
        var advisor = agentService.createCancellationAdvisor("session-1");
        var intercepted = advisor.doAfterCall(toolCallResponse("123"), mock(CallAdvisorChain.class));

        assertThat(intercepted.chatResponse().hasToolCalls()).isFalse();
        assertThat(intercepted.chatResponse().getResult().getOutput().getText())
                .contains("commande 123", "Confirmer l'annulation");
        assertThat(advisor.pendingCancellation()).isNotNull();
        assertThat(advisor.pendingCancellation().orderId()).isEqualTo("123");
        assertThat(advisor.pendingCancellation().status()).isEqualTo("SHIPPED");

        var confirmed = agentService.confirmCancellation(
                "session-1",
                advisor.pendingCancellation().confirmationId());

        assertThat(confirmed.toolExecutions())
                .extracting("toolName")
                .containsExactly("checkOrderStatus", "cancelOrder");
        assertThat(confirmed.answer()).contains("CANCELLED");
    }

    @Test
    void deliveredOrderCannotBeQueuedOrCancelled() {
        var advisor = agentService.createCancellationAdvisor("session-2");
        var intercepted = advisor.doAfterCall(toolCallResponse("456"), mock(CallAdvisorChain.class));

        assertThat(intercepted.chatResponse().hasToolCalls()).isFalse();
        assertThat(intercepted.chatResponse().getResult().getOutput().getText()).contains("déjà livrée");
        assertThat(advisor.pendingCancellation()).isNull();
    }

    @Test
    void staleConfirmationCannotCancelAnOrder() {
        var advisor = agentService.createCancellationAdvisor("session-3");
        advisor.doAfterCall(toolCallResponse("123"), mock(CallAdvisorChain.class));

        var confirmed = agentService.confirmCancellation("session-3", "invalid-confirmation-id");

        assertThat(confirmed.status()).isEqualTo("confirmation_expired");
        assertThat(confirmed.toolExecutions()).isEmpty();
    }

    private ChatClientResponse toolCallResponse(String orderId) {
        var toolCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "cancelOrder",
                "{\"orderId\":\"" + orderId + "\"}");
        var message = AssistantMessage.builder().toolCalls(List.of(toolCall)).build();
        return new ChatClientResponse(new ChatResponse(List.of(new Generation(message))), Map.of());
    }
}
