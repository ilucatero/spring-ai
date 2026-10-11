package com.ilucatero.springai.chat_cs.app.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ilucatero.springai.chat_cs.app.repository.QuestionnaireRepository;

class FluxQuestionnaireServiceTest {

    private final FluxQuestionnaireService questionnaireService = new FluxQuestionnaireService(
            new QuestionnaireRepository());

    @Test
    void parsesTitleAndNumberedQuestions() {
        var questionnaire = questionnaireService.extractQuestionnaireLines(
                " Customer feedback \n 1. How was your experience? \n 2) What should we improve? ");

        assertThat(questionnaire).containsExactly(
                "Customer feedback",
                "How was your experience?",
                "What should we improve?");
    }

    @Test
    void parsesAsteriskAndHyphenQuestions() {
        var questionnaire = questionnaireService.extractQuestionnaireLines(
                "Customer feedback\n* How was your experience?\n- What should we improve?");

        assertThat(questionnaire).containsExactly(
                "Customer feedback",
                "How was your experience?",
                "What should we improve?");
    }

    @Test
    void truncatesTitleAndQuestionToFiveHundredCharacters() {
        var title = "t".repeat(510);
        var question = "q".repeat(510);

        var questionnaire = questionnaireService.extractQuestionnaireLines(
                title + "\n- " + question);

        assertThat(questionnaire).hasSize(2);
        assertThat(questionnaire.get(0)).hasSize(500);
        assertThat(questionnaire.get(1)).hasSize(500);
    }

    @Test
    void rejectsMissingTitleOrQuestions() {
        assertThatThrownBy(() -> questionnaireService.extractQuestionnaireLines(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first line should be the title");
        assertThatThrownBy(() -> questionnaireService.extractQuestionnaireLines("Customer feedback"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first line should be the title");
    }

    @Test
    void rejectsQuestionWithoutAValidMarker() {
        assertThatThrownBy(() -> questionnaireService.extractQuestionnaireLines(
                "Customer feedback\nHow was your experience?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each question should start with a bullet point or number");
    }
}
