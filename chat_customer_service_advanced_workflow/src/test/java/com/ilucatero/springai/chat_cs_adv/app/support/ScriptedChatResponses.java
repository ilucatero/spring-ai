package com.ilucatero.springai.chat_cs_adv.app.support;

import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public final class ScriptedChatResponses {

    private ScriptedChatResponses() {
    }

    public static ChatResponse toolCall(String toolName, String arguments) {
        var toolCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                toolName,
                arguments);

        var message = AssistantMessage.builder()
                .toolCalls(List.of(toolCall))
                .build();

        var metadata = ChatGenerationMetadata.builder()
                .finishReason("TOOL_CALLS")
                .build();

        return new ChatResponse(List.of(new Generation(message, metadata)));
    }

    public static ChatResponse finalAnswer(String answer) {
        var message = new AssistantMessage(answer);
        return new ChatResponse(List.of(new Generation(message)));
    }
}
