package com.ilucatero.springai.chat_cs.app.models;

/**
 * Represents the AI analysis of a customer request, including its category,
 * priority, sentiment, and a short summary.
 */
public record CustomerRequestAnalysis(
                String category,
                String priority,
                String sentiment,
                String summary) {
}
