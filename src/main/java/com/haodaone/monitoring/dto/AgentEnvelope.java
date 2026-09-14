package com.haodaone.monitoring.dto;

/**
 * Wraps every /api/agent/** response body. This is a deliberate exception
 * to the rest of the platform's convention of returning raw DTOs (compare
 * DepartmentController, AuditController) - it exists because HaodaOne.Agent
 * Services/ApiClientService.cs deserializes responses as
 * {@code ApiEnvelope<T>} with a {@code data} field and reads
 * {@code envelope?.Data}. Only AgentController uses this; every other
 * controller in the platform keeps returning bare DTOs/entities as before.
 */
public class AgentEnvelope<T> {

    private boolean success;
    private String message;
    private T data;
    private Integer statusCode;

    public static <T> AgentEnvelope<T> ok(T data) {
        AgentEnvelope<T> envelope = new AgentEnvelope<>();
        envelope.success = true;
        envelope.data = data;
        envelope.statusCode = 200;
        return envelope;
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }
}
