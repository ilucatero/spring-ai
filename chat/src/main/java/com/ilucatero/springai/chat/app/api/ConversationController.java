package com.ilucatero.springai.chat.app.api;

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

import com.ilucatero.springai.chat.app.service.ConversationService;

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
                    "message", "Conversation started successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String conversationId = body.get("conversationId");
        String message = body.getOrDefault("message", "");

        if (conversationId == null || conversationId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "conversationId is required"));
        }

        if (message.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "message cannot be empty"));
        }

        try {
            String answer = conversationService.chat(conversationId, message);
            return ResponseEntity.ok(Map.of(
                    "conversationId", conversationId,
                    "message", message,
                    "answer", answer));
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
                    "messages", history));
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