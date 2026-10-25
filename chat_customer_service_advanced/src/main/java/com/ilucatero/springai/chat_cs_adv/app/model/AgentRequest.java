package com.ilucatero.springai.chat_cs_adv.app.model;

public record AgentRequest(String message, String sessionId) {

    public AgentRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or blank");
        }
    }

    public static AgentRequest of(String message) {
        return new AgentRequest(message, null);
    }
}
