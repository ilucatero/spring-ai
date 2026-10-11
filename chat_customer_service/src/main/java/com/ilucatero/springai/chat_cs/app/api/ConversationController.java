package com.ilucatero.springai.chat_cs.app.api;

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

import com.ilucatero.springai.chat_cs.app.mapper.ChatConversationResponseMapper;
import com.ilucatero.springai.chat_cs.app.service.ConversationService;

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
    public ResponseEntity<Map<String, Object>> startConversation() {
        try {
            var conversationId = conversationService.startConversation();
            return ResponseEntity.ok(
                    ChatConversationResponseMapper.toChatStartResponse(
                            conversationId,
                            conversationService.getHistory(conversationId).get(0).getText(),
                            ConversationService.MODE_SELECTION_PROMPT,
                            conversationService.getStage(conversationId).name()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatConversationResponseMapper.toErrorResponse(null,
                            e.getMessage() != null ? e.getMessage() : "Unknown error"));
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
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            "conversationId is required"));
        }

        if ((decision != null ? 1 : 0) + (mode != null ? 1 : 0) > 1) {
            return ResponseEntity.badRequest()
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            "Only one of decision or mode may be provided"));
        }

        if (decision == null && mode == null && message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            "message, decision, or mode is required"));
        }

        try {
            // Process the chat message or decision and return the mapped reply
            var reply = conversationService.processChatFlux(mode, conversationId, decision, message);
            return ResponseEntity.ok(ChatConversationResponseMapper.toChatResponse(reply, conversationId));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            e.getMessage() != null ? e.getMessage() : "Unknown error"));
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
        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            "conversationId is required"));
        }

        try {
            if (!conversationService.conversationExists(conversationId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                                "Conversation not found"));
            }

            var history = conversationService.getHistory(conversationId);
            return ResponseEntity.ok(ChatConversationResponseMapper.toHistoryResponse(
                    history,
                    conversationId,
                    conversationService.isAwaitingDecision(conversationId),
                    conversationService.getStage(conversationId).name()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    /**
     * Clears the chat history for a given conversation ID.
     *
     * @param conversationId the conversation ID
     * @return ResponseEntity containing a success message and the conversation ID
     */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String conversationId) {
        try {
            conversationService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of(
                    "message", "Conversation cleared successfully",
                    "conversationId", conversationId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ChatConversationResponseMapper.toErrorResponse(conversationId,
                            e.getMessage() != null ? e.getMessage() : "Unknown error"));

        }
    }

}
