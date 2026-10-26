package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.service.DeliveryAssessmentService.DeliveryAssessment;
import com.ilucatero.springai.chat_cs_adv.app.support.FakeReturnPolicyTool;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatModel;
import com.ilucatero.springai.chat_cs_adv.app.support.ScriptedChatResponses;

class AgentServiceOrchestrationTest {

    OrderRepository orderRepository;
    ChatMemory chatMemory;
    ToolCallbackProvider mcpTools;
    DefaultToolCallingManager toolCallingManager;
    DeliveryAssessmentService deliveryAssessmentService;

    @BeforeEach
    void setUp() {
        chatMemory = mock(ChatMemory.class);
        toolCallingManager = DefaultToolCallingManager.builder().build();
        orderRepository = mock(OrderRepository.class);
        deliveryAssessmentService = mock(DeliveryAssessmentService.class);

        mcpTools = mock(ToolCallbackProvider.class);
        when(mcpTools.getToolCallbacks()).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
    }

    @Test
    void shouldExecuteOrderStatusToolAndReturnFinalAnswer() {
        // given
        when(orderRepository.get("123"))
                .thenReturn(new Order("123", Order.Status.SHIPPED, null, null));
        when(deliveryAssessmentService.assess(anyString())).thenReturn(DeliveryAssessment.ON_TRACK);
        when(chatMemory.get("session-123")).thenReturn(java.util.List.of());

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("checkOrderStatus", "{\"orderId\":\"123\"}"),
                ScriptedChatResponses.finalAnswer("La commande 123 a été expédiée."));

        var agentService = createAgentService(model, mcpTools);

        // when
        var response = agentService.executeTask(new AgentRequest(
                "Quel est le statut de la commande 123 ?",
                "session-123"));

        // then
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer()).isEqualTo("La commande 123 a été expédiée.");
        assertThat(response.toolExecutions()).anyMatch(execution -> execution.toolName().equals("checkOrderStatus"));
    }

    @Test
    void shouldHandleTechnicalErrorWhenCheckingOrderStatus() {
        // given
        when(orderRepository.get("123")).thenThrow(new IllegalStateException("Database unavailable"));
        when(chatMemory.get("session-error")).thenReturn(java.util.List.of());

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("checkOrderStatus", "{\"orderId\":\"123\"}"),
                ScriptedChatResponses.finalAnswer(
                        "I couldn't retrieve the order information due to a technical problem."));

        var agentService = createAgentService(model, mcpTools);

        // when
        var response = agentService.executeTask(new AgentRequest(
                "Quel est le statut de la commande 123 ?",
                "session-error"));

        // then
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer()).contains("technical problem");
        assertThat(response.answer()).doesNotContain("ORDER_NOT_FOUND");
    }

    @Test
    void shouldExecuteMcpToolAndUseItsResultInFinalAnswer() {
        // Given
        var mcpToolFake = new FakeReturnPolicyTool();

        var mcpTools = mock(ToolCallbackProvider.class);
        when(mcpTools.getToolCallbacks()).thenReturn(new ToolCallback[] { mcpToolFake });

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("getReturnPolicy", "{\"category\":\"electronics\"}"),
                ScriptedChatResponses.finalAnswer(
                        "Les produits électroniques peuvent être retournés sous 30 jours."));

        var agentService = createAgentService(model, mcpTools);

        // When
        var response = agentService.executeTask(new AgentRequest(
                "Quelle est la politique de retour pour les produits électroniques ?",
                "session-123"));

        // Then
        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer()).contains("30 jours");
        assertThat(mcpToolFake.getCallCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleMcpToolErrorAndReturnFinalAnswer() {
        // Given
        var mcpToolFake = new FakeReturnPolicyTool(true);

        var mcpTools = mock(ToolCallbackProvider.class);
        when(mcpTools.getToolCallbacks()).thenReturn(new ToolCallback[] { mcpToolFake });

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("getReturnPolicy", "{\"category\":\"electronics\"}"),
                ScriptedChatResponses.finalAnswer(
                        "Je ne peux pas vérifier la politique de retour actuellement."));

        var agentService = createAgentService(model, mcpTools);

        // When
        var response = agentService.executeTask(new AgentRequest(
                "Quelle est la politique de retour pour les produits électroniques ?",
                "session-123"));

        // Then
        assertThat(mcpToolFake.getCallCount()).isEqualTo(1);
        assertThat(response.status()).isEqualTo(AgentResponse.Status.FAILED);
        assertThat(response.answer()).contains("Error on FakeReturnPolicyTool");
    }

    @Test
    void shouldAnswerWithoutCallingAnyTool() {
        var model = new ScriptedChatModel(ScriptedChatResponses.finalAnswer("Bonjour ! Comment puis-je vous aider ?"));

        var agentService = createAgentService(model, mcpTools);

        var response = agentService.executeTask(new AgentRequest("session-123", "Bonjour"));

        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer()).isEqualTo("Bonjour ! Comment puis-je vous aider ?");
        assertThat(response.toolExecutions()).isEmpty();
    }

    @Test
    void shouldExecuteBusinessAndMcpToolsInSameTask() {
        var mcpTool = new FakeReturnPolicyTool();

        var mcpTools = mock(ToolCallbackProvider.class);
        when(mcpTools.getToolCallbacks()).thenReturn(new ToolCallback[] { mcpTool });

        var model = new ScriptedChatModel(
                ScriptedChatResponses.toolCall("checkOrderStatus", "{\"orderId\":\"123\"}"),
                ScriptedChatResponses.toolCall("getReturnPolicy", "{\"category\":\"electronics\"}"),
                ScriptedChatResponses.finalAnswer(
                        "La commande 123 a été expédiée. Les produits électroniques sont retournables sous 30 jours."));

        var agentService = createAgentService(model, mcpTools);

        var response = agentService.executeTask(new AgentRequest(
                "Quel est le statut de la commande 123 et la politique de retour des produits électroniques ?",
                "session-123"));

        assertThat(response.status()).isEqualTo(AgentResponse.Status.COMPLETED);
        assertThat(response.answer())
                .contains("expédiée")
                .contains("30 jours");
        assertThat(response.toolExecutions())
                .extracting((@NonNull ToolExecutionInfo execution) -> execution.toolName())
                .contains("checkOrderStatus");
        assertThat(mcpTool.getCallCount()).isEqualTo(1);

    }

    private AgentService createAgentService(ChatModel model, ToolCallbackProvider mcpTools) {
        return new AgentService(
                toolCallingManager,
                ChatClient.builder(model),
                chatMemory,
                orderRepository,
                deliveryAssessmentService,
                mcpTools);
    }

}
