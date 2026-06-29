package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
@Schema(description = "Request object for sending an offline UPI payment")
public class PaymentRequest {

    @Schema(
            description = "Name of the sender",
            example = "Alice",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Sender cannot be empty")
    private String sender;

    @Schema(
            description = "Name of the receiver",
            example = "Bob",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Receiver cannot be empty")
    private String receiver;

    @Schema(
            description = "Amount to be transferred",
            example = "500.0",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be greater than zero")
    private Double amount;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}