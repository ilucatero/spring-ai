package com.ilucatero.springai.chat_cs_adv.app.support;

import java.util.ArrayDeque;
import java.util.Queue;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

public class ScriptedChatModel implements ChatModel {

    private final Queue<ChatResponse> responses = new ArrayDeque<>();

    public ScriptedChatModel(ChatResponse... responses) {
        this.responses.addAll(java.util.List.of(responses));
    }

    @Override
    public ChatOptions getOptions() {
        // To simulate correctly a tool calling, ChatModel must expose
        // ToolCallingChatOptions throug getOptions()
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (responses.isEmpty()) {
            throw new IllegalStateException("No scripted response available");
        }

        var response = responses.remove();

        return response;
    }
}
