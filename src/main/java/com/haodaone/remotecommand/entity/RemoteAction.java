package com.haodaone.remotecommand.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The approved remote actions. Each one is implemented inside the agent as fixed code (no shell
 * text is ever sent), so a compromised admin account can only trigger these, not run anything.
 * Keys must match HaodaOne.Agent/Services/RemoteActionCatalog.cs. Queued with shellType "ACTION"
 * and the key in {@code command}.
 */
public enum RemoteAction {
    SYSTEM_INFO("System information", "OS, model, memory, uptime and hotfixes (systeminfo).", "Diagnostics", 90),
    DISK_SPACE("Disk space", "Free and total space for every fixed drive.", "Diagnostics", 30),
    DISK_HEALTH("Disk health", "Health status reported by each physical disk.", "Diagnostics", 60),
    LIST_PROCESSES("Running programs", "Processes with memory use, largest first.", "Diagnostics", 30),
    RECENT_CRASHES("Recent crashes and display errors", "Blue screens, unexpected shutdowns, display-driver resets and app hangs from the last 7 days.", "Diagnostics", 60),
    NETWORK_INFO("Network details", "IP addresses, DNS servers and adapters (ipconfig /all).", "Network", 30),
    FLUSH_DNS("Flush DNS cache", "Fixes sites that won't load after a DNS change (ipconfig /flushdns).", "Network", 30),
    RENEW_IP("Renew IP address", "Releases and renews the DHCP address. The laptop is offline for a few seconds.", "Network", 90),
    RESTART_PRINT_SPOOLER("Restart print spooler", "Clears stuck print jobs.", "Fixes", 60),
    RESTART_DISPLAY_ADAPTER("Restart display adapter", "Restarts the graphics driver; fixes many black-screen or frozen-display cases. The screen flickers once.", "Fixes", 90),
    CLEAR_TEMP_FILES("Clear temporary files", "Deletes Windows temporary files older than 1 day.", "Fixes", 120),
    GROUP_POLICY_UPDATE("Refresh group policy", "Applies the latest company policies (gpupdate /force).", "Fixes", 180),
    SCHEDULE_RESTART("Restart in 5 minutes", "Warns the user and restarts the laptop after 5 minutes.", "Power", 30),
    CANCEL_RESTART("Cancel scheduled restart", "Cancels a pending restart.", "Power", 30);

    private final String label;
    private final String description;
    private final String category;
    private final int timeoutSeconds;

    RemoteAction(String label, String description, String category, int timeoutSeconds) {
        this.label = label;
        this.description = description;
        this.category = category;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String category() { return category; }
    public int timeoutSeconds() { return timeoutSeconds; }

    public static Optional<RemoteAction> fromKey(String key) {
        if (key == null) return Optional.empty();
        String normalized = key.trim();
        return Arrays.stream(values()).filter(action -> action.name().equalsIgnoreCase(normalized)).findFirst();
    }
}
