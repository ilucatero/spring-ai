package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.OrderUserActionConfirmation;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.CustomToolCallingAdvisor;
import com.ilucatero.springai.chat_cs_adv.app.tool.CustomToolCallingAdvisor.OrderUserActionResult;

@Service
public class AgentService {

    private static final String PROMT_SYSTEM = """
            You are a customer support agent.

            Objective: Answer questions about orders.

            GENERAL RULES:
             - Use tools to retrieve real information.
             - Never assume the status of an order.
             - If a result is insufficient, decide whether another tool can provide useful information.
             - If the order cannot be found, explain it to the customer.
             - Finish when you have the necessary information.
             - Limit data retrieval to 3 distinct orders maximum per request.
             - Never cancel an order without specific user approval.

            DECISION STRATEGY:
             - First, retrieve the order status when the user asks about an order.
             - If the user asks whether they should worry about an order, inspect the delivery details when they provide useful additional information.
             - Do not call a tool if its result is not needed to answer the user's question.
             - Do not repeat a tool call when its result is already available and still valid.
             - If the available information is sufficient, answer without calling additional tools.
             - If the tool-call limit prevents you from obtaining all necessary information, explain what you could verify and what remains unknown.
             - Never conclude that an order is delayed unless the retrieved information supports that conclusion.

            IMPORTANT — TOOL ERROR HANDLING:
             - When a tool execution fails with a technical error, do not interpret the error as ORDER_NOT_FOUND.
             - Do not claim that the order does not exist.
             - Explain that the order information could not be retrieved due to a technical problem.
             - Never invent the result of a failed tool call.
             - Compare the scheduled delivery date with the current date before assessing whether a customer should worry.
             - If the scheduled delivery date is in the past, do not reassure the customer that everything is on schedule.
             - Explain that the delivery date has passed and recommend checking the latest delivery status.
             - If the current date is unavailable, do not make claims about whether the delivery is on time.
            """;

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final List<Order.Status> CANCELLABLE_STATUS = List.of(
            Order.Status.PROCESSING, Order.Status.READY_TO_SHIP, Order.Status.SHIPPING, Order.Status.SHIPPED);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ToolCallingManager toolCallingManager;
    private final OrderRepository orderRepository;

    // recorder must be renewed on each session call, no previous state reuse
    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderDeliveranceTool orderDeliveranceTool;
    private final OrderStatusTool orderStatusTool;
    private final OrderCancelationTool orderCancelationTool;
    private final ToolCallbackProvider mcpTools;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, OrderUserActionConfirmation> pendingOrderUserActions = new ConcurrentHashMap<>();

