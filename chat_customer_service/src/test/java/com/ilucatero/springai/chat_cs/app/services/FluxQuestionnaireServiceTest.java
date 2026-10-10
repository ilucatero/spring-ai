package com.ilucatero.springai.chat_cs.app.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FluxQuestionnaireServiceTest {

    private final FluxQuestionnaireService questionnaireService = new FluxQuestionnaireService();

    @Test
    void parsesTitleAndNumberedQuestions() {
        var questionnaire = questionnaireService.createManualQuestionnaire(
                " Customer feedback \n 1. How was your experience? \n 2) What should we improve? ");

        assertThat(questionnaire).containsExactly(
                "Customer feedback",
                "How was your experience?",
                "What should we improve?");
    }

    @Test
    void parsesAsteriskAndHyphenQuestions() {
        var questionnaire = questionnaireService.createManualQuestionnaire(
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

        var questionnaire = questionnaireService.createManualQuestionnaire(
                title + "\n- " + question);

        assertThat(questionnaire).hasSize(2);
        assertThat(questionnaire.get(0)).hasSize(500);
        assertThat(questionnaire.get(1)).hasSize(500);
    }

    @Test
    void rejectsMissingTitleOrQuestions() {
        assertThatThrownBy(() -> questionnaireService.createManualQuestionnaire(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first line should be the title");
        assertThatThrownBy(() -> questionnaireService.createManualQuestionnaire("Customer feedback"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first line should be the title");
    }

    @Test
    void rejectsQuestionWithoutAValidMarker() {
        assertThatThrownBy(() -> questionnaireService.createManualQuestionnaire(
                "Customer feedback\nHow was your experience?"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each question should start with a bullet point or number");
    }
}
