package com.ilucatero.springai.chat_cs.app.model;

import java.util.List;

/**
 * Represents the AI analysis of a list of questions to be added to a
 * formulaire.
 */
public record QuestionnaireAnalysis(String title, List<String> questions) {
}
