package com.ilucatero.springai.chat_cs.app.model;

import java.util.List;

/**
 * Represents the analysis of a questionnaire, including its title and the list
 * of questions.
 *
 * @param title     the title of the questionnaire
 * @param questions the list of questions in the questionnaire
 */
public record QuestionnaireAnalysis(String title, List<String> questions) {
}
