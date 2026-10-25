package com.ilucatero.springai.chat_cs_adv.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ToolExecutionInfo(
        String toolName,
        List<String> arguments,
        Status status) {

    public enum Status {
        COMPLETED, FAILED
    }

    public ToolExecutionInfo {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }

    /**
     * Legacy constructor kept for older examples that passed result and timing
     * metadata.
     */
    public ToolExecutionInfo(String toolName, String arguments, String result, long executionTimeMs) {
        this(toolName, List.of(arguments), Status.COMPLETED);
    }

    /**
     * Records tool executions for the current agent request.
     */
    public static class Recorder {

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
                add(toolName, arguments, Status.COMPLETED);
                return result;
            } catch (RuntimeException e) {
                add(toolName, arguments, Status.FAILED);
                throw e;
            }
        }

        private void add(String toolName, List<String> arguments, Status status) {
            List<ToolExecutionInfo> executions = currentExecutions.get();
            if (executions != null) {
                executions.add(new ToolExecutionInfo(toolName, arguments, status));
            }
        }
    }
}
