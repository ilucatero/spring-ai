package com.ilucatero.springai.chat_cs.app.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs.app.model.QuestionnaireAnalysis;
import com.ilucatero.springai.chat_cs.app.model.QuestionnaireReply;
import com.ilucatero.springai.chat_cs.app.model.QuestionnaireReply.Status;

@Service
public class ConversationService {

    public static final String WELCOME_MESSAGE = "Hello, share your customer context so I can suggest a list of questions to add to your questionnaire.";

    private static final String SYS_PROMPT_TEMPLATE = """
            You are an AI assistant that analyzes requests to propose a questionnaire to add to a formulaire.
            Return a structured response with a concise, descriptive "title" and a "questions"
            property containing an array of strings.
            If the request is unclear, put a concise clarification question in the array.
            Otherwise, put each suggested questionnaire question in a separate array item.
            Limit the number of questions to 5.
            Do not include any other text in your response.
            Do not put prose or markdown outside the structured response.
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final Set<String> activeConversations = ConcurrentHashMap.newKeySet();
    private final Map<String, QuestionnaireAnalysis> pendingQuestionnaires = new ConcurrentHashMap<>();

    public ConversationService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String startConversation() {
        var conversationId = UUID.randomUUID().toString();
        activeConversations.add(conversationId);
        chatMemory.add(conversationId, List.of(new AssistantMessage(WELCOME_MESSAGE)));
        return conversationId;
    }

    public QuestionnaireReply chat(String conversationId, String message) {
        if (pendingQuestionnaires.containsKey(conversationId)) {
            throw new IllegalStateException("Approve or regenerate the pending questionnaire first");
        }
        activeConversations.add(conversationId);
        var questionnaire = generateQuestionnaire(conversationId, message);
        pendingQuestionnaires.put(conversationId, questionnaire);
        return awaitingApproval(questionnaire);
    }

    public QuestionnaireReply decide(String conversationId, String decision) {
        var pendingQuestionnaire = pendingQuestionnaires.get(conversationId);
        if (pendingQuestionnaire == null) {
            throw new IllegalStateException("There is no questionnaire awaiting a decision");
        }

        return switch (decision.trim().toUpperCase()) {
            case "APPROVE" -> {
                pendingQuestionnaires.remove(conversationId);
                chatMemory.add(conversationId, List.of(
                        new UserMessage("Approve questionnaire"),
                        new AssistantMessage("Questionnaire created successfully.")));
                yield new QuestionnaireReply(Status.CREATED, "Questionnaire created successfully.",
                        pendingQuestionnaire);
            }
            case "REGENERATE" -> {
                var instruction = "Regenerate the proposed questionnaire using the same customer context. "
                        + "Make meaningful improvements to this draft, including its title: "
                        + pendingQuestionnaire;
                var newQuestionnaire = generateQuestionnaire(conversationId, instruction);
                pendingQuestionnaires.put(conversationId, newQuestionnaire);
                yield awaitingApproval(newQuestionnaire);
            }
            default -> throw new IllegalArgumentException("decision must be APPROVE or REGENERATE");
        };
    }

    public boolean isAwaitingDecision(String conversationId) {
        return pendingQuestionnaires.containsKey(conversationId);
    }

    private QuestionnaireAnalysis generateQuestionnaire(String conversationId, String message) {
        return chatClient.prompt()
                .system(SYS_PROMPT_TEMPLATE)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .entity(QuestionnaireAnalysis.class);
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
        activeConversations.remove(conversationId);
        pendingQuestionnaires.remove(conversationId);
    }

    public boolean conversationExists(String conversationId) {
        return activeConversations.contains(conversationId);
    }
}
