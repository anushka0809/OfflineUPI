package com.upi.offline.dto;

import java.util.List;

public class SplitExpenseResponse {
    private String description;
    private Double totalAmount;
    private Double sharePerPerson;
    private int participantCount;
    private List<PaymentResponse> transactions;

    public SplitExpenseResponse() {
    }

    public SplitExpenseResponse(String description, Double totalAmount, Double sharePerPerson,
                                int participantCount, List<PaymentResponse> transactions) {
        this.description = description;
        this.totalAmount = totalAmount;
        this.sharePerPerson = sharePerPerson;
        this.participantCount = participantCount;
        this.transactions = transactions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Double getSharePerPerson() {
        return sharePerPerson;
    }

    public void setSharePerPerson(Double sharePerPerson) {
        this.sharePerPerson = sharePerPerson;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public List<PaymentResponse> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<PaymentResponse> transactions) {
        this.transactions = transactions;
    }
}
