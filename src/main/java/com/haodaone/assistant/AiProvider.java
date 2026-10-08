package com.haodaone.assistant;

import java.util.List;
import java.util.Map;

public interface AiProvider {
    boolean isConfigured();

    ModelTurn chat(List<ModelMessage> messages, List<ModelTool> tools);

    String providerName();

    record ModelMessage(String role, String content, String toolName, List<ToolCall> toolCalls) {
        public static ModelMessage user(String content) {
            return new ModelMessage("user", content, null, List.of());
        }

        public static ModelMessage assistant(String content) {
            return new ModelMessage("assistant", content, null, List.of());
        }

        public static ModelMessage assistantToolCalls(List<ToolCall> toolCalls) {
            return new ModelMessage("assistant", "", null, List.copyOf(toolCalls));
        }

        public static ModelMessage tool(String toolName, String content) {
            return new ModelMessage("tool", content, toolName, List.of());
        }
    }

    record ToolCall(String name, Map<String, Object> arguments) {}

    record ModelTurn(String content, List<ToolCall> toolCalls) {
        public ModelTurn {
            toolCalls = List.copyOf(toolCalls);
        }
    }

    record ModelTool(String name, String description, Map<String, Object> parameters) {}
}
