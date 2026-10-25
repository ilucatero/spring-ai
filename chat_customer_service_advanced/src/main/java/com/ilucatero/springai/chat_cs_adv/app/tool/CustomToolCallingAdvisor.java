package com.ilucatero.springai.chat_cs_adv.app.tool;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;

import com.ilucatero.springai.chat_cs_adv.app.model.OrderUserActionConfirmation;

public final class CustomToolCallingAdvisor extends ToolCallingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CustomToolCallingAdvisor.class);

    private final Predicate<? super AssistantMessage.ToolCall> toolFilterPredicate;
    private final Function<String, OrderUserActionResult> userActionHandler;

    private OrderUserActionConfirmation pendingOrderUserAction;

    public CustomToolCallingAdvisor(ToolCallingManager toolCallingManager,
            Predicate<? super AssistantMessage.ToolCall> toolFilterPredicate,
            Function<String, OrderUserActionResult> userActionHandler) {

        super(toolCallingManager,
                chatResponse -> chatResponse != null && chatResponse.hasToolCalls(),
                ToolCallingAdvisor.DEFAULT_ORDER,
                true);

        this.toolFilterPredicate = toolFilterPredicate;
        this.userActionHandler = userActionHandler;
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        var chatResponse = chatClientResponse.chatResponse();
        if (chatResponse == null || !chatResponse.hasToolCalls()) {
            return chatClientResponse;
        }

        // Look for a tool call in the chat response
        var filteredToolCall = chatResponse.getResults()
                .stream()
                .filter(generation -> generation.getOutput().hasToolCalls())
                .flatMap(generation -> generation.getOutput().getToolCalls().stream())
                .filter(this.toolFilterPredicate)
                .findFirst();
        if (filteredToolCall.isEmpty()) {
            return chatClientResponse;
        }

        // Process the tool call and generate a response
        var result = handleUserAction(filteredToolCall.get().arguments());

        // log penging user action
        pendingOrderUserAction = result.pendingOrderUserAction();
        log.info("Pending user action: {}",
                (pendingOrderUserAction != null ? pendingOrderUserAction : "None"));

        // Create a new chat response with the answer from the UserAction result
        var interceptedResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(result.answer()))));
        return chatClientResponse.mutate().chatResponse(interceptedResponse).build();
    }

    private OrderUserActionResult handleUserAction(String arguments) {
        try {
            return userActionHandler.apply(arguments);

        } catch (RuntimeException e) {
            log.warn("An error occured during userAction execution");
            return new OrderUserActionResult(
                    "UserAction couldn't be executed correctly",
                    null);
        }
    }

    public OrderUserActionConfirmation pendingOrderUserAction() {
        return pendingOrderUserAction;
    }

    public record OrderUserActionResult(String answer, OrderUserActionConfirmation pendingOrderUserAction) {
    }
}
