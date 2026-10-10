package com.ilucatero.springai.chat_cs.app.model;

public record QuestionnaireReply(
        Status status,
        String message,
        QuestionnaireAnalysis questionnaire) {

    public enum Status {
        AWAITING_APPROVAL,
        CREATED
    }
}
