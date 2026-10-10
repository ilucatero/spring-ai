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

import com.ilucatero.springai.chat_cs.app.service.ConversationService;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startConversation() {
        try {
            var conversationId = conversationService.startConversation();
            return ResponseEntity.ok(Map.of(
                    "conversationId", conversationId,
                    "message", conversationService.getHistory(conversationId).get(0).getText()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        var conversationId = body.get("conversationId");
        var decision = body.get("decision");
        var message = body.getOrDefault("message", "");

        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "conversationId is required"));
        }

        if (decision == null && message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "message or decision is required"));
        }

        try {
            var reply = decision == null
                    ? conversationService.chat(conversationId, message)
                    : conversationService.decide(conversationId, decision);
            return ResponseEntity.ok(Map.of(
                    "conversationId", conversationId,
                    "answer", reply.questionnaire(),
                    "status", reply.status(),
                    "message", reply.message()));
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
                    "awaitingDecision", conversationService.isAwaitingDecision(conversationId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

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
