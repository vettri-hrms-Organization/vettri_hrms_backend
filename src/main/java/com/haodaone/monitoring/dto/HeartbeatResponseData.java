package com.haodaone.monitoring.dto;

/**
 * Becomes the "data" field of the AgentEnvelope returned from
 * POST /api/agent/heartbeat. Mirrors HeartbeatResponse +
 * RemoteAgentDirective from HaodaOne.Agent.Models.HeartbeatModels - the
 * agent applies {@code directive} in-memory only (never persisted over
 * local config), so it's safe to change on every response.
 */
public class HeartbeatResponseData {

    private boolean success = true;
    private String message;
    private Directive directive;

    public static HeartbeatResponseData accepted(Directive directive) {
        HeartbeatResponseData data = new HeartbeatResponseData();
        data.success = true;
        data.directive = directive;
        return data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Directive getDirective() {
        return directive;
    }

    public void setDirective(Directive directive) {
        this.directive = directive;
    }

    /** Mirrors RemoteAgentDirective exactly - null fields mean "no change" on the agent side. */
    public static class Directive {
        private Integer heartbeatIntervalSeconds;
        private Boolean pauseMonitoring;

        public Directive() {
        }

        public Directive(Integer heartbeatIntervalSeconds, Boolean pauseMonitoring) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
            this.pauseMonitoring = pauseMonitoring;
        }

        public Integer getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(Integer heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public Boolean getPauseMonitoring() {
            return pauseMonitoring;
        }

        public void setPauseMonitoring(Boolean pauseMonitoring) {
            this.pauseMonitoring = pauseMonitoring;
        }
    }
}
