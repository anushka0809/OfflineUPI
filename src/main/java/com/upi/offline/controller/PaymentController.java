package com.upi.offline.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/payment")
@Tag(
        name = "Offline UPI APIs",
        description = "APIs for sending, syncing and retrieving offline UPI transactions"
)
public class PaymentController {

    @Autowired
    private PaymentService service;

    @Operation(
            summary = "Send Offline UPI Payment",
            description = "Creates an AES encrypted offline UPI transaction, simulates network hops, stores it in the database, and returns the transaction details."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment processed successfully",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
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

    @Operation(
            summary = "Get All Transactions",
            description = "Retrieves all offline UPI transactions stored in the database."
    )
    @GetMapping("/all")
    public List<Transaction> getAllTransactions() {
        return service.getAllTransactions();
    }

    @Operation(
            summary = "Get Transaction by ID",
            description = "Retrieves a specific offline UPI transaction using its database ID."
    )
    @GetMapping("/{id}")
    public Transaction getTransaction(@PathVariable Long id) {
        return service.getTransaction(id);
    }

    @Operation(
            summary = "Synchronize Pending Transactions",
            description = "Synchronizes every transaction that is waiting for network connectivity and updates its status."
    )
    @PostMapping("/sync")
    public SyncResponse syncTransactions() {

        List<Transaction> synced = service.syncPendingTransactions();

        return new SyncResponse(
                synced.size(),
                "Pending transactions synced successfully"
        );
    }
}