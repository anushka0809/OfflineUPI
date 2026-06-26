package com.upi.offline.dto;

public class SyncResponse {

    private int syncedTransactions;

    public SyncResponse() {
    }

    public SyncResponse(int syncedTransactions) {
        this.syncedTransactions = syncedTransactions;
    }

    public int getSyncedTransactions() {
        return syncedTransactions;
    }

    public void setSyncedTransactions(int syncedTransactions) {
        this.syncedTransactions = syncedTransactions;
    }
}