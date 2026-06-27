package com.upi.offline.dto;

public class SyncResponse {

    private int syncedCount;
    private String message;

    public SyncResponse() {
    }

    public SyncResponse(int syncedCount, String message) {
        this.syncedCount = syncedCount;
        this.message = message;
    }

    public int getSyncedCount() {
        return syncedCount;
    }

    public void setSyncedCount(int syncedCount) {
        this.syncedCount = syncedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}