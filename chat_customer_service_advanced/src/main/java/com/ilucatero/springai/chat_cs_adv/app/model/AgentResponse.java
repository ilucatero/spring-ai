package com.ilucatero.springai.chat_cs_adv.app.model;

import java.util.List;

public record AgentResponse(
        String answer,
        String sessionId,
        List<ToolExecutionInfo> toolExecutions,
        Status status,
        CancellationConfirmation pendingCancellation) {

    public enum Status {
        COMPLETED, CONFIRMATION_EXPIRED, FAILED
    }

    public AgentResponse {
        toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
    }

    public AgentResponse(String answer, String sessionId, List<ToolExecutionInfo> toolExecutions, Status status) {
        this(answer, sessionId, toolExecutions, status, null);
    }
}
