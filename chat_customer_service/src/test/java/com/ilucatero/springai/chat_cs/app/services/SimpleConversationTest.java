package com.ilucatero.springai.chat_cs.app.services;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.ilucatero.springai.chat_cs.app.models.QuestionnaireAnalysis;
import com.ilucatero.springai.chat_cs.app.models.QuestionnaireReply.Status;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationFlowRepository;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationRepository;
import com.ilucatero.springai.chat_cs.app.repositories.PendingQuestionnairesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Conversation service")
class SimpleConversationTest {

    private static final QuestionnaireAnalysis FIRST_QUESTIONNAIRE = new QuestionnaireAnalysis(
            "Customer satisfaction",
            List.of("How satisfied are you with our service?", "What could we improve?"));
    private static final QuestionnaireAnalysis REGENERATED_QUESTIONNAIRE = new QuestionnaireAnalysis(
            "Customer experience",
            List.of("How would you rate your experience?", "Which part should we improve?"));

    private ConversationService conversationService;

    @Mock
    private OpenAiChatModel mockChatModel;

    private static ChatResponse mockChatResponse(QuestionnaireAnalysis questionnaire) {
        var questions = questionnaire.questions().stream()
                .map(question -> "\"" + question + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        var json = "{\"contextSufficient\":true,\"clarificationQuestion\":\"\",\"title\":\""
                + questionnaire.title() + "\",\"questions\":[" + questions + "]}";
        return mockChatResponse(json);
    }

    private static ChatResponse mockClarificationResponse(String question) {
        var json = "{\"contextSufficient\":false,\"clarificationQuestion\":\"" + question
                + "\",\"title\":\"\",\"questions\":[]}";
        return mockChatResponse(json);
    }

    private static ChatResponse mockChatResponse(String json) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
    }

    private List<String> historyTexts(String conversationId) {
        var texts = new ArrayList<String>();
        for (Message message : conversationService.getHistory(conversationId)) {
            texts.add(message.getText());
        }
        return texts;
    }

    @BeforeEach
    void setUp() {
        lenient().when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(mockChatResponse(FIRST_QUESTIONNAIRE));
        lenient().when(mockChatModel.getOptions())
                .thenReturn(OpenAiChatOptions.builder().build());

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        conversationService = new ConversationService(
                ChatClient.builder(mockChatModel),
                chatMemory,
                new ConversationRepository<>(),
                new PendingQuestionnairesRepository(),
                new ConversationFlowRepository(),
                new FluxQuestionnaireService());
    }

    private String startGenerationConversation() {
        var conversationId = conversationService.startConversation();
        conversationService.selectMode(conversationId, "GENERATE");
        return conversationId;
    }

