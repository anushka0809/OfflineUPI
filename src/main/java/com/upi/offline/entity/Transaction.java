package com.upi.offline.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_sender", columnList = "sender"),
    @Index(name = "idx_receiver", columnList = "receiver"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    @Column(nullable = false)
    private Double amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "sync_time")
    private LocalDateTime syncTime;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private String status;

    @Column(name = "hop_count", nullable = false)
    private Integer hopCount = 0;

    @Column(name = "encrypted_payload", length = 5000)
    private String encryptedPayload;

    public Transaction() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getSyncTime() {
        return syncTime;
    }

    public void setSyncTime(LocalDateTime syncTime) {
        this.syncTime = syncTime;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHopCount() {
        return hopCount;
    }

    public void setHopCount(Integer hopCount) {
        this.hopCount = hopCount;
    }

    public String getEncryptedPayload() {
        return encryptedPayload;
    }

    public void setEncryptedPayload(String encryptedPayload) {
        this.encryptedPayload = encryptedPayload;
    }

    // Compatibility method
    public LocalDateTime getTimestamp() {
        return createdAt;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.createdAt = timestamp;
    }
}