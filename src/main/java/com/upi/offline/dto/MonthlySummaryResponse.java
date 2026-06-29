package com.upi.offline.dto;

import java.util.Map;

public class MonthlySummaryResponse {
    private String month;
    private Double totalSpent;
    private Double totalReceived;
    private int transactionCount;
    private Map<String, Double> categoryBreakdown;
    private Map<String, Long> dailyActivity;

    public MonthlySummaryResponse() {
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(Double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Double getTotalReceived() {
        return totalReceived;
    }

    public void setTotalReceived(Double totalReceived) {
        this.totalReceived = totalReceived;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Map<String, Double> getCategoryBreakdown() {
        return categoryBreakdown;
    }

    public void setCategoryBreakdown(Map<String, Double> categoryBreakdown) {
        this.categoryBreakdown = categoryBreakdown;
    }

    public Map<String, Long> getDailyActivity() {
        return dailyActivity;
    }

    public void setDailyActivity(Map<String, Long> dailyActivity) {
        this.dailyActivity = dailyActivity;
    }
}
