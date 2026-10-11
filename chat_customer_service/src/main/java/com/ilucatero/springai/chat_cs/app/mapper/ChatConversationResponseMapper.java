package com.ilucatero.springai.chat_cs.app.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ilucatero.springai.chat_cs.app.model.QuestionnaireReply;
import org.springframework.ai.chat.messages.Message;

public final class ChatConversationResponseMapper {

    private ChatConversationResponseMapper() {
        // Private constructor to prevent instantiation
    }

    public static Map<String, Object> toChatStartResponse(String conversationId, String message, String prompt,
            String stage) {
        return Map.of(
                "conversationId", conversationId,
                "message", message,
                "choicePrompt", prompt,
                "stage", stage);
    }

    public static Map<String, Object> toChatResponse(QuestionnaireReply reply, String conversationId) {
        // Implement your mapping logic here

        var response = new LinkedHashMap<String, Object>();
        response.put("conversationId", conversationId);
        response.put("status", reply.status());
        response.put("message", reply.message());
        if (reply.questionnaire() != null) {
            response.put("answer", reply.questionnaire());
        }
        return response;
    }

    // map to history response
    public static Map<String, Object> toHistoryResponse(List<Message> history, String conversationId,
            boolean awaitingDecision, String stage) {
        return Map.of(
                "conversationId", conversationId,
                "messageCount", history.size(),
                "messages", history,
                "awaitingDecision", awaitingDecision,
                "stage", stage);
    }

    public static Map<String, Object> toErrorResponse(String conversationId, String errorMessage) {
        var response = new LinkedHashMap<String, Object>();
        response.put("conversationId", conversationId);
        response.put("status", "error");
        response.put("errorMessage", errorMessage);
        return response;
    }

}
