package com.ilucatero.springai.chat_cs.app.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class FluxQuestionnaireService {

    private static final Pattern QUESTION_MARKER = Pattern.compile("^(?:\\d+[.)]|[*-])\\s+(.+?)\\s*$");

    /**
     * Creates a manual questionnaire from the given input string.
     *
     * @param input the input string containing the title and questions
     * @return a list of strings representing the title (0) and questions (1..n)
     * @throws IllegalArgumentException if the input format is invalid
     */
    public List<String> createManualQuestionnaire(String input) {
        var lines = new ArrayList<String>();
        for (var line : input.split("\\R")) {
            var trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                lines.add(trimmedLine);
            }
        }
        if (lines.size() < 2 || lines.get(0).startsWith("*") || lines.get(0).startsWith("-")
                || lines.get(0).matches("^\\d+[.)].*")) {
            throw new IllegalArgumentException(
                    "Invalid manual questionnaire format. The first line should be the title, followed by questions.");
        }

        var questionnaire = new ArrayList<String>();
        // truncate title and add it as the first element
        var title = lines.get(0);
        questionnaire.add(title.substring(0, Math.min(title.length(), 500)));

        for (var line : lines.subList(1, lines.size())) {
            var matcher = QUESTION_MARKER.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Invalid manual questionnaire format. Each question should start with a bullet point or number.");
            }
            // extract the question text and truncate it to 500 characters
            var question = matcher.group(1);
            questionnaire.add(question.substring(0, Math.min(question.length(), 500)));
        }

        return questionnaire;
    }
}
