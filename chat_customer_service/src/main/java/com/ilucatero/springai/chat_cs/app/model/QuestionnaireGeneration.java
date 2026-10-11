package com.ilucatero.springai.chat_cs.app.model;

import java.util.List;

/**
 * Represents the AI's assessment of whether there is enough context to create a
 * questionnaire, along with either a clarification question or a draft.
 *
 * @param contextSufficient     indicates if there is enough context to create a
 *                              questionnaire
 * @param clarificationQuestion a question to clarify the context if it is
 *                              insufficient
 * @param title                 the title of the questionnaire, if context is
 *                              sufficient
 * @param questions             the list of questions for the questionnaire, if
 *                              context is sufficient
 */
public record QuestionnaireGeneration(
        Boolean contextSufficient,
        String clarificationQuestion,
        String title,
        List<String> questions) {
}
