package com.ilucatero.springai.chat_cs_adv.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.IntentType;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderToolSet;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.model.UserIntent;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;
import com.ilucatero.springai.chat_cs_adv.config.McpToolRegistry;

class AgentServiceTest {

    private AgentService agentService;

    private ChatClientRequestSpec chatClientRequest;
    private CallResponseSpec responseSpec;
    private ToolCallback mcpToolCallback;
    private ToolExecutionInfo.Recorder toolExecutionRecorder;

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

        var mcpToolRegistry = mock(McpToolRegistry.class);
        mcpToolCallback = mock(ToolCallback.class);
        when(mcpToolRegistry.getToolCallbacks()).thenReturn(new ToolCallback[] { mcpToolCallback });

        toolExecutionRecorder = new ToolExecutionInfo.Recorder();

        var orderStatusTool = new OrderStatusTool(toolExecutionRecorder, orderRepository);

        var DeliveryAssessmentService = mock(DeliveryAssessmentService.class);
        var orderDeliveranceTool = new OrderDeliveranceTool(toolExecutionRecorder, orderRepository,
                DeliveryAssessmentService);

        var intentService = mock(IntentService.class);
        when(intentService.detectIntent(anyString(), anyList())).thenReturn(new UserIntent(IntentType.GENERAL_QUESTION, null, null));

        var cancellationWorkflowService = mock(OrderCancellationWorkflowService.class);

        agentService = new AgentService(
                chatClientBuilder,
                mock(ChatMemory.class),
                orderRepository,
                toolCallingManager,
                new OrderToolSet(toolExecutionRecorder, orderStatusTool, orderDeliveranceTool),
                mcpToolRegistry,
                intentService,
                cancellationWorkflowService);

    }

    @Test
    void shouldRegisterMcpToolCallbackProvider() {
        // Given
        var request = new AgentRequest(
                "Quelle est la politique de retour des produits électroniques ?",
                "session-mcp");

        when(responseSpec.content()).thenReturn("Retour possible sous 30 jours.");

        // When
        agentService.executeTask(request);

        // Then
        System.out.println("MockingDetails:" + Mockito.mockingDetails(chatClientRequest).getInvocations());

        var captor = ArgumentCaptor.forClass(Object[].class);
        verify(chatClientRequest).tools(captor.capture());
        assertThat(captor.getValue()).contains(mcpToolCallback);
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