    @Test
    @DisplayName("starts a conversation with a welcome message and unique ID")
    void startsConversation() {
        var firstId = conversationService.startConversation();
        var secondId = conversationService.startConversation();

        assertThat(firstId).isNotBlank().isNotEqualTo(secondId);
        assertThat(conversationService.conversationExists(firstId)).isTrue();
        assertThat(historyTexts(firstId))
                .containsExactly(ConversationService.WELCOME_MESSAGE);
        assertThat(conversationService.isAwaitingDecision(firstId)).isFalse();
        assertThat(conversationService.getStage(firstId))
                .isEqualTo(ConversationFlowRepository.Stage.AWAITING_MODE_SELECTION);
        assertThatThrownBy(() -> conversationService.chat(firstId, "Create a questionnaire."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Choose whether to generate the questionnaire or enter it manually first");
    }

    @Test
    @DisplayName("blocks for a choice and then asks for context in generation mode")
    void requiresModeSelectionBeforeGenerating() {
        var conversationId = conversationService.startConversation();

        var reply = conversationService.selectMode(conversationId, " generate ");

        assertThat(reply.status()).isEqualTo(Status.NEEDS_CONTEXT);
        assertThat(reply.message()).contains("customer context");
        assertThat(conversationService.getStage(conversationId))
                .isEqualTo(ConversationFlowRepository.Stage.GENERATING_QUESTIONNAIRE);
    }

    @Test
    @DisplayName("requests a title and marked questions when manual entry is selected")
    void asksForManualQuestionnaire() {
        var conversationId = conversationService.startConversation();

        var reply = conversationService.selectMode(conversationId, "MANUAL");

        assertThat(reply.status()).isEqualTo(Status.PROVIDE_MANUAL_QUESTIONNAIRE);
        assertThat(reply.message()).contains("title on the first line");
        assertThat(conversationService.getStage(conversationId))
                .isEqualTo(ConversationFlowRepository.Stage.ENTERING_MANUAL_QUESTIONNAIRE);
    }

    @Test
    @DisplayName("parses numbered manual questions and waits for approval")
    void acceptsNumberedManualQuestionnaire() {
        var conversationId = conversationService.startConversation();
        conversationService.selectMode(conversationId, "MANUAL");

        var reply = conversationService.chat(conversationId,
                "Customer feedback\n1. How satisfied are you?\n2) What should we improve?");

        assertThat(reply.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(reply.questionnaire()).isEqualTo(new QuestionnaireAnalysis(
                "Customer feedback",
                List.of("How satisfied are you?", "What should we improve?")));
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
    }

    @Test
    @DisplayName("parses asterisk and hyphen manual question markers")
    void acceptsBulletedManualQuestionnaire() {
        var conversationId = conversationService.startConversation();
        conversationService.selectMode(conversationId, "MANUAL");

        var reply = conversationService.chat(conversationId,
                "Customer feedback\n* How satisfied are you?\n- What should we improve?");

        assertThat(reply.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(reply.questionnaire().questions())
                .containsExactly("How satisfied are you?", "What should we improve?");
    }

    @Test
    @DisplayName("creates a manually entered questionnaire only after approval")
    void approvesManualQuestionnaire() {
        var conversationId = conversationService.startConversation();
        conversationService.selectMode(conversationId, "MANUAL");
        conversationService.chat(conversationId, "Customer feedback\n- How satisfied are you?");

        var reply = conversationService.decide(conversationId, "APPROVE");

        assertThat(reply.status()).isEqualTo(Status.CREATED);
        assertThat(reply.questionnaire()).isEqualTo(new QuestionnaireAnalysis(
                "Customer feedback", List.of("How satisfied are you?")));
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();
        assertThat(conversationService.getStage(conversationId))
                .isEqualTo(ConversationFlowRepository.Stage.IDLE);
    }

    @Test
    @DisplayName("keeps manual input active and rejects malformed question lists")
    void rejectsMalformedManualQuestionnaire() {
        var conversationId = conversationService.startConversation();
        conversationService.selectMode(conversationId, "MANUAL");

        var reply = conversationService.chat(conversationId,
                "Customer feedback\nHow satisfied are you?");

        assertThat(reply.status()).isEqualTo(Status.MANUAL_FORMAT_ERROR);
        assertThat(conversationService.getStage(conversationId))
                .isEqualTo(ConversationFlowRepository.Stage.ENTERING_MANUAL_QUESTIONNAIRE);
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();

        var retry = conversationService.chat(conversationId, "Customer feedback\n- How satisfied are you?");

        assertThat(retry.status()).isEqualTo(Status.AWAITING_APPROVAL);
    }

    @Test
    @DisplayName("rejects an unsupported questionnaire creation mode")
    void rejectsUnsupportedMode() {
        var conversationId = conversationService.startConversation();

        assertThatThrownBy(() -> conversationService.selectMode(conversationId, "OTHER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mode must be GENERATE or MANUAL");
    }

    @Test
    @DisplayName("returns a structured questionnaire and waits for a decision")
    void createsPendingQuestionnaire() {
        var conversationId = startGenerationConversation();

        var reply = conversationService.chat(conversationId, "We want to improve customer satisfaction.");

        assertThat(reply.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(reply.questionnaire()).isEqualTo(FIRST_QUESTIONNAIRE);
        assertThat(reply.message()).contains("Approve or Regenerate");
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
        assertThat(historyTexts(conversationId))
                .contains(ConversationService.WELCOME_MESSAGE, "We want to improve customer satisfaction.");
    }

    @Test
    @DisplayName("asks for more context without creating a pending questionnaire")
    void asksForContextWhenTheRequestIsUnclear() {
        var conversationId = startGenerationConversation();
        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(mockClarificationResponse("What type of customer experience should the survey cover?"))
                .thenReturn(mockChatResponse(FIRST_QUESTIONNAIRE));

        var clarification = conversationService.chat(conversationId, "Help me with a survey.");

        assertThat(clarification.status()).isEqualTo(Status.NEEDS_CONTEXT);
        assertThat(clarification.message()).isEqualTo("What type of customer experience should the survey cover?");
        assertThat(clarification.questionnaire()).isNull();
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();

        var questionnaire = conversationService.chat(conversationId,
                "It is a post-purchase survey for customers buying our online furniture.");

        assertThat(questionnaire.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(questionnaire.questionnaire()).isEqualTo(FIRST_QUESTIONNAIRE);
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
    }

    @Test
    @DisplayName("does not accept another message while a questionnaire is pending")
    void rejectsChatWhileDecisionIsPending() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");

        assertThatThrownBy(() -> conversationService.chat(conversationId, "Add more questions."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Approve or regenerate the pending questionnaire first");
    }

    @Test
    @DisplayName("approves a pending questionnaire and records the decision")
    void approvesQuestionnaire() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");

        var reply = conversationService.decide(conversationId, " approve ");

        assertThat(reply.status()).isEqualTo(Status.CREATED);
        assertThat(reply.questionnaire()).isEqualTo(FIRST_QUESTIONNAIRE);
        assertThat(reply.message()).isEqualTo("Questionnaire created successfully.");
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();
        assertThat(historyTexts(conversationId))
                .contains("Approve questionnaire", "Questionnaire created successfully.");
    }

    @Test
    @DisplayName("regenerates a pending questionnaire and keeps it awaiting approval")
    void regeneratesQuestionnaire() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");
        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(mockChatResponse(REGENERATED_QUESTIONNAIRE));

        var reply = conversationService.decide(conversationId, " regenerate ");

        assertThat(reply.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(reply.questionnaire()).isEqualTo(REGENERATED_QUESTIONNAIRE);
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
    }

    @Test
    @DisplayName("allows more context if a regenerated questionnaire needs clarification")
    void allowsMoreContextWhenRegenerationNeedsClarification() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");
        when(mockChatModel.call(any(Prompt.class)))
                .thenReturn(mockClarificationResponse("Which customer group should this survey address?"))
                .thenReturn(mockChatResponse(REGENERATED_QUESTIONNAIRE));

        var clarification = conversationService.decide(conversationId, "REGENERATE");

        assertThat(clarification.status()).isEqualTo(Status.NEEDS_CONTEXT);
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();

        var questionnaire = conversationService.chat(conversationId, "This survey is for new subscribers.");

        assertThat(questionnaire.status()).isEqualTo(Status.AWAITING_APPROVAL);
        assertThat(questionnaire.questionnaire()).isEqualTo(REGENERATED_QUESTIONNAIRE);
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
    }

    @Test
    @DisplayName("rejects invalid decisions without discarding the pending questionnaire")
    void rejectsInvalidDecision() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");

        assertThatThrownBy(() -> conversationService.decide(conversationId, "reject"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("decision must be APPROVE or REGENERATE");
        assertThat(conversationService.isAwaitingDecision(conversationId)).isTrue();
    }

    @Test
    @DisplayName("rejects a decision when there is no pending questionnaire")
    void rejectsDecisionWithoutPendingQuestionnaire() {
        var conversationId = startGenerationConversation();

        assertThatThrownBy(() -> conversationService.decide(conversationId, "APPROVE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("There is no questionnaire awaiting a decision");
    }

    @Test
    @DisplayName("keeps conversation histories and pending questionnaires isolated")
    void isolatesConversations() {
        var firstId = startGenerationConversation();
        var secondId = startGenerationConversation();

        conversationService.chat(firstId, "First customer context.");
        conversationService.chat(secondId, "Second customer context.");
        conversationService.decide(firstId, "APPROVE");

        assertThat(conversationService.isAwaitingDecision(firstId)).isFalse();
        assertThat(conversationService.isAwaitingDecision(secondId)).isTrue();
        assertThat(historyTexts(firstId))
                .contains("First customer context.")
                .doesNotContain("Second customer context.");
        assertThat(historyTexts(secondId))
                .contains("Second customer context.")
                .doesNotContain("First customer context.");
    }

    @Test
    @DisplayName("rejects messages for a conversation that was not started")
    void rejectsMessageForUnknownConversation() {
        var conversationId = "conversation-created-by-chat";

        assertThatThrownBy(() -> conversationService.chat(conversationId, "Create a customer survey."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Start a conversation before sending a message");
    }

    @Test
    @DisplayName("clears history, conversation registration, and pending questionnaire")
    void clearsConversation() {
        var conversationId = startGenerationConversation();
        conversationService.chat(conversationId, "Create a customer survey.");

        conversationService.clearConversation(conversationId);

        assertThat(conversationService.conversationExists(conversationId)).isFalse();
        assertThat(conversationService.isAwaitingDecision(conversationId)).isFalse();
        assertThat(conversationService.getHistory(conversationId)).isEmpty();
    }
}
