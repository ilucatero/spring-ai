package com.ilucatero.springai.chat_cs.app.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs.app.models.QuestionnaireAnalysis;
import com.ilucatero.springai.chat_cs.app.models.QuestionnaireGeneration;
import com.ilucatero.springai.chat_cs.app.models.QuestionnaireReply;
import com.ilucatero.springai.chat_cs.app.models.QuestionnaireReply.Status;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationFlowRepository;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationFlowRepository.Stage;
import com.ilucatero.springai.chat_cs.app.repositories.ConversationRepository;
import com.ilucatero.springai.chat_cs.app.repositories.PendingQuestionnairesRepository;

@Service
public class ConversationService {

    public static final String WELCOME_MESSAGE = "Hello, share your customer context so I can suggest a list of questions to add to your questionnaire.";
    public static final String MODE_SELECTION_PROMPT = "Would you like me to generate a questionnaire, or would you prefer to enter it manually?";
    public static final String MANUAL_QUESTIONNAIRE_PROMPT = """
            Enter the questionnaire title on the first line, then list each question on its own line.
            Start each question with a number (for example, 1.), an asterisk (*), or a hyphen (-).
            """;
    private static final String MANUAL_FORMAT_ERROR = """
            I couldn't read that questionnaire. Please enter a title on the first line, followed by
            at least one question on its own line starting with a number (1.), an asterisk (*), or
            a hyphen (-).
            """;
    private static final String DEFAULT_CLARIFICATION_QUESTION = "Could you share more details about the customer context and what you want the questionnaire to learn?";

    private static final String SYS_PROMPT_CREATE_QUESTIONNAIRE_TEMPLATE = """
            You are an assistant that helps create questionnaires from customer context.
            First decide whether the conversation contains enough specific information to propose
            useful questionnaire questions, including the subject or goal of the questionnaire.
            Use earlier conversation messages together with the latest user message.
            If the context is missing, vague, or insufficient, set "contextSufficient" to false,
            provide one concise, specific question in "clarificationQuestion", and set "title" to
            an empty string and "questions" to an empty array. Never put a clarification question
            in the questionnaire questions and never invent a questionnaire when context is
            insufficient.
            If the context is sufficient, set "contextSufficient" to true, set
            "clarificationQuestion" to an empty string, provide a concise descriptive "title",
            and put up to 5 useful questionnaire questions in "questions".
            Always return all four fields: "contextSufficient", "clarificationQuestion", "title",
            and "questions". Return only the structured response, with no prose or markdown.
            """;

