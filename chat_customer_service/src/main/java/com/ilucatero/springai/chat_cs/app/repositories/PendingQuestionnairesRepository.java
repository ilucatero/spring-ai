package com.ilucatero.springai.chat_cs.app.repositories;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ilucatero.springai.chat_cs.app.models.QuestionnaireAnalysis;

/** In-memory repository backed by a thread-safe set. */
@Repository
public class PendingQuestionnairesRepository {

    private final Map<String, QuestionnaireAnalysis> pendingQuestionnaires = new ConcurrentHashMap<>();

    public QuestionnaireAnalysis save(String conversationId, QuestionnaireAnalysis questionnaire) {
        pendingQuestionnaires.put(conversationId, questionnaire);
        return questionnaire;
    }

    public QuestionnaireAnalysis get(String conversationId) {
        return pendingQuestionnaires.get(conversationId);
    }

    public boolean delete(String conversationId) {
        return pendingQuestionnaires.remove(conversationId) != null;
    }

    public void clear() {
        pendingQuestionnaires.clear();
    }

}
