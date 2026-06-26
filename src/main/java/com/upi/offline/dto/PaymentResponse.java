package com.upi.offline.dto;

public class PaymentResponse {

    private String transactionId;
    private String status;
    private Integer hops;

    public PaymentResponse() {
    }

    public PaymentResponse(String transactionId, String status, Integer hops) {
        this.transactionId = transactionId;
        this.status = status;
        this.hops = hops;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHops() {
        return hops;
    }

    public void setHops(Integer hops) {
        this.hops = hops;
    }
}