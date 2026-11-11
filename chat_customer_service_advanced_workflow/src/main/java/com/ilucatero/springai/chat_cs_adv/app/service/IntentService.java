package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.UserIntent;
import com.ilucatero.springai.chat_cs_adv.app.repository.SystemPromptRepository;

@Service
public class IntentService {

    private final ChatClient chatClient;

    public IntentService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public UserIntent detectIntent(String message, List<Message> messages) {
        return chatClient.prompt()
                .system(SystemPromptRepository.getUserIntentPrompt())
                .user(message)
                .messages(messages)
                .call()
                .entity(UserIntent.class);
    }
}
