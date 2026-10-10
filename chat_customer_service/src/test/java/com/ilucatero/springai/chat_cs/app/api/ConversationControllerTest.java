package com.ilucatero.springai.chat_cs.app.api;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.ilucatero.springai.chat_cs.app.models.QuestionnaireReply;
import com.ilucatero.springai.chat_cs.app.models.QuestionnaireReply.Status;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationFlowRepository.Stage;
import com.ilucatero.springai.chat_cs.app.services.ConversationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private ConversationService conversationService;

    @Test
    void returnsClarificationWithoutAnAnswerQuestionnaire() {
        var conversationId = "conversation-1";
        when(conversationService.processChatFlux(null, conversationId, null, "Help me with a survey."))
                .thenReturn(new QuestionnaireReply(
                        Status.NEEDS_CONTEXT,
                        "What type of customer experience should the survey cover?",
                        null));
        var controller = new ConversationController(conversationService);

        var response = controller.chat(Map.of(
                "conversationId", conversationId,
                "message", "Help me with a survey."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("conversationId", conversationId)
                .containsEntry("status", Status.NEEDS_CONTEXT)
                .containsEntry("message", "What type of customer experience should the survey cover?")
                .doesNotContainKey("answer");
    }

    @Test
    void routesQuestionnaireModeSelection() {
        var conversationId = "conversation-2";
        when(conversationService.processChatFlux("MANUAL", conversationId, null, ""))
                .thenReturn(new QuestionnaireReply(
                        Status.PROVIDE_MANUAL_QUESTIONNAIRE,
                        "Enter the title and questions.",
                        null));
        var controller = new ConversationController(conversationService);

        var response = controller.chat(Map.of(
                "conversationId", conversationId,
                "mode", "MANUAL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", Status.PROVIDE_MANUAL_QUESTIONNAIRE)
                .containsEntry("message", "Enter the title and questions.")
                .doesNotContainKey("answer");
    }

    @Test
    void routesDecisionThroughConversationFlux() {
        var conversationId = "conversation-4";
        var questionnaire = new com.ilucatero.springai.chat_cs.app.models.QuestionnaireAnalysis(
                "Customer feedback", java.util.List.of("What can we improve?"));
        when(conversationService.processChatFlux(null, conversationId, "APPROVE", ""))
                .thenReturn(new QuestionnaireReply(
                        Status.CREATED,
                        "Questionnaire created successfully.",
                        questionnaire));
        var controller = new ConversationController(conversationService);

        var response = controller.chat(Map.of(
                "conversationId", conversationId,
                "decision", "APPROVE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("status", Status.CREATED)
                .containsEntry("answer", questionnaire);
    }

    @Test
    void rejectsModeAndDecisionInOneRequest() {
        var controller = new ConversationController(conversationService);

        var response = controller.chat(Map.of(
                "conversationId", "conversation-5",
                "mode", "MANUAL",
                "decision", "APPROVE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Only one of decision or mode may be provided");
    }

    @Test
    void rejectsRequestWithoutModeDecisionOrMessage() {
        var controller = new ConversationController(conversationService);

        var response = controller.chat(Map.of("conversationId", "conversation-6"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "message, decision, or mode is required");
    }

    @Test
    void startsConversationWithTheModePrompt() {
        var conversationId = "conversation-3";
        when(conversationService.startConversation()).thenReturn(conversationId);
        when(conversationService.getHistory(conversationId)).thenReturn(java.util.List.of(
                new org.springframework.ai.chat.messages.AssistantMessage("Welcome")));
        when(conversationService.getStage(conversationId)).thenReturn(Stage.AWAITING_MODE_SELECTION);
        var controller = new ConversationController(conversationService);

        var response = controller.startConversation();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("conversationId", conversationId)
                .containsEntry("choicePrompt", ConversationService.MODE_SELECTION_PROMPT)
                .containsEntry("stage", Stage.AWAITING_MODE_SELECTION.name());
    }
}
