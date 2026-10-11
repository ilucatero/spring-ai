package com.ilucatero.springai.chat_cs.app.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ilucatero.springai.chat_cs.app.service.ChatCustomeRequestAnalysisService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatCustomeRequestAnalysisService customRequestAnalysisService;

    public ChatController(ChatCustomeRequestAnalysisService customRequestAnalysisService) {
        this.customRequestAnalysisService = customRequestAnalysisService;
    }

    /**
     * Endpoint to handle chat requests. It accepts a JSON body with a "message"
     * field,
     * sends the message to the ChatClient, and returns the response.
     *
     * @param body A map containing the "message" key with the user's input.
     * @return A ResponseEntity containing the original prompt and the answer from
     *         the ChatClient, or an error message if the request fails.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        var prompt = body.getOrDefault("message", "Hello");
        try {
            var answer = customRequestAnalysisService.chat(prompt);
            return ResponseEntity.ok(Map.of("prompt", prompt, "answer", answer));

        } catch (Exception e) {
            // Propagate error details back to the client
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "prompt", prompt,
                            "error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

}
