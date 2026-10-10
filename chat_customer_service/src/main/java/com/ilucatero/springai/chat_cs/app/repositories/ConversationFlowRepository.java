package com.ilucatero.springai.chat_cs.app.repositories;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class ConversationFlowRepository {

    private final Map<String, Stage> stages = new ConcurrentHashMap<>();

    public void setStage(String conversationId, Stage stage) {
        stages.put(conversationId, stage);
    }

    public Stage getStage(String conversationId) {
        return stages.get(conversationId);
    }

    public void delete(String conversationId) {
        stages.remove(conversationId);
    }

    public enum Stage {
        IDLE,
        AWAITING_MODE_SELECTION,
        GENERATING_QUESTIONNAIRE,
        ENTERING_MANUAL_QUESTIONNAIRE,
        AWAITING_APPROVAL
    }
}
