package com.ilucatero.springai.chat_cs.app.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ilucatero.springai.chat_cs.app.services.ConversationService;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Starts a new conversation and returns the conversation ID along with the
     * welcome message.
     *
     * @return ResponseEntity containing the conversation ID and welcome message
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startConversation() {
        try {
            var conversationId = conversationService.startConversation();
            return ResponseEntity.ok(Map.of(
                    "conversationId", conversationId,
                    "message", conversationService.getHistory(conversationId).get(0).getText(),
                    "choicePrompt", ConversationService.MODE_SELECTION_PROMPT,
                    "stage", conversationService.getStage(conversationId).name()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Handles chat messages and decisions (approve or regenerate) for a given
     * conversation ID.
     *
     * @param body a map containing the conversationId, message, and/or decision
     * @return ResponseEntity containing the conversation ID, answer, status, and
     *         message
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        var conversationId = body.get("conversationId");
        var decision = body.get("decision");
        var mode = body.get("mode");
        var message = body.getOrDefault("message", "");

        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "conversationId is required"));
        }

        if ((decision != null ? 1 : 0) + (mode != null ? 1 : 0) > 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only one of decision or mode may be provided"));
        }

        if (decision == null && mode == null && message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message, decision, or mode is required"));
        }

        try {
            var reply = conversationService.processChatFlux(mode, conversationId, decision, message);

            var response = new LinkedHashMap<String, Object>();
            response.put("conversationId", conversationId);
            response.put("status", reply.status());
            response.put("message", reply.message());
            if (reply.questionnaire() != null) {
                response.put("answer", reply.questionnaire());
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "conversationId", conversationId,
                            "error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Retrieves the chat history for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return ResponseEntity containing the conversation ID, message count,
     *         messages, and awaiting decision status
     */
    @GetMapping("/{conversationId}/history")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String conversationId) {
        try {
            if (!conversationService.conversationExists(conversationId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Conversation not found"));
            }

            var history = conversationService.getHistory(conversationId);
            return ResponseEntity.ok(Map.of(
                    "conversationId", conversationId,
                    "messageCount", history.size(),
                    "messages", history,
                    "awaitingDecision", conversationService.isAwaitingDecision(conversationId),
                    "stage", conversationService.getStage(conversationId).name()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Clears the chat history for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return ResponseEntity containing a success message and the conversation ID
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            conversationService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of(
                    "message", "Conversation cleared successfully",
                    "conversationId", conversationId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

}
