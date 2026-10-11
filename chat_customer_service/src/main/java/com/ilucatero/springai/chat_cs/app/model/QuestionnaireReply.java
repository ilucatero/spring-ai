package com.ilucatero.springai.chat_cs.app.model;

/**
 * Represents a reply to a questionnaire request, including its status, message,
 * and the associated questionnaire analysis.
 * @param status        the status of the questionnaire reply
 * @param message       a message providing additional information about the reply
 * @param questionnaire the analysis of the questionnaire, if applicable
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
