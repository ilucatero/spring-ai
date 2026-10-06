package com.ilucatero.springai.chat.app.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Set<String> activeConversations = ConcurrentHashMap.newKeySet();

    public ConversationService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String startConversation() {
        var conversationId = UUID.randomUUID().toString();
        activeConversations.add(conversationId);
        return conversationId;
    }

    public String chat(String conversationId, String message) {
        activeConversations.add(conversationId);

        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public List<Message> getHistory(String conversationId) {
        return chatMemory.get(conversationId);
    }

    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        activeConversations.remove(conversationId);
    }

    public boolean conversationExists(String conversationId) {
        return activeConversations.contains(conversationId);
    }
}