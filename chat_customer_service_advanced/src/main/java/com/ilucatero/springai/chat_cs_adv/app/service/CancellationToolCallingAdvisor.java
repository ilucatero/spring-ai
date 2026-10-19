package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;

import com.ilucatero.springai.chat_cs_adv.app.model.CancellationConfirmation;

final class CancellationToolCallingAdvisor extends ToolCallingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CancellationToolCallingAdvisor.class);

    private static final String CANCEL_ORDER_TOOL = "cancelOrder";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Function<String, CancellationResult> cancellationHandler;

    private CancellationConfirmation pendingCancellation;

    CancellationToolCallingAdvisor(Function<String, CancellationResult> cancellationHandler) {
        super(
                ToolCallingManager.builder().build(),
                chatResponse -> chatResponse != null && chatResponse.hasToolCalls(),
                ToolCallingAdvisor.DEFAULT_ORDER,
                true);
        this.cancellationHandler = cancellationHandler;
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        var chatResponse = chatClientResponse.chatResponse();
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return chatClientResponse;
        }

        // Look for a cancelOrder tool call in the chat response
        var cancellationCall = chatResponse.getResults()
                .stream()
                .filter(generation -> generation.getOutput().hasToolCalls())
                .flatMap(generation -> generation.getOutput().getToolCalls().stream())
                .filter(toolCall -> CANCEL_ORDER_TOOL.equals(toolCall.name()))
                .findFirst();
        if (cancellationCall.isEmpty()) {
            return chatClientResponse;
        }

        // Process the cancelOrder tool call and generate a response
        var result = cancellationResult(cancellationCall.get().arguments());
        pendingCancellation = result.pendingCancellation();
        log.info("Pending cancellation: {}",
                pendingCancellation != null ? pendingCancellation : "No pending cancellation");

        // Create a new chat response with the answer from the cancellation result
        var interceptedResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(result.answer()))));
        return chatClientResponse.mutate().chatResponse(interceptedResponse).build();
    }

    private CancellationResult cancellationResult(String arguments) {
        try {
            JsonNode orderId = objectMapper.readTree(arguments).get("orderId");
            if (orderId == null || !orderId.isTextual() || orderId.asText().isBlank()) {
                return new CancellationResult(
                        "To verify if the cancellation is possible, please provide the order number.",
                        null);
            }
            return cancellationHandler.apply(orderId.asText());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Ignoring malformed cancelOrder tool arguments");
            return new CancellationResult(
                    "I couldn't identify the order number. Please specify it.",
                    null);
        }
    }

    CancellationConfirmation pendingCancellation() {
        return pendingCancellation;
    }

    record CancellationResult(String answer, CancellationConfirmation pendingCancellation) {
    }
}
