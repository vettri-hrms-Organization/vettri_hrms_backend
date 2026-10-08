package com.haodaone.assistant;

import java.util.Set;

public record AssistantContext(
        Long userId,
        Long employeeId,
        Long companyId,
        Set<String> authorities,
        Set<String> roleNames,
        String department,
        String team,
        String designation
) {
    public AssistantContext {
        authorities = Set.copyOf(authorities);
        roleNames = Set.copyOf(roleNames);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority) || authorities.contains("ROLE_" + authority);
    }

    public boolean hasAnyAuthority(String... requested) {
        for (String authority : requested) {
            if (hasAuthority(authority)) return true;
        }
        return false;
    }
}
