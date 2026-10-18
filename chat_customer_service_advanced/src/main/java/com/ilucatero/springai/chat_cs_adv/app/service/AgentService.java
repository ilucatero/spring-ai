package com.ilucatero.springai.chat_cs_adv.app.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;
import com.ilucatero.springai.chat_cs_adv.app.model.ToolExecutionInfo;
import com.ilucatero.springai.chat_cs_adv.app.tool.TemperatureTool;
import com.ilucatero.springai.chat_cs_adv.app.tool.WeatherTool;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    private final ToolExecutionInfo.Recorder toolExecutionRecorder;
    private final WeatherTool weatherTool;
    private final TemperatureTool temperatureTool;

    private final Set<String> activeSessions = ConcurrentHashMap.newKeySet();


    /**
     * Constructor with auto-wired ChatClient.Builder.
     * Tool instances are created here and passed to ChatClient at call time.
     */
    public AgentService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.chatMemory = chatMemory;

        this.toolExecutionRecorder = new ToolExecutionInfo.Recorder();
        this.weatherTool = new WeatherTool(toolExecutionRecorder);
        this.temperatureTool = new TemperatureTool(toolExecutionRecorder);

        log.info("Agent service initialized with Spring AI ChatClient");
    }

    /**
     * Create a new agent session.
     * Returns a new UUID used to track conversation history.
     */
    public String createAgentSession() {
        var sessionId = UUID.randomUUID().toString();
        activeSessions.add(sessionId);
        log.info("Created new agent session: {}", sessionId);
        return sessionId;
    }

    /**
     * Execute an agent task using ChatClient with automatic tool orchestration.
     * The ChatClient handles the tool calling loop: when the model wants to call a
     * tool,
     * Spring AI executes the @Tool method and feeds the result back to the model.
     */
    public AgentResponse executeTask(AgentRequest request) {
        log.info("Executing agent task: {}", request.message());

        final var sessionId = request.sessionId() == null ? createAgentSession() : request.sessionId();

        try {
            activeSessions.add(sessionId);
            toolExecutionRecorder.start();

            // Add user message to memory
            chatMemory.add(sessionId, new UserMessage(request.message()));

            // Get conversation history from ChatMemory
            var messages = chatMemory.get(sessionId);

            // Call via ChatClient with tools — Spring AI handles tool execution loop
            var answer = chatClient.prompt()
                    .messages(messages)
                    .tools(weatherTool, temperatureTool)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .content();

            // Add AI response to memory
            chatMemory.add(sessionId, new AssistantMessage(answer));
            var toolExecutions = toolExecutionRecorder.stop();

            log.info("Agent completed task successfully with {} tool execution(s)", toolExecutions.size());

            return new AgentResponse(answer, sessionId, toolExecutions, "completed");

        } catch (Exception e) {
            var toolExecutions = toolExecutionRecorder.stop();
            log.error("Agent task execution failed", e);
            return new AgentResponse(
                    "I encountered an error: " + e.getMessage(),
                    sessionId,
                    toolExecutions,
                    "failed");
        }
    }

    /**
     * Clear agent session.
     * Removes conversation history for the given session ID.
     */
    public void clearSession(String sessionId) {
        chatMemory.clear(sessionId);
        activeSessions.remove(sessionId);
        log.info("Cleared session: {}", sessionId);
    }
}
