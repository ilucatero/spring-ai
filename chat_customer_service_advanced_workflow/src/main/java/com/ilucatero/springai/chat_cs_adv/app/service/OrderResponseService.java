package com.ilucatero.springai.chat_cs_adv.app.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.OrderWorkflowContext;
import com.ilucatero.springai.chat_cs_adv.app.repository.SystemPromptRepository;

@Service
public class OrderResponseService {

    private final ChatClient chatClient;

    public OrderResponseService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String generateResponse(OrderWorkflowContext context) {

        return chatClient.prompt()
                .system(SystemPromptRepository.getSystemPrompt())
                .user("""
                        Order ID: %s
                        Order status: %s
                        Delivery details: %s

                        Generate a response for the customer.
                        """.formatted(
                        context.orderId(),
                        context.orderStatus(),
                        context.deliveryDetails()))
                .call()
                .content();
    }
}
