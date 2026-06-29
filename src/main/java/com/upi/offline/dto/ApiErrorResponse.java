package com.upi.offline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private Boolean success;
    private String timestamp;
    private Integer status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> errors;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(int status, String error, String message) {
        this.success = false;
        this.timestamp = LocalDateTime.now().toString();
        this.status = status;
        this.error = error;
        this.message = message;
    }

    public ApiErrorResponse(int status, String message, Map<String, String> errors) {
        this.success = false;
        this.timestamp = LocalDateTime.now().toString();
        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    public ApiErrorResponse(String message) {
        this.success = false;
        this.timestamp = LocalDateTime.now().toString();
        this.message = message;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }
}
