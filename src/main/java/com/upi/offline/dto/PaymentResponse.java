package com.upi.offline.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response returned after processing an offline UPI payment")
public class PaymentResponse {

    @Schema(
            description = "Unique transaction ID generated for the payment",
            example = "33ae5ed4-0b52-4069-b64f-df271b00e2f3"
    )
    private String transactionId;

    @Schema(
            description = "Current transaction status",
            example = "DELIVERED_OFFLINE"
    )
    private String status;

    @Schema(
            description = "Number of network hops simulated during delivery",
            example = "2"
    )
    @JsonProperty("hopCount")
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