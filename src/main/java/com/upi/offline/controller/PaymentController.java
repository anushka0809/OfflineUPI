package com.upi.offline.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.upi.offline.dto.*;
import com.upi.offline.entity.Transaction;
import com.upi.offline.service.PaymentService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/payment")
@Tag(name = "Offline UPI APIs", description = "Send, sync, split, and manage offline UPI transactions")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    private String getLoggedInUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        return principal.toString();
    }

    private boolean isCurrentUserAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    @Operation(summary = "Send Offline UPI Payment")
    @PostMapping("/send")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> send(@Valid @RequestBody PaymentRequest request) {
        String loggedInUser = getLoggedInUsername();
        log.info("Payment request from {}: receiver={}, amount={}", loggedInUser, request.getReceiver(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setReceiver(request.getReceiver());
        transaction.setAmount(request.getAmount());
        transaction.setCategory(request.getCategory());
        transaction.setNote(request.getNote());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus("PENDING");
        transaction.setHopCount(0);

        transaction = service.save(transaction, loggedInUser);

        PaymentResponse data = new PaymentResponse(
                transaction.getTransactionId(),
                transaction.getStatus(),
                transaction.getHopCount()
        );

        return new ResponseEntity<>(ApiResponse.success("Payment processed successfully", data), HttpStatus.CREATED);
    }

    @Operation(summary = "Split an expense among participants")
    @PostMapping("/split")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SplitExpenseResponse>> splitExpense(@Valid @RequestBody SplitExpenseRequest request) {
        String loggedInUser = getLoggedInUsername();
        SplitExpenseResponse result = service.splitExpense(request, loggedInUser);
        return new ResponseEntity<>(ApiResponse.success("Split expense created", result), HttpStatus.CREATED);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Transaction>>> getAllTransactions() {
        List<Transaction> transactions = service.getAllTransactions();
        return ResponseEntity.ok(ApiResponse.success("All transactions retrieved successfully", transactions));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> getTransaction(@PathVariable Long id) {
        Transaction transaction = service.getTransaction(id);
        service.verifyTransactionAccess(transaction, getLoggedInUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(ApiResponse.success("Transaction retrieved successfully", transaction));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncResponse>> syncTransactions() {
        List<Transaction> synced = service.syncPendingTransactions();
        long syncedCount = synced.stream().filter(t -> "SYNCED".equals(t.getStatus())).count();
        SyncResponse syncResponse = new SyncResponse((int) syncedCount, syncedCount + " transactions synced successfully");
        return ResponseEntity.ok(ApiResponse.success("Transactions synchronized successfully", syncResponse));
    }

    @PostMapping("/retry/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> retry(@PathVariable Long id) {
        Transaction transaction = service.retryPayment(id, getLoggedInUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(ApiResponse.success("Transaction retry queued successfully", transaction));
    }

    @PostMapping("/cancel/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> cancel(@PathVariable Long id) {
        Transaction transaction = service.cancelPayment(id, getLoggedInUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(ApiResponse.success("Transaction cancelled successfully", transaction));
    }

    @GetMapping("/status/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> getStatus(@PathVariable Long id) {
        Transaction transaction = service.getTransaction(id);
        service.verifyTransactionAccess(transaction, getLoggedInUsername(), isCurrentUserAdmin());
        return ResponseEntity.ok(ApiResponse.success("Status retrieved successfully", transaction.getStatus()));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<Transaction>>> getHistory(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double amount,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String receiver,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String username = getLoggedInUsername();
        Pageable pageable = buildPageable(page, size, sort);
        Page<Transaction> result = service.getHistory(username, status, amount, sender, receiver, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("User history retrieved successfully", result));
    }

    @GetMapping("/history/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<Transaction>>> getUserHistoryAdmin(
            @PathVariable String username,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double amount,
            @RequestParam(required = false) String sender,
            @RequestParam(required = false) String receiver,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        Pageable pageable = buildPageable(page, size, sort);
        Page<Transaction> result = service.getHistoryAdmin(username, status, amount, sender, receiver, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Admin query completed successfully", result));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        AdminStatsResponse stats = service.getStats();
        return ResponseEntity.ok(ApiResponse.success("Dashboard metrics retrieved successfully", stats));
    }

    private Pageable buildPageable(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        Sort sortObj = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1])
                ? Sort.by(sortParams[0]).descending()
                : Sort.by(sortParams[0]).ascending();
        return PageRequest.of(page, size, sortObj);
    }

    private List<Transaction> getHistoryListForExport() {
        String username = getLoggedInUsername();
        if (isCurrentUserAdmin()) {
            return service.getAllTransactions();
        }
        return service.getHistory(username, null, null, null, null, null, Pageable.unpaged()).getContent();
    }

    @GetMapping("/export/csv")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportCSV(HttpServletResponse response) {
        service.exportHistoryToCSV(getHistoryListForExport(), response);
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportExcel(HttpServletResponse response) {
        service.exportHistoryToExcel(getHistoryListForExport(), response);
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportPDF(HttpServletResponse response) {
        service.exportHistoryToPDF(getHistoryListForExport(), response);
    }
}
