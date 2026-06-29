package com.upi.offline.controller;

import com.upi.offline.dto.*;
import com.upi.offline.entity.BillReminder;
import com.upi.offline.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet APIs", description = "Balance, bills, and monthly summary")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    private String getLoggedInUsername() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return principal.toString();
    }

    @Operation(summary = "Get wallet balance and monthly stats")
    @GetMapping("/balance")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WalletResponse>> getBalance() {
        String username = getLoggedInUsername();
        WalletResponse wallet = walletService.getWallet(username);
        return ResponseEntity.ok(ApiResponse.success("Wallet retrieved successfully", wallet));
    }

    @Operation(summary = "Get monthly spending summary")
    @GetMapping("/monthly-summary")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MonthlySummaryResponse>> getMonthlySummary() {
        String username = getLoggedInUsername();
        MonthlySummaryResponse summary = walletService.getMonthlySummary(username);
        return ResponseEntity.ok(ApiResponse.success("Monthly summary retrieved", summary));
    }

    @Operation(summary = "List registered usernames for transfers")
    @GetMapping("/users")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> listUsers() {
        List<String> users = walletService.listUsernames();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }

    @Operation(summary = "Get active bill reminders")
    @GetMapping("/bills")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<BillReminder>>> getBills() {
        String username = getLoggedInUsername();
        List<BillReminder> bills = walletService.getBillReminders(username);
        return ResponseEntity.ok(ApiResponse.success("Bill reminders retrieved", bills));
    }

    @Operation(summary = "Create a monthly bill reminder")
    @PostMapping("/bills")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BillReminder>> createBill(@Valid @RequestBody BillReminderRequest request) {
        String username = getLoggedInUsername();
        BillReminder bill = walletService.createBillReminder(
                username, request.getTitle(), request.getAmount(),
                request.getCategory(), request.getDueDay());
        return new ResponseEntity<>(ApiResponse.success("Bill reminder created", bill), HttpStatus.CREATED);
    }

    @Operation(summary = "Delete a bill reminder")
    @DeleteMapping("/bills/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteBill(@PathVariable Long id) {
        String username = getLoggedInUsername();
        walletService.deleteBillReminder(id, username);
        return ResponseEntity.ok(ApiResponse.success("Bill reminder deleted", "OK"));
    }
}
