package com.haodaone.monitoring.dto;

import java.util.List;

/**
 * Becomes the "data" field of the AgentEnvelope returned from
 * POST /api/agent/activity/batch. Mirrors ActivityBatchResponse - the agent
 * uses acceptedSessionIds to prune its local cache (LocalCacheService)
 * safely, so this must only list sessions that were actually persisted.
 */
public class ActivityBatchResponseData {

    private boolean success;
    private String message;
    private List<String> acceptedSessionIds;

    public static ActivityBatchResponseData of(List<String> acceptedSessionIds) {
        ActivityBatchResponseData data = new ActivityBatchResponseData();
        data.success = true;
        data.acceptedSessionIds = acceptedSessionIds;
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

    public List<String> getAcceptedSessionIds() {
        return acceptedSessionIds;
    }

    public void setAcceptedSessionIds(List<String> acceptedSessionIds) {
        this.acceptedSessionIds = acceptedSessionIds;
    }
}
