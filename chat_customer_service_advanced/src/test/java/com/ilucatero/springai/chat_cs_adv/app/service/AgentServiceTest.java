package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;

class AgentServiceTest {

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        var chatClientBuilder = mock(ChatClient.Builder.class, RETURNS_SELF);
        var orderRepository = mock(OrderRepository.class, RETURNS_SELF);
        var toolCallingManager = mock(ToolCallingManager.class, RETURNS_SELF);

        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        when(orderRepository.get(anyString())).thenReturn(new Order("123", Order.Status.SHIPPED, null, null));

        agentService = new AgentService(toolCallingManager, chatClientBuilder, mock(ChatMemory.class), orderRepository);
    }

    @Test
    void confirmationWithoutPreparedCancellationExpires() {
        var confirmed = agentService.confirmCancellation("session-1", "invalid-confirmation-id");

        assertThat(confirmed.status()).isEqualTo(AgentResponse.Status.CONFIRMATION_EXPIRED);
        assertThat(confirmed.toolExecutions()).isEmpty();
        assertThat(confirmed.sessionId()).isEqualTo("session-1");
    }
}
