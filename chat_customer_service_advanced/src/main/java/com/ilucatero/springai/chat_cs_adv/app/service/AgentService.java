package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.CancellationConfirmation;
import com.ilucatero.springai.chat_cs_adv.app.model.Order;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.repository.OrderRepository;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderCancelationTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderDeliveranceTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.OrderStatusTool;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String CONFIRMATION_EXPIRED = "confirmation_expired";
    private static final List<Order.Status> CANCELLABLE_STATUS = List.of(
            Order.Status.PROCESSING, Order.Status.READY_TO_SHIP, Order.Status.SHIPPING, Order.Status.SHIPPED);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final OrderDeliveranceTool orderDeliveranceTool;
    private final OrderStatusTool orderStatusTool;
    private final OrderCancelationTool orderCancelationTool;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();
    private final Map<String, CancellationConfirmation> pendingCancellations = new ConcurrentHashMap<>();

    /**
     * Constructor with auto-wired ChatClient.Builder.
     * Tool instances are created here and passed to ChatClient at call time.
     */
    public AgentService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, OrderRepository orderRepository) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;

        this.toolExecutionRecorder = new ToolExecutionInfo.Recorder();
        this.orderDeliveranceTool = new OrderDeliveranceTool(toolExecutionRecorder, orderRepository);
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

        final var sessionId = request.sessionId() == null ? createAgentSession() : request.sessionId();

        try {
            activeSessions.add(sessionId);
            toolExecutionRecorder.start();
            pendingCancellations.remove(sessionId);
            var cancellationAdvisor = createCancellationAdvisor(sessionId);

            // Add user message to memory
            chatMemory.add(sessionId, new UserMessage(request.message()));

            // Get conversation history from ChatMemory
            var messages = chatMemory.get(sessionId);

            // Call via ChatClient with tools — Spring AI handles tool execution loop
            var answer = chatClient.prompt()
                    .messages(messages)
                    .tools(orderDeliveranceTool, orderStatusTool, orderCancelationTool)
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
                    "completed",
                    cancellationAdvisor.pendingCancellation());

        } catch (Exception e) {
            var toolExecutions = toolExecutionRecorder.stop();
            log.error("Agent task execution failed", e);
            return new AgentResponse(
                    "I encountered an error: " + e.getMessage(),
                    sessionId,
                    toolExecutions,
                    "failed");
        }
    }

    CancellationToolCallingAdvisor createCancellationAdvisor(String sessionId) {
        return new CancellationToolCallingAdvisor(orderId -> prepareCancellation(orderId, sessionId));
    }

    private CancellationToolCallingAdvisor.CancellationResult prepareCancellation(String id, String sessionId) {
        var status = orderStatusTool.getOrderStatus(id);
        if (!CANCELLABLE_STATUS.contains(status)) {
            return switch (status) {
                case ORDER_NOT_FOUND -> new CancellationToolCallingAdvisor.CancellationResult(
                        "I couldn't find the order %s. Please verify the order number before requesting cancellation."
                                .formatted(id),
                        null);
                default -> new CancellationToolCallingAdvisor.CancellationResult(
                        "The order %s with status %s cannot be cancelled. No cancellation has been prepared."
                                .formatted(id, status),
                        null);
            };
        }

        var details = orderDeliveranceTool.getDeliveryDetails(id);
        if (details == null) {
            details = "No additional delivery details are available.";
        }
        var confirmation = new CancellationConfirmation(UUID.randomUUID().toString(), id, status.name(), details);
        pendingCancellations.put(sessionId, confirmation);
        return new CancellationToolCallingAdvisor.CancellationResult(
                "The order %s is not delivered. Please verify the details below, then choose \"Confirm Cancellation\" if you wish to proceed."
                        .formatted(id),
                confirmation);
    }

    public AgentResponse confirmCancellation(String sessionId, String confirmationId) {
        var pending = pendingCancellations.get(sessionId);
        if (pending == null
                || !pending.confirmationId().equals(confirmationId)
                || !pendingCancellations.remove(sessionId, pending)) {
            return new AgentResponse(
                    "This confirmation request has expired. Please submit your cancellation request again.",
                    sessionId,
                    List.of(),
                    CONFIRMATION_EXPIRED);
        }

        toolExecutionRecorder.start();
        try {
            var status = orderStatusTool.getOrderStatus(pending.orderId());
            final String answer;
            if (CANCELLABLE_STATUS.contains(status)) {
                var result = orderCancelationTool.cancelOrder(pending.orderId());
                answer = "Result of canceling order %s:%s.".formatted(pending.orderId(), result);
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
            return new AgentResponse(answer, sessionId, toolExecutionRecorder.stop(), "completed");
        } catch (RuntimeException e) {
            toolExecutionRecorder.stop();
            throw e;
        }
    }

    public AgentResponse declineCancellation(String sessionId, String confirmationId) {
        var pending = pendingCancellations.get(sessionId);
        if (pending == null
                || !pending.confirmationId().equals(confirmationId)
                || !pendingCancellations.remove(sessionId, pending)) {
            return new AgentResponse(
                    "This confirmation request has expired. Please submit your cancellation request again.",
                    sessionId,
                    List.of(),
                    CONFIRMATION_EXPIRED);
        }

        var answer = "The cancellation of order %s has been declined.".formatted(pending.orderId());
        chatMemory.add(sessionId, new UserMessage("I do not wish to cancel order %s.".formatted(pending.orderId())));
        chatMemory.add(sessionId, new AssistantMessage(answer));
        return new AgentResponse(answer, sessionId, List.of(), "completed");
    }

    /**
     * Clear agent session.
     * Removes conversation history for the given session ID.
     */
    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
        activeSessions.remove(sessionId);
        pendingCancellations.remove(sessionId);
        log.info("Cleared session: {}", sessionId);
    }
}
