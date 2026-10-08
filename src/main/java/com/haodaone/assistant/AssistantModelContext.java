package com.haodaone.assistant;

import java.util.List;
import java.util.Map;

public record AssistantModelContext(
        List<String> roleNames,
        List<String> capabilities,
        Map<String, List<String>> permissionScopes,
        String department,
        String team,
        String designation
) {
    public AssistantModelContext {
        roleNames = List.copyOf(roleNames);
        capabilities = List.copyOf(capabilities);
        permissionScopes = Map.copyOf(permissionScopes);
    }
}