    private static final String USR_REGENERATE_INSTRUCTION_TEMPLATE = """
            Regenerate the proposed questionnaire using the same customer context.
            Make meaningful improvements to this draft, including its title: %s
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final ConversationRepository<String> conversationRepository;
    private final PendingQuestionnairesRepository pendingQuestionnaires;
    private final ConversationFlowRepository conversationFlow;
    private final FluxQuestionnaireService questionnaireService;

    public ConversationService(ChatClient.Builder chatClientBuilder,
            ChatMemory chatMemory,
            ConversationRepository<String> conversationRepository,
            PendingQuestionnairesRepository pendingQuestionnaires,
            ConversationFlowRepository conversationFlow,
            FluxQuestionnaireService questionnaireService) {

        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;
        this.conversationRepository = conversationRepository;
        this.pendingQuestionnaires = pendingQuestionnaires;
        this.conversationFlow = conversationFlow;
        this.questionnaireService = questionnaireService;
    }

    /**
     * Processes a chat message or decision for the given conversation ID.
     *
     * @param mode           the mode ("GENERATE" or "MANUAL") if selecting a mode
     * @param conversationId the conversation ID
     * @param decision       the decision ("APPROVE" or "REGENERATE") if making a
     *                       decision
     * @param message        the chat message if sending a message
     * @return a {@link QuestionnaireReply} containing the status, message, and
     *         questionnaire analysis
     */
    public QuestionnaireReply processChatFlux(String mode, String conversationId, String decision, String message) {
        if (mode != null) {
            return selectMode(conversationId, mode);
        } else if (decision != null) {
            return decide(conversationId, decision);
        }
        // If neither mode nor decision is provided, treat it as a chat message with the
        // generation mode. This is the default behavior for chat messages.
        return chat(conversationId, message);
    }

    /**
     * Starts a new conversation and returns the conversation ID.
     *
     * @return the conversation ID
     */
    public String startConversation() {
        var conversationId = UUID.randomUUID().toString();
        conversationRepository.save(conversationId);
        chatMemory.add(conversationId, List.of(new AssistantMessage(WELCOME_MESSAGE)));
        conversationFlow.setStage(conversationId, Stage.AWAITING_MODE_SELECTION);
        return conversationId;
    }

    public QuestionnaireReply selectMode(String conversationId, String mode) {
        var stage = conversationFlow.getStage(conversationId);
        if (stage != Stage.AWAITING_MODE_SELECTION && stage != Stage.IDLE) {
            throw new IllegalStateException(
                    "Choose whether to generate the questionnaire or enter it manually first");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must be GENERATE or MANUAL");
        }

        return switch (mode.trim().toUpperCase()) {
            case "GENERATE" -> {
                conversationFlow.setStage(conversationId, Stage.GENERATING_QUESTIONNAIRE);
                var prompt = "Generate a questionnaire";
                chatMemory.add(conversationId, List.of(
                        new UserMessage(prompt),
                        new AssistantMessage("Please share the customer context for the questionnaire.")));
                yield new QuestionnaireReply(Status.NEEDS_CONTEXT,
                        "Please share the customer context for the questionnaire.", null);
            }
            case "MANUAL" -> {
                conversationFlow.setStage(conversationId, Stage.ENTERING_MANUAL_QUESTIONNAIRE);
                chatMemory.add(conversationId, List.of(
                        new UserMessage("Enter a questionnaire manually"),
                        new AssistantMessage(MANUAL_QUESTIONNAIRE_PROMPT)));
                yield new QuestionnaireReply(Status.PROVIDE_MANUAL_QUESTIONNAIRE,
                        MANUAL_QUESTIONNAIRE_PROMPT, null);
            }
            default -> throw new IllegalArgumentException("mode must be GENERATE or MANUAL");
        };
    }

    /**
     * Processes a chat message for the given conversation ID.
     *
     * @param conversationId the conversation ID
     * @param message        the chat message
     * @return a {@link QuestionnaireReply} containing the status, message, and
     *         questionnaire analysis
     */
    public QuestionnaireReply chat(String conversationId, String message) {
        var stage = conversationFlow.getStage(conversationId);
        if (stage == null) {
            throw new IllegalStateException("Start a conversation before sending a message");
        }
        if (stage == Stage.AWAITING_MODE_SELECTION) {
            throw new IllegalStateException("Choose whether to generate the questionnaire or enter it manually first");
        }
        if (stage == Stage.AWAITING_APPROVAL || pendingQuestionnaires.get(conversationId) != null) {
            throw new IllegalStateException("Approve or regenerate the pending questionnaire first");
        }
        if (stage == Stage.ENTERING_MANUAL_QUESTIONNAIRE) {
            return createManualQuestionnaire(conversationId, message);
        }
        if (stage != Stage.GENERATING_QUESTIONNAIRE) {
            throw new IllegalStateException("Conversation is not ready to receive a message");
        }

        var generation = generateQuestionnaire(conversationId, message);
        if (!hasEnoughContext(generation)) {
            return needsContext(generation);
        }
        var questionnaire = toQuestionnaire(generation);
        pendingQuestionnaires.save(conversationId, questionnaire);
        conversationFlow.setStage(conversationId, Stage.AWAITING_APPROVAL);
        return awaitingApproval(questionnaire);
    }

    private QuestionnaireReply createManualQuestionnaire(String conversationId, String input) {

        QuestionnaireAnalysis questionnaire;
        try {
            var lines = questionnaireService.createManualQuestionnaire(input);
            // add title as the first element and questions as the rest of the list
            questionnaire = new QuestionnaireAnalysis(lines.get(0), List.copyOf(lines.subList(1, lines.size())));
        } catch (IllegalArgumentException e) {
            recordManualInput(conversationId, input, MANUAL_FORMAT_ERROR);
            return new QuestionnaireReply(Status.MANUAL_FORMAT_ERROR, MANUAL_FORMAT_ERROR, null);
        }

        pendingQuestionnaires.save(conversationId, questionnaire);
        conversationFlow.setStage(conversationId, Stage.AWAITING_APPROVAL);
        var reply = awaitingApproval(questionnaire);
        recordManualInput(conversationId, input, "Your manual questionnaire is ready for approval.");
        return reply;
    }

    private void recordManualInput(String conversationId, String input, String response) {
        chatMemory.add(conversationId, List.of(
                new UserMessage(input),
                new AssistantMessage(response)));
    }

    /**
     * Processes a decision (approve or regenerate) for the given conversation ID.
     *
     * @param conversationId the conversation ID
     * @param decision       the decision ("APPROVE" or "REGENERATE")
     * @return a {@link QuestionnaireReply} containing the status, message, and
     *         questionnaire analysis
     */
    public QuestionnaireReply decide(String conversationId, String decision) {
        var pendingQuestionnaire = pendingQuestionnaires.get(conversationId);
        if (pendingQuestionnaire == null) {
            throw new IllegalStateException("There is no questionnaire awaiting a decision");
        }

        return switch (decision.trim().toUpperCase()) {
            case "APPROVE" -> {
                chatMemory.add(conversationId, List.of(
                        new UserMessage("Approve questionnaire"),
                        new AssistantMessage("Questionnaire created successfully."),
                        new AssistantMessage(MODE_SELECTION_PROMPT)));
                pendingQuestionnaires.delete(conversationId);
                conversationFlow.setStage(conversationId, Stage.IDLE);
                yield new QuestionnaireReply(Status.CREATED, "Questionnaire created successfully.",
                        pendingQuestionnaire);
            }
            case "REGENERATE" -> {
                var instruction = USR_REGENERATE_INSTRUCTION_TEMPLATE.formatted(pendingQuestionnaire);
                var generation = generateQuestionnaire(conversationId, instruction);
                if (!hasEnoughContext(generation)) {
                    pendingQuestionnaires.delete(conversationId);
                    conversationFlow.setStage(conversationId, Stage.GENERATING_QUESTIONNAIRE);

                    yield needsContext(generation);
                }

                var newQuestionnaire = toQuestionnaire(
                        generation);
                pendingQuestionnaires.save(conversationId, newQuestionnaire);
                conversationFlow.setStage(conversationId, Stage.AWAITING_APPROVAL);

                yield awaitingApproval(newQuestionnaire);
            }
            default -> throw new IllegalArgumentException("decision must be APPROVE or REGENERATE");

        };
    }

    /**
     * Checks if there is a pending questionnaire awaiting a decision for the given
     * conversation ID.
     *
     * @param conversationId the conversation ID
     * @return true if there is a pending questionnaire, false otherwise
     */
    public boolean isAwaitingDecision(String conversationId) {
        return pendingQuestionnaires.get(conversationId) != null;
    }

    public Stage getStage(String conversationId) {
        return conversationFlow.getStage(conversationId);
    }

    private QuestionnaireGeneration generateQuestionnaire(String conversationId, String message) {
        var generation = chatClient.prompt()
                .system(SYS_PROMPT_CREATE_QUESTIONNAIRE_TEMPLATE)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(QuestionnaireGeneration.class);
        if (generation == null || generation.contextSufficient() == null) {
            throw new IllegalStateException("AI response did not indicate whether the context is sufficient");
        }
        return generation;
    }

    private boolean hasEnoughContext(QuestionnaireGeneration generation) {
        return generation.contextSufficient();
    }

    private QuestionnaireAnalysis toQuestionnaire(QuestionnaireGeneration generation) {
        if (generation.title() == null || generation.title().isBlank()
                || generation.questions() == null || generation.questions().isEmpty()) {
            throw new IllegalStateException(
                    "AI response marked the context sufficient but did not provide a questionnaire");
        }
        return new QuestionnaireAnalysis(generation.title(), generation.questions());
    }

    private QuestionnaireReply needsContext(QuestionnaireGeneration generation) {
        var clarification = generation.clarificationQuestion();
        if (clarification == null || clarification.isBlank()) {
            clarification = DEFAULT_CLARIFICATION_QUESTION;
        }
        return new QuestionnaireReply(Status.NEEDS_CONTEXT, clarification, null);
    }

    private QuestionnaireReply awaitingApproval(QuestionnaireAnalysis questionnaire) {
        return new QuestionnaireReply(Status.AWAITING_APPROVAL,
                "Does this questionnaire work for you? Choose Approve or Regenerate.",
                questionnaire);
    }

    public List<Message> getHistory(String conversationId) {
        return chatMemory.get(conversationId);
    }

    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        conversationRepository.delete(conversationId);
        pendingQuestionnaires.delete(conversationId);
        conversationFlow.delete(conversationId);
    }

    public boolean conversationExists(String conversationId) {
        return conversationRepository.findAll().contains(conversationId);
    }
}
