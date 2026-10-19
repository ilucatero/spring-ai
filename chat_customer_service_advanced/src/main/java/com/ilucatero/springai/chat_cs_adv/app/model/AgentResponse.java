package com.ilucatero.springai.chat_cs_adv.app.model;

import java.util.List;

public record AgentResponse(
        String answer,
        String sessionId,
        List<ToolExecutionInfo> toolExecutions,
        String status,
        CancellationConfirmation pendingCancellation) {
    public AgentResponse {
        toolExecutions = toolExecutions == null ? List.of() : List.copyOf(toolExecutions);
    }

    public AgentResponse(String answer, String sessionId, List<ToolExecutionInfo> toolExecutions, String status) {
        this(answer, sessionId, toolExecutions, status, null);
    }
}
