package com.upi.offline.dto;

public class WalletResponse {
    private String username;
    private String upiId;
    private Double balance;
    private Double monthlySpent;
    private Double monthlyReceived;

    public WalletResponse() {
    }

    public WalletResponse(String username, String upiId, Double balance, Double monthlySpent, Double monthlyReceived) {
        this.username = username;
        this.upiId = upiId;
        this.balance = balance;
        this.monthlySpent = monthlySpent;
        this.monthlyReceived = monthlyReceived;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Double getMonthlySpent() {
        return monthlySpent;
    }

    public void setMonthlySpent(Double monthlySpent) {
        this.monthlySpent = monthlySpent;
    }

    public Double getMonthlyReceived() {
        return monthlyReceived;
    }

    public void setMonthlyReceived(Double monthlyReceived) {
        this.monthlyReceived = monthlyReceived;
    }
}
