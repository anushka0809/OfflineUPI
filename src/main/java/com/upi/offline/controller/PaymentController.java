package com.upi.offline.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.upi.offline.dto.ApiResponse;
import com.upi.offline.dto.ApiErrorResponse;
import com.upi.offline.dto.PaymentRequest;
import com.upi.offline.dto.PaymentResponse;
import com.upi.offline.dto.SyncResponse;
import com.upi.offline.dto.AdminStatsResponse;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/payment")
@Tag(
        name = "Offline UPI APIs",
        description = "APIs for sending, syncing, retrying, and retrieving offline UPI transactions"
)
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
        } else {
            return principal.toString();
        }
    }

    private boolean isCurrentUserAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    @Operation(
            summary = "Send Offline UPI Payment",
            description = "Creates an AES encrypted offline UPI transaction, simulates network hops, stores it, and returns details. Accessible by USER or ADMIN."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Payment processed successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data or validation failure",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized access",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/send")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> send(@Valid @RequestBody PaymentRequest request) {
        log.info("Payment request received: sender={}, receiver={}, amount={}", 
                request.getSender(), request.getReceiver(), request.getAmount());

        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setSender(request.getSender());
        transaction.setReceiver(request.getReceiver());
        transaction.setAmount(request.getAmount());
        transaction.setTimestamp(LocalDateTime.now());
        transaction.setStatus("PENDING");
        transaction.setHopCount(0);

        transaction = service.save(transaction);

        PaymentResponse data = new PaymentResponse(
                transaction.getTransactionId(),
                transaction.getStatus(),
                transaction.getHopCount()
        );

        ApiResponse<PaymentResponse> response = ApiResponse.success("Payment processed successfully", data);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Get All Transactions",
            description = "Retrieves all offline UPI transactions stored in the database. Restricted to ADMIN."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Transactions retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized access",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden access (admin privilege required)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Transaction>>> getAllTransactions() {
        log.info("Request received to fetch all transactions");
        List<Transaction> transactions = service.getAllTransactions();
        ApiResponse<List<Transaction>> response = ApiResponse.success("All transactions retrieved successfully", transactions);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get Transaction by ID",
            description = "Retrieves a specific offline UPI transaction using its database ID. Accessible by USER or ADMIN."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Transaction retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> getTransaction(@PathVariable Long id) {
        log.info("Request received to fetch transaction with ID: {}", id);
        Transaction transaction = service.getTransaction(id);
        ApiResponse<Transaction> response = ApiResponse.success("Transaction retrieved successfully", transaction);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Synchronize Pending Transactions",
            description = "Synchronizes every transaction that is waiting for network connectivity and updates its status. Restricted to ADMIN."
    )
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncResponse>> syncTransactions() {
        log.info("Request received to synchronize transactions");
        List<Transaction> synced = service.syncPendingTransactions();

        SyncResponse syncResponse = new SyncResponse(
                synced.size(),
                "Pending transactions synced successfully"
        );

        ApiResponse<SyncResponse> response = ApiResponse.success("Transactions synchronized successfully", syncResponse);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Retry failed transaction", description = "Resets a FAILED transaction to WAITING_FOR_SYNC for synchronization retrying.")
    @PostMapping("/retry/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> retry(@PathVariable Long id) {
        log.info("Request received to retry transaction with ID: {}", id);
        Transaction transaction = service.retryPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Transaction retry queued successfully", transaction));
    }

    @Operation(summary = "Cancel pending transaction", description = "Cancels a transaction in PENDING or WAITING_FOR_SYNC state.")
    @PostMapping("/cancel/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Transaction>> cancel(@PathVariable Long id) {
        log.info("Request received to cancel transaction with ID: {}", id);
        Transaction transaction = service.cancelPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Transaction cancelled successfully", transaction));
    }

    @Operation(summary = "Get transaction status", description = "Retrieves the status string of a transaction by ID.")
    @GetMapping("/status/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> getStatus(@PathVariable Long id) {
        log.info("Request received to fetch status of transaction ID: {}", id);
        String status = service.getPaymentStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Status retrieved successfully", status));
    }

    @Operation(summary = "Get current user's history", description = "Fetches filtering, paginated, and sorted transaction history for the logged-in user.")
    @GetMapping("/history")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<Transaction>>> getHistory(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by exact amount") @RequestParam(required = false) Double amount,
            @Parameter(description = "Filter by sender keyword") @RequestParam(required = false) String sender,
            @Parameter(description = "Filter by receiver keyword") @RequestParam(required = false) String receiver,
            @Parameter(description = "Search term (uuid, sender or receiver)") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sorting criteria (field,asc/desc)", example = "createdAt,desc") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        String username = getLoggedInUsername();
        log.info("Fetching transaction history for logged-in user: {}", username);

        String[] sortParams = sort.split(",");
        Sort sortObj = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1])
                ? Sort.by(sortParams[0]).descending()
                : Sort.by(sortParams[0]).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Transaction> result = service.getHistory(username, status, amount, sender, receiver, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("User history retrieved successfully", result));
    }

    @Operation(summary = "Get user history (Admin Only)", description = "Enables an administrator to fetch history of any specific username with filtering, pagination, and sorting.")
    @GetMapping("/history/{username}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<Transaction>>> getUserHistoryAdmin(
            @Parameter(description = "Username to fetch history for") @PathVariable String username,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by exact amount") @RequestParam(required = false) Double amount,
            @Parameter(description = "Filter by sender keyword") @RequestParam(required = false) String sender,
            @Parameter(description = "Filter by receiver keyword") @RequestParam(required = false) String receiver,
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sorting criteria") @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        log.info("Admin request to fetch history for user: {}", username);

        String[] sortParams = sort.split(",");
        Sort sortObj = sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1])
                ? Sort.by(sortParams[0]).descending()
                : Sort.by(sortParams[0]).ascending();
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<Transaction> result = service.getHistory(username, status, amount, sender, receiver, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Admin query completed successfully", result));
    }

    @Operation(summary = "Get administrator dashboard metrics", description = "Retrieves statistics for all transactions. Restricted to ADMIN.")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        log.info("Admin dashboard statistics request received");
        AdminStatsResponse stats = service.getStats();
        return ResponseEntity.ok(ApiResponse.success("Dashboard metrics retrieved successfully", stats));
    }

    private List<Transaction> getHistoryListForExport() {
        String username = getLoggedInUsername();
        if (isCurrentUserAdmin()) {
            return service.getAllTransactions();
        } else {
            return service.getHistory(username, null, null, null, null, null, Pageable.unpaged()).getContent();
        }
    }

    @Operation(summary = "Export history as CSV", description = "Downloads payment history as a CSV file. Admins get all records; users get their own.")
    @GetMapping("/export/csv")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportCSV(HttpServletResponse response) {
        log.info("Export CSV request received");
        List<Transaction> list = getHistoryListForExport();
        service.exportHistoryToCSV(list, response);
    }

    @Operation(summary = "Export history as XLSX Excel", description = "Downloads payment history as an Excel spreadsheet. Admins get all records; users get their own.")
    @GetMapping("/export/excel")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportExcel(HttpServletResponse response) {
        log.info("Export Excel request received");
        List<Transaction> list = getHistoryListForExport();
        service.exportHistoryToExcel(list, response);
    }

    @Operation(summary = "Export history as PDF", description = "Downloads payment history as a PDF report. Admins get all records; users get their own.")
    @GetMapping("/export/pdf")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public void exportPDF(HttpServletResponse response) {
        log.info("Export PDF request received");
        List<Transaction> list = getHistoryListForExport();
        service.exportHistoryToPDF(list, response);
    }
}