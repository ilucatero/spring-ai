package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

class AgentServiceTest {

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        var chatClientBuilder = mock(ChatClient.Builder.class, RETURNS_SELF);
        var orderRepository = mock(OrderRepository.class, RETURNS_SELF);

        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        when(orderRepository.get(anyString())).thenReturn(new Order("123", Order.Status.SHIPPED, null, null));

        agentService = new AgentService(chatClientBuilder, mock(ChatMemory.class), orderRepository);
    }

    @Test
    void shippedOrderCannotBeQueuedOrCancelled() {
        var advisor = agentService.createCancellationAdvisor("session-1");
        var intercepted = advisor.doAfterCall(toolCallResponse("123"), mock(CallAdvisorChain.class));

        assertThat(intercepted.chatResponse().hasToolCalls()).isFalse();
        assertThat(intercepted.chatResponse().getResult().getOutput().getText())
                .isEqualTo(
                        "The order 123 is not delivered. Please verify the details below, then choose \"Confirm Cancellation\" if you wish to proceed.");
        assertThat(advisor.pendingCancellation()).isNotNull();
    }

    @Test
    void deliveredOrderCannotBeQueuedOrCancelled() {
        var advisor = agentService.createCancellationAdvisor("session-2");
        var intercepted = advisor.doAfterCall(toolCallResponse("456"), mock(CallAdvisorChain.class));

        assertThat(intercepted.chatResponse().hasToolCalls()).isFalse();
        assertThat(intercepted.chatResponse().getResult().getOutput().getText())
                .isEqualTo(
                        "The order 456 is not delivered. Please verify the details below, then choose \"Confirm Cancellation\" if you wish to proceed.");
        assertThat(advisor.pendingCancellation()).isNotNull();
    }

    @Test
    void staleConfirmationCannotCancelAnOrder() {
        var advisor = agentService.createCancellationAdvisor("session-3");
        advisor.doAfterCall(toolCallResponse("999"), mock(CallAdvisorChain.class));
        assertThat(advisor.pendingCancellation()).isNotNull();

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
