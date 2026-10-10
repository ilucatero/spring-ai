package com.ilucatero.springai.chat_cs.app.models;

/**
 * Represents a reply to a questionnaire request, including its status, message,
 * and the associated questionnaire analysis.
 */
public record QuestionnaireReply(
        Status status,
        String message,
        QuestionnaireAnalysis questionnaire) {

    public enum Status {
        PROVIDE_MANUAL_QUESTIONNAIRE,
        MANUAL_FORMAT_ERROR,
        NEEDS_CONTEXT,
        AWAITING_APPROVAL,
        CREATED
    }
}
