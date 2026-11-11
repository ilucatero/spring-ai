package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderUserActionConfirmation;
import com.ilucatero.springai.chat_cs_adv.app.model.UserIntent;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderToolSet;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.repository.SystemPromptRepository;
import com.ilucatero.springai.chat_cs_adv.config.McpToolRegistry;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ToolCallingManager toolCallingManager;
    private final OrderRepository orderRepository;

    // recorder must be renewed on each session call, no previous state reuse
    private final OrderToolSet orderToolSet;
    private final McpToolRegistry mcpTools;

    private final IntentService intentService;
    private final OrderCancellationWorkflowService cancellationWorkflowService;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, OrderUserActionConfirmation> pendingOrderUserActions = new ConcurrentHashMap<>();

    /**
     * Constructor with auto-wired ChatClient.Builder.
     * Tool instances are created here and passed to ChatClient at call time.
     *
     * IMPORTANT : never build a ToolCallingManager inline, otherwise the
     * parametered limits wont be used
     */
    public AgentService(
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            OrderRepository orderRepository,
            ToolCallingManager toolCallingManager,
            OrderToolSet orderToolSet,
            McpToolRegistry mcpTools,
            IntentService intentService,
            OrderCancellationWorkflowService cancellationWorkflowService) {

        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.orderRepository = orderRepository;

        this.toolCallingManager = toolCallingManager;
        this.mcpTools = mcpTools;
        this.orderToolSet = orderToolSet;

        this.intentService = intentService;
        this.cancellationWorkflowService = cancellationWorkflowService;

    }

    /**
     * Create a new agent session.
     * Returns a new UUID used to track conversation history.
     */
    public String createAgentSession() {
        var sessionId = UUID.randomUUID().toString();
        activeSessions.add(sessionId);
        log.info("Created new agent session: {}", sessionId);
        return sessionId;
    }

    /**
     * Execute an agent task using ChatClient with automatic tool orchestration.
     * The ChatClient handles the tool calling loop: when the model wants to call a
     * tool,
     * Spring AI executes the @Tool method and feeds the result back to the model.
     */
    public AgentResponse executeTask(AgentRequest request) {
        log.info("Executing agent task: {}", request.message());

        // log toolManager config
        inspectToolCallingManager(toolCallingManager);

        final var sessionId = request.sessionId() == null ? createAgentSession() : request.sessionId();
        activeSessions.add(sessionId);
        invalidatePendingConfirmation(sessionId);

        var intent = intentService.detectIntent(request.message(), chatMemory.get(sessionId));

        // Add user message to memory
        chatMemory.add(sessionId, new UserMessage(request.message()));
        var response = routeIntent(intent, sessionId, request);

        chatMemory.add(sessionId, new AssistantMessage(response.answer()));

        return response;
    }

    private AgentResponse routeIntent(UserIntent intent, String sessionId, AgentRequest request) {
        return switch (intent.intent()) {
            case CANCEL_ORDER ->
                createCancellationResponse(sessionId, intent);
            case ORDER_STATUS ->
                handleOrderStatus(intent.orderId(), sessionId);
            case GENERAL_QUESTION ->
                handleGeneralQuestion(sessionId, request.message());
        };
    }

    private void invalidatePendingConfirmation(String sessionId) {
        var pending = pendingOrderUserActions.remove(sessionId);

        if (pending != null) {
            try {
                cancellationWorkflowService.confirmCancellation(pending.workflowId(), false);
            } catch (RuntimeException e) {
                log.warn("Unable to invalidate workflow {}", pending.workflowId(), e);
            }
        }
    }

    private AgentResponse createResponse(String sessionId, String message) {
        return new AgentResponse(
                message,
                sessionId,
                List.of(),
                AgentResponse.Status.COMPLETED);
    }

    private AgentResponse createCancellationResponse(String sessionId, UserIntent intent) {

        if (intent.orderId() == null || intent.orderId().isBlank()) {
            return createResponse(
                    sessionId,
                    "Can you please provide the order number you wish to cancel? I need it to check if the cancellation is possible.");
        }

        try {
            // get order before workflow runs, to avoid creating a workflow if an exception
            // occurs
            var order = orderRepository.get(intent.orderId());

            var context = cancellationWorkflowService.startCancellation(intent.orderId());

            var confirmation = new OrderUserActionConfirmation(
                    UUID.randomUUID().toString(),
                    context.workflowId(),
                    context.orderId(),
                    context.orderStatus().name(),
                    order.deliveryDetails);
            pendingOrderUserActions.put(sessionId, confirmation);

            var message = "The order %s is eligible for cancellation. Please confirm before proceeding."
                    .formatted(context.orderId());

            return new AgentResponse(
                    message,
                    sessionId,
                    List.of(),
                    AgentResponse.Status.COMPLETED,
                    confirmation);

        } catch (IllegalStateException e) {
            log.info("Cancellation rejected: {}", e.getMessage());
            return createResponse(sessionId, e.getMessage());
        }
    }

    private AgentResponse handleOrderStatus(String orderId, String sessionId) {

        if (orderId == null || orderId.isBlank()) {
            return createResponse(
                    sessionId,
                    "Could you please provide your order number?");
        }

        var order = orderRepository.get(orderId);
        var message = "The status of order %s is: %s.".formatted(orderId, order.status);

        return new AgentResponse(
                message,
                sessionId,
                List.of(),
                AgentResponse.Status.COMPLETED);
    }

    private AgentResponse handleGeneralQuestion(String sessionId, String message) {

        orderToolSet.toolExecutionRecorder().start();

        try {

            // Get conversation history from ChatMemory
            var messages = chatMemory.get(sessionId);

            // Call via ChatClient with tools — Spring AI handles tool execution loop
            var toolObjects = Stream.concat(
                    Stream.of(orderToolSet.getTools()),
                    Arrays.stream(mcpTools.getToolCallbacks()))
                    .toArray();

            var answer = chatClient
                    .prompt()
                    .system(SystemPromptRepository.getSystemPrompt())
                    .messages(messages)
                    .tools(toolObjects)
                    .call()
                    .content();

            var toolExecutions = orderToolSet.toolExecutionRecorder().stop();

            log.info("Agent completed task successfully with {} tool execution(s)", toolExecutions.size());

            return new AgentResponse(
                    answer,
                    sessionId,
                    toolExecutions,
                    AgentResponse.Status.COMPLETED);

        } catch (Exception e) {
            var toolExecutions = orderToolSet.toolExecutionRecorder().stop();
            log.error("Agent task execution failed", e);
            return new AgentResponse(
                    "I encountered an error: " + e.getMessage(),
                    sessionId,
                    toolExecutions,
                    AgentResponse.Status.FAILED);
        }
    }

    public AgentResponse confirmCancellation(String sessionId, String confirmationId) {

        var pending = pendingOrderUserActions.get(sessionId);
        if (pending == null || !pending.confirmationId().equals(confirmationId)) {
            return expiredConfirmationResponse(sessionId);
        }

        // Consume confirmation
        if (!pendingOrderUserActions.remove(sessionId, pending)) {
            return expiredConfirmationResponse(sessionId);
        }

        orderToolSet.toolExecutionRecorder().start();

        try {
            var context = cancellationWorkflowService.confirmCancellation(pending.workflowId(), true);

            var answer = "Result of cancelling order %s: %s."
                    .formatted(context.orderId(), context.workflowStatus());

            chatMemory.add(sessionId,
                    new UserMessage("I confirm the cancellation of order %s.".formatted(pending.orderId())));
            chatMemory.add(sessionId, new AssistantMessage(answer));

            return new AgentResponse(
                    answer,
                    sessionId,
                    orderToolSet.toolExecutionRecorder().stop(),
                    AgentResponse.Status.COMPLETED);

        } catch (RuntimeException e) {
            orderToolSet.toolExecutionRecorder().stop();
            throw e;
        }
    }

    public AgentResponse declineCancellation(String sessionId, String confirmationId) {
        var pending = pendingOrderUserActions.get(sessionId);
        if (pending == null
                || !pending.confirmationId().equals(confirmationId)
                || !pendingOrderUserActions.remove(sessionId, pending)) {
            return expiredConfirmationResponse(sessionId);
        }

        orderToolSet.toolExecutionRecorder().start();

        try {
            var context = cancellationWorkflowService.confirmCancellation(pending.workflowId(), false);

            var answer = "The cancellation of order %s has been declined.".formatted(context.orderId());
            chatMemory.add(sessionId,
                    new UserMessage("I do not wish to cancel order %s.".formatted(context.orderId())));
            chatMemory.add(sessionId, new AssistantMessage(answer));

            return new AgentResponse(answer,
                    sessionId,
                    orderToolSet.toolExecutionRecorder().stop(),
                    AgentResponse.Status.COMPLETED);

        } catch (RuntimeException e) {
            orderToolSet.toolExecutionRecorder().stop();
            throw e;
        }
    }

    private AgentResponse expiredConfirmationResponse(String sessionId) {
        return new AgentResponse(
                "This confirmation request has expired. Please submit your cancellation request again.",
                sessionId,
                List.of(),
                AgentResponse.Status.CONFIRMATION_EXPIRED);
    }

    /**
     * Clear agent session.
     * Removes conversation history for the given session ID.
     */
    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
        activeSessions.remove(sessionId);
        invalidatePendingConfirmation(sessionId);
        log.info("Cleared session: {}", sessionId);
    }

    /** Logs ToolCallinManager configuration */
    public static void inspectToolCallingManager(ToolCallingManager manager) {

        if (!(manager instanceof DefaultToolCallingManager)) {
            log.info("""
                    === ToolCallingManager ===
                            Class: {}"
                            Gestionnaire non standard : inspection spécifique nécessaire.
                    ==========================
                    """,
                    manager.getClass().getName());
            return;
        }

        try {
            var limitsField = DefaultToolCallingManager.class.getDeclaredField("toolCallLimits");
            limitsField.setAccessible(true);
            var limits = limitsField.get(manager);

            var fieldLimits = new StringBuilder();
            for (var field : limits.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                fieldLimits.append("\n\t" + field.getName() + " = " + field.get(limits));
            }

            log.info("""
                    === ToolCallingManager ===
                        Class: {}
                        ToolCallLimits class: {}
                        ---- ToolCallLimits Field Limits --- {}
                    ==========================
                    """,
                    manager.getClass().getName(),
                    limits.getClass().getName(),
                    fieldLimits);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Impossible d'inspecter les limites", e);
        }
    }
}
