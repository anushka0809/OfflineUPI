package com.upi.offline.controller;

import com.upi.offline.dto.PaymentRequest;
import com.upi.offline.dto.PaymentResponse;
import com.upi.offline.dto.SyncResponse;
import com.upi.offline.entity.Transaction;
import com.upi.offline.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private PaymentService service;

    @PostMapping("/send")
    public PaymentResponse send(@Valid @RequestBody PaymentRequest request) {

        Transaction transaction = new Transaction();

        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setSender(request.getSender());
        transaction.setReceiver(request.getReceiver());
        transaction.setAmount(request.getAmount());
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setStatus("PENDING");
        transaction.setHopCount(0);

        transaction = service.save(transaction);

        return new PaymentResponse(
                transaction.getTransactionId(),
                transaction.getStatus(),
                transaction.getHopCount()
        );
    }

    @GetMapping("/all")
    public List<Transaction> getAllTransactions() {
        return service.getAllTransactions();
    }

    @GetMapping("/{id}")
    public Transaction getTransaction(@PathVariable Long id) {
        return service.getTransaction(id);
    }

    @PostMapping("/sync")
    public SyncResponse syncTransactions() {

        List<Transaction> synced = service.syncPendingTransactions();

        return new SyncResponse(
                synced.size(),
                "Pending transactions synced successfully"
        );
    }
}