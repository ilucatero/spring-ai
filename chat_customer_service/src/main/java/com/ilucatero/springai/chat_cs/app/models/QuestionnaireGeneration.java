package com.ilucatero.springai.chat_cs.app.models;

import java.util.List;

/**
 * Represents the AI's assessment of whether there is enough context to create a
 * questionnaire, along with either a clarification question or a draft.
 */
public record QuestionnaireGeneration(
        Boolean contextSufficient,
        String clarificationQuestion,
        String title,
        List<String> questions) {
}
