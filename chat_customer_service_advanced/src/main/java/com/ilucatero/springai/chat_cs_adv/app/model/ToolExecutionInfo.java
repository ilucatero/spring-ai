package com.ilucatero.springai.chat_cs_adv.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ToolExecutionInfo(
        String toolName,
        List<String> arguments,
        String status) {
    public ToolExecutionInfo {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    /**
     * Legacy constructor kept for older examples that passed result and timing
     * metadata.
     */
    public ToolExecutionInfo(String toolName, String arguments, String result, long executionTimeMs) {
        this(toolName, List.of(arguments), "completed");
    }

    /**
     * Records tool executions for the current agent request.
     */
    public static class Recorder {

        private static final String COMPLETED = "completed";
        private static final String FAILED = "failed";

        private final ThreadLocal<List<ToolExecutionInfo>> currentExecutions = new ThreadLocal<>();

        public void start() {
            currentExecutions.set(new ArrayList<>());
        }

        public List<ToolExecutionInfo> stop() {
            List<ToolExecutionInfo> executions = currentExecutions.get();
            currentExecutions.remove();
            return executions == null ? List.of() : List.copyOf(executions);
        }

        public <T> T record(String toolName, List<String> arguments, Supplier<T> invocation) {
            try {
                T result = invocation.get();
                add(toolName, arguments, COMPLETED);
                return result;
            } catch (RuntimeException e) {
                add(toolName, arguments, FAILED);
                throw e;
            }
        }

        private void add(String toolName, List<String> arguments, String status) {
            List<ToolExecutionInfo> executions = currentExecutions.get();
            if (executions != null) {
                executions.add(new ToolExecutionInfo(toolName, arguments, status));
            }
        }
    }
}
