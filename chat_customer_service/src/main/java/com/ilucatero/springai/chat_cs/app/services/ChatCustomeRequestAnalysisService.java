package com.ilucatero.springai.chat_cs.app.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs.app.models.CustomerRequestAnalysis;

/**
 * Service for analyzing customer requests using AI. It sends the request to
 * the AI model and retrieves the analysis, including category, priority,
 * sentiment, and a summary.
 */
@Service
public class ChatCustomeRequestAnalysisService {

    private static final String PROMPT_TEMPLATE = """
            You are an AI assistant that analyzes customer service requests.
            Analyze the request and provide its category, priority, sentiment,
            and a short, one-sentence summary. Use a concise category label;
            if no existing category fits, provide a new descriptive label.
            Return only the structured response.
            """;

    private final ChatClient chatClient;

    public ChatCustomeRequestAnalysisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Analyzes a customer request message and returns the AI-generated analysis.
     *
     * @param userMessage The customer request message to analyze.
     * @return A {@link CustomerRequestAnalysis} object containing the analysis
     *         results.
     */
    public CustomerRequestAnalysis chat(String userMessage) {
        return chatClient
                .prompt()
                .system(PROMPT_TEMPLATE)
                .user(userMessage)
                .call()
                .entity(CustomerRequestAnalysis.class);
    }

}
