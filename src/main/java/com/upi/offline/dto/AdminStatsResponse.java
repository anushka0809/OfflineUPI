package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Aggregate statistics dashboard payload for administrator users")
public class AdminStatsResponse {

    @Schema(description = "Total number of transactions processed")
    private long totalTransactions;

    @Schema(description = "Count of transactions in PENDING status")
    private long pendingCount;

    @Schema(description = "Count of transactions in SYNCED status")
    private long syncedCount;

    @Schema(description = "Count of transactions in FAILED status")
    private long failedCount;

    @Schema(description = "Percentage success rate of transactions", example = "92.5")
    private double successRate;

    @Schema(description = "Total amount of settled/synced transactions", example = "50000.00")
    private double totalAmount;

    @Schema(description = "Daily aggregate volume count of transactions")
    private Map<String, Long> dailyTransactions;

    @Schema(description = "Monthly aggregate volume count of transactions")
    private Map<String, Long> monthlyTransactions;

    public AdminStatsResponse() {
    }

    public long getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(long totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(long pendingCount) {
        this.pendingCount = pendingCount;
    }

    public long getSyncedCount() {
        return syncedCount;
    }

    public void setSyncedCount(long syncedCount) {
        this.syncedCount = syncedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Map<String, Long> getDailyTransactions() {
        return dailyTransactions;
    }

    public void setDailyTransactions(Map<String, Long> dailyTransactions) {
        this.dailyTransactions = dailyTransactions;
    }

    public Map<String, Long> getMonthlyTransactions() {
        return monthlyTransactions;
    }

    public void setMonthlyTransactions(Map<String, Long> monthlyTransactions) {
        this.monthlyTransactions = monthlyTransactions;
    }
}