    /**
     * Constructor with auto-wired ChatClient.Builder.
     * Tool instances are created here and passed to ChatClient at call time.
     *
     * IMPORTANT : never build a ToolCallingManager inline, otherwise the
     * parametered limits wont be used
     */
    public AgentService(ToolCallingManager toolCallingManager,
            ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            OrderRepository orderRepository,
            DeliveryAssessmentService deliveryAssessmentService,
            ToolCallbackProvider mcpTools) {

        this.toolCallingManager = toolCallingManager;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;
        this.orderRepository = orderRepository;

        this.mcpTools = mcpTools;

        this.toolExecutionRecorder = new ToolExecutionInfo.Recorder();
        this.orderDeliveranceTool = new OrderDeliveranceTool(toolExecutionRecorder, orderRepository,
                deliveryAssessmentService);
        this.orderStatusTool = new OrderStatusTool(toolExecutionRecorder, orderRepository);
        this.orderCancelationTool = new OrderCancelationTool(toolExecutionRecorder, orderRepository);

        log.info("Agent service initialized with Spring AI ChatClient");
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
        pendingOrderUserActions.remove(sessionId);

        toolExecutionRecorder.start();
        var cancellationAdvisor = createCancellingToolCallingAdvisor(sessionId);

        try {
            // Add user message to memory
            chatMemory.add(sessionId, new UserMessage(request.message()));

            // Get conversation history from ChatMemory
            var messages = chatMemory.get(sessionId);

            // Call via ChatClient with tools — Spring AI handles tool execution loop
            var answer = chatClient
                    .prompt()
                    .system(PROMT_SYSTEM)
                    .messages(messages)
                    .tools(orderDeliveranceTool, orderStatusTool, orderCancelationTool, mcpTools)
                    .advisors(cancellationAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            // Add AI response to memory
            chatMemory.add(sessionId, new AssistantMessage(answer));
            var toolExecutions = toolExecutionRecorder.stop();

            log.info("Agent completed task successfully with {} tool execution(s)", toolExecutions.size());

            return new AgentResponse(
                    answer,
                    sessionId,
                    toolExecutions,
                    AgentResponse.Status.COMPLETED,
                    cancellationAdvisor.pendingOrderUserAction());

        } catch (Exception e) {
            var toolExecutions = toolExecutionRecorder.stop();
            log.error("Agent task execution failed", e);
            return new AgentResponse(
                    "I encountered an error: " + e.getMessage(),
                    sessionId,
                    toolExecutions,
                    AgentResponse.Status.FAILED);
        }
    }

    CustomToolCallingAdvisor createCancellingToolCallingAdvisor(String sessionId) {
        return new CustomToolCallingAdvisor(this.toolCallingManager,
                toolCall -> OrderCancelationTool.TOOL_NAME_CANCEL_ORDER.equals(toolCall.name()),
                toolArguments -> prepareCancellation(toolArguments, sessionId));
    }

    private CustomToolCallingAdvisor.OrderUserActionResult prepareCancellation(String toolArguments, String sessionId) {
        var orderId = (String) null;

        // parse tool arguments to extrat Order Id
        try {
            var orderIdNode = objectMapper.readTree(toolArguments).get("orderId");
            if (orderIdNode == null || !orderIdNode.isTextual() || orderIdNode.asText().isBlank()) {
                return new OrderUserActionResult(
                        "To verify if the cancellation is possible, please provide the order number.",
                        null);
            }
            orderId = orderIdNode.textValue();

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Ignoring malformed cancelOrder tool arguments");
            return new OrderUserActionResult(
                    "I couldn't identify the order number. Please specify it.",
                    null);
        }

        // get Order from DB
        var order = orderRepository.get(orderId);

        // extract status
        var status = order.status;
        if (!CANCELLABLE_STATUS.contains(status)) {
            return switch (status) {
                case ORDER_NOT_FOUND -> new CustomToolCallingAdvisor.OrderUserActionResult(
                        "I couldn't find the order %s. Please verify the order number before requesting cancellation."
                                .formatted(orderId),
                        null);
                default -> new CustomToolCallingAdvisor.OrderUserActionResult(
                        "The order %s with status %s cannot be cancelled. No cancellation has been prepared."
                                .formatted(orderId, status),
                        null);
            };
        }

        // extract details
        var details = order.deliveryDetails;
        if (details == null) {
            details = "No additional delivery details are available.";
        }

        // create confirmation user action
        var confirmation = new OrderUserActionConfirmation(UUID.randomUUID().toString(), orderId, status.name(),
                details);
        pendingOrderUserActions.put(sessionId, confirmation);

        // returns user action result
        return new CustomToolCallingAdvisor.OrderUserActionResult(
                "The order %s is not delivered. Please verify the details below, then choose \"Confirm Cancellation\" if you wish to proceed."
                        .formatted(orderId),
                confirmation);
    }

    public AgentResponse confirmCancellation(String sessionId, String confirmationId) {
        var pending = pendingOrderUserActions.get(sessionId);
        if (pending == null
                || !pending.confirmationId().equals(confirmationId)
                || !pendingOrderUserActions.remove(sessionId, pending)) {
            return new AgentResponse(
                    "This confirmation request has expired. Please submit your cancellation request again.",
                    sessionId,
                    List.of(),
                    AgentResponse.Status.CONFIRMATION_EXPIRED);
        }

        toolExecutionRecorder.start();
        try {
            var status = orderStatusTool.getOrderStatus(pending.orderId());
            final String answer;
            if (CANCELLABLE_STATUS.contains(status)) {
                var result = orderCancelationTool.cancelOrder(pending.orderId());
                answer = "Result of canceling order %s: %s.".formatted(pending.orderId(), result);
            } else {
                if (Order.Status.ORDER_NOT_FOUND.equals(status)) {
                    answer = "I couldn't find the order %s. No cancellation has been made."
                            .formatted(pending.orderId());
                } else {
                    answer = "The order %s is now marked with status %s. No cancellation can been made."
                            .formatted(pending.orderId(), status);
                }
            }
            chatMemory.add(sessionId,
                    new UserMessage("I confirm the cancellation of order %s.".formatted(pending.orderId())));
            chatMemory.add(sessionId, new AssistantMessage(answer));
            return new AgentResponse(answer, sessionId, toolExecutionRecorder.stop(), AgentResponse.Status.COMPLETED);
        } catch (RuntimeException e) {
            toolExecutionRecorder.stop();
            throw e;
        }
    }

    public AgentResponse declineCancellation(String sessionId, String confirmationId) {
        var pending = pendingOrderUserActions.get(sessionId);
        if (pending == null
                || !pending.confirmationId().equals(confirmationId)
                || !pendingOrderUserActions.remove(sessionId, pending)) {
            return new AgentResponse(
                    "This confirmation request has expired. Please submit your cancellation request again.",
                    sessionId,
                    List.of(),
                    AgentResponse.Status.CONFIRMATION_EXPIRED);
        }

        var answer = "The cancellation of order %s has been declined.".formatted(pending.orderId());
        chatMemory.add(sessionId, new UserMessage("I do not wish to cancel order %s.".formatted(pending.orderId())));
        chatMemory.add(sessionId, new AssistantMessage(answer));
        return new AgentResponse(answer, sessionId, List.of(), AgentResponse.Status.COMPLETED);
    }

    /**
     * Clear agent session.
     * Removes conversation history for the given session ID.
     */
    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
        activeSessions.remove(sessionId);
        pendingOrderUserActions.remove(sessionId);
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
