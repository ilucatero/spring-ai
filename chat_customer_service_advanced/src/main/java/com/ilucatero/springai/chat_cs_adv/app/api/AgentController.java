package com.ilucatero.springai.chat_cs_adv.app.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ilucatero.springai.chat_cs_adv.app.service.AgentService;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentRequest;
import com.ilucatero.springai.chat_cs_adv.app.model.AgentResponse;

@RestController
@RequestMapping("/api/conversation")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Starts a new conversation and returns the conversation ID along with the
     * welcome message.
     *
     * @return ResponseEntity containing the conversation ID and welcome message
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startConversation() {
        try {
            var sessionId = agentService.createAgentSession();
            return ResponseEntity.ok(Map.of(
                    "sessionId", sessionId,
                    "message", "Agent session started successfully"));

        } catch (Exception e) {
            log.error("Failed to start agent session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Chat with the agent (simplified conversational interface).
     * Provides input validation and security checks.
     *
     * @param request chat request with message and optional session ID
     * @return chat response with answer
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        var sessionId = request.get("sessionId");
        var message = request.get("message");

        // Input validation (as per security guidelines)
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message cannot be empty"));
        }

        // Limit prompt length (security consideration)
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", String.format("Message too long (max %d characters)", MAX_MESSAGE_LENGTH)));
        }

        try {
            var agentRequest = new AgentRequest(message, sessionId);
            var response = agentService.executeTask(agentRequest);

            return ResponseEntity.ok(responseBody(response));

        } catch (Exception e) {
            log.error("Agent chat failed", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process message"));
        }
    }

    @PostMapping("/session/{sessionId}/cancellation/confirm")
    public ResponseEntity<Map<String, Object>> confirmCancellation(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        var confirmationId = request.get("confirmationId");
        if (confirmationId == null || confirmationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Confirmation ID is required"));
        }
        return cancellationResponse(agentService.confirmCancellation(sessionId, confirmationId));
    }

    @PostMapping("/session/{sessionId}/cancellation/decline")
    public ResponseEntity<Map<String, Object>> declineCancellation(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        var confirmationId = request.get("confirmationId");
        if (confirmationId == null || confirmationId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Confirmation ID is required"));
        }
        return cancellationResponse(agentService.declineCancellation(sessionId, confirmationId));
    }

    private ResponseEntity<Map<String, Object>> cancellationResponse(AgentResponse response) {
        var body = responseBody(response);
        return AgentResponse.Status.CONFIRMATION_EXPIRED.equals(response.status())
                ? ResponseEntity.status(HttpStatus.CONFLICT).body(body)
                : ResponseEntity.ok(body);
    }

    private Map<String, Object> responseBody(AgentResponse response) {
        var body = new LinkedHashMap<String, Object>();
        body.put("sessionId", response.sessionId());
        body.put("answer", response.answer());
        body.put("toolExecutions", response.toolExecutions());
        body.put("toolsUsed", response.toolExecutions().size());
        body.put("status", response.status());
        if (response.userActionOrderConfirmation() != null) {
            body.put("pendingCancellation", response.userActionOrderConfirmation());
        }
        return body;
    }

    /**
     * Clears the chat history for a given conversation ID.
     *
     * @param sessionId the session ID
     * @return ResponseEntity containing a success message and the session ID
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String sessionId) {
        try {
            agentService.clearSession(sessionId);
            return ResponseEntity.ok(Map.of(
                    "message", "Conversation cleared successfully",
                    "sessionId", sessionId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));

        }
    }

}
