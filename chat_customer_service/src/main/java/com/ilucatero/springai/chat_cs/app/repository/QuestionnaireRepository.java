package com.ilucatero.springai.chat_cs.app.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

/**
 * In-memory repository for managing questionnaires and their data (title,
 * questions, ...).
 * This repository is backed by a static map, allowing for quick access and
 * storage of questionnaires.
 */
@Repository
public class QuestionnaireRepository {

    record Questionnaire(
            String uuid,
            String title,
            List<String> questions) {
    };

    private static final Map<String, Questionnaire> QUESTIONNAIRES = new HashMap<>();

    public Questionnaire save(String title, List<String> questions) {
        var uuid = UUID.randomUUID().toString();
        var questionnaire = new Questionnaire(uuid, title, questions);
        QUESTIONNAIRES.put(uuid, questionnaire);
        return questionnaire;
    }
}
