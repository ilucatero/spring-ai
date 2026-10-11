package com.ilucatero.springai.chat_cs.app.model;

/**
 * Represents the AI analysis of a customer request, including its category,
 * priority, sentiment, and a short summary.
 *
 * @param category  the category of the customer request
 * @param priority  the priority level of the customer request
 * @param sentiment the sentiment of the customer request
 * @param summary   a short summary of the customer request
 */
public record CustomerRequestAnalysis(
        String category,
        String priority,
        String sentiment,
        String summary) {
}
