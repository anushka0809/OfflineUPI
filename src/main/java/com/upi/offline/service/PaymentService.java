package com.upi.offline.service;

import com.upi.offline.dto.AdminStatsResponse;
import com.upi.offline.dto.PaymentResponse;
import com.upi.offline.dto.SplitExpenseRequest;
import com.upi.offline.dto.SplitExpenseResponse;
import com.upi.offline.encryption.AESEncryption;
import com.upi.offline.entity.Transaction;
import com.upi.offline.exception.InvalidTransactionException;
import com.upi.offline.exception.TransactionNotFoundException;
import com.upi.offline.exception.UnauthorizedAccessException;
import com.upi.offline.repository.TransactionRepository;
import com.upi.offline.repository.TransactionSpecifications;
import com.upi.offline.simulator.NetworkSimulator;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository repository;
    private final NetworkSimulator simulator;
    private final AESEncryption aesEncryption;
    private final WalletService walletService;

    public PaymentService(TransactionRepository repository, NetworkSimulator simulator,
                          AESEncryption aesEncryption, WalletService walletService) {
        this.repository = repository;
        this.simulator = simulator;
        this.aesEncryption = aesEncryption;
        this.walletService = walletService;
    }

    public void verifyTransactionAccess(Transaction transaction, String username, boolean isAdmin) {
        if (isAdmin) return;
        if (!username.equals(transaction.getSender()) && !username.equals(transaction.getReceiver())) {
            throw new UnauthorizedAccessException("You do not have access to this transaction");
        }
    }

    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public Transaction save(Transaction transaction, String loggedInUser) {
        log.info("Attempting to save transaction with ID: {}", transaction.getTransactionId());

        if (transaction.getAmount() == null || transaction.getAmount() <= 0) {
            throw new InvalidTransactionException("Transaction amount must be greater than zero");
        }

        transaction.setSender(loggedInUser);
        walletService.validateReceiver(transaction.getReceiver(), loggedInUser);

        if (repository.existsByTransactionId(transaction.getTransactionId())) {
            throw new InvalidTransactionException("Duplicate transaction ID detected");
        }

        walletService.debit(loggedInUser, transaction.getAmount());
        transaction.setSenderDebited(true);

        String payload = transaction.getSender() + "|" + transaction.getReceiver() + "|" + transaction.getAmount();
        transaction.setEncryptedPayload(aesEncryption.encrypt(payload));
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        if (transaction.getTransactionType() == null) {
            transaction.setTransactionType("TRANSFER");
        }
        if (transaction.getCategory() == null) {
            transaction.setCategory("OTHER");
        }

        simulator.simulate(transaction);
        Transaction saved = repository.save(transaction);

        log.info("Transaction saved. ID: {}, Status: {}, Hops: {}", saved.getId(), saved.getStatus(), saved.getHopCount());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "stats", allEntries = true)
    public SplitExpenseResponse splitExpense(SplitExpenseRequest request, String creator) {
        List<String> participants = request.getParticipants().stream()
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (participants.isEmpty()) {
            throw new InvalidTransactionException("At least one valid participant is required");
        }

        for (String participant : participants) {
            if (participant.equalsIgnoreCase(creator)) {
                throw new InvalidTransactionException("Cannot include yourself as a participant");
            }
            walletService.validateReceiver(participant, creator);
        }

        int totalPeople = participants.size() + 1;
        double share = Math.round((request.getTotalAmount() / totalPeople) * 100.0) / 100.0;
        String description = request.getDescription() != null ? request.getDescription() : "Split expense";
        String category = request.getCategory() != null ? request.getCategory() : "SPLIT";

        List<PaymentResponse> created = new ArrayList<>();
        for (String participant : participants) {
            Transaction tx = new Transaction();
            tx.setTransactionId(UUID.randomUUID().toString());
            tx.setReceiver(creator);
            tx.setAmount(share);
            tx.setTransactionType("SPLIT");
            tx.setCategory(category);
            tx.setNote(description + " (split share)");
            tx.setStatus("PENDING");
            tx.setHopCount(0);

            Transaction saved = save(tx, participant);
            created.add(new PaymentResponse(saved.getTransactionId(), saved.getStatus(), saved.getHopCount()));
        }

        return new SplitExpenseResponse(description, request.getTotalAmount(), share, totalPeople, created);
    }

    public List<Transaction> getAllTransactions() {
        return repository.findAll();
    }

    @Cacheable(value = "transactions", key = "#id")
    public Transaction getTransaction(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction with ID " + id + " not found"));
    }

    public String getPaymentStatus(Long id) {
        return getTransaction(id).getStatus();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stats", allEntries = true),
        @CacheEvict(value = "transactions", key = "#id")
    })
    public Transaction retryPayment(Long id, String username, boolean isAdmin) {
        Transaction transaction = getTransaction(id);
        verifyTransactionAccess(transaction, username, isAdmin);

        if (!"FAILED".equals(transaction.getStatus())) {
            throw new InvalidTransactionException("Only FAILED transactions can be retried.");
        }

        transaction.setStatus("WAITING_FOR_SYNC");
        transaction.setRetryCount(0);
        transaction.setFailureReason(null);
        transaction.setUpdatedAt(LocalDateTime.now());
        return repository.save(transaction);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stats", allEntries = true),
        @CacheEvict(value = "transactions", key = "#id")
    })
    public Transaction cancelPayment(Long id, String username, boolean isAdmin) {
        Transaction transaction = getTransaction(id);
        verifyTransactionAccess(transaction, username, isAdmin);

        if (!"PENDING".equals(transaction.getStatus()) && !"WAITING_FOR_SYNC".equals(transaction.getStatus())) {
            throw new InvalidTransactionException("Only PENDING or WAITING_FOR_SYNC transactions can be cancelled.");
        }

        refundSenderIfNeeded(transaction);
        transaction.setStatus("REJECTED");
        transaction.setFailureReason("Cancelled by user");
        transaction.setUpdatedAt(LocalDateTime.now());
        return repository.save(transaction);
    }

    private void refundSenderIfNeeded(Transaction transaction) {
        if (transaction.isSenderDebited() && !transaction.isReceiverCredited()) {
            walletService.credit(transaction.getSender(), transaction.getAmount());
            transaction.setSenderDebited(false);
            log.info("Refunded ₹{} to {}", transaction.getAmount(), transaction.getSender());
        }
    }

    private void settleTransaction(Transaction transaction) {
        if ("SYNCED".equals(transaction.getStatus()) && !transaction.isReceiverCredited()) {
            walletService.credit(transaction.getReceiver(), transaction.getAmount());
            transaction.setReceiverCredited(true);
            log.info("Settled ₹{} to {}", transaction.getAmount(), transaction.getReceiver());
        }
    }

    @Transactional
    @CacheEvict(value = {"stats", "transactions"}, allEntries = true)
    public List<Transaction> syncPendingTransactions() {
        log.info("Background synchronization batch execution started");

        List<Transaction> pending = new ArrayList<>();
        pending.addAll(repository.findByStatus("WAITING_FOR_SYNC"));
        pending.addAll(repository.findByStatus("PENDING"));

        log.info("Found {} transactions to sync", pending.size());

        for (Transaction transaction : pending) {
            try {
                if (Math.random() < 0.15) {
                    throw new RuntimeException("Simulated network timeout");
                }

                transaction.setStatus("SYNCED");
                transaction.setSyncTime(LocalDateTime.now());
                transaction.setUpdatedAt(LocalDateTime.now());
                settleTransaction(transaction);
                log.info("Transaction {} synced successfully", transaction.getTransactionId());
            } catch (Exception e) {
                int currentRetry = transaction.getRetryCount() + 1;
                transaction.setRetryCount(currentRetry);
                transaction.setUpdatedAt(LocalDateTime.now());
                log.warn("Sync failed for transaction {}. Attempt: {}", transaction.getTransactionId(), currentRetry);

                if (currentRetry >= 3) {
                    transaction.setStatus("FAILED");
                    transaction.setFailureReason("Synchronization failed after 3 retries: " + e.getMessage());
                    refundSenderIfNeeded(transaction);
                    log.error("Transaction {} marked as FAILED", transaction.getTransactionId());
                } else {
                    transaction.setFailureReason("Synchronization failed: " + e.getMessage());
                }
            }
        }

        return repository.saveAll(pending);
    }

    public Page<Transaction> getHistory(String username, String status, Double amount, String sender,
                                       String receiver, String search, Pageable pageable) {
        Specification<Transaction> spec = Specification.where(TransactionSpecifications.isSenderOrReceiver(username));

        if (status != null && !status.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasStatus(status));
        }
        if (amount != null) {
            spec = spec.and(TransactionSpecifications.hasAmount(amount));
        }
        if (sender != null && !sender.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasSender(sender));
        }
        if (receiver != null && !receiver.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasReceiver(receiver));
        }
        if (search != null && !search.isEmpty()) {
            spec = spec.and(TransactionSpecifications.searchBy(search));
        }

        return repository.findAll(spec, pageable);
    }

    public Page<Transaction> getHistoryAdmin(String username, String status, Double amount, String sender,
                                             String receiver, String search, Pageable pageable) {
        Specification<Transaction> spec = Specification.where(null);

        if (username != null && !username.isEmpty()) {
            spec = spec.and(TransactionSpecifications.isSenderOrReceiver(username));
        }
        if (status != null && !status.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasStatus(status));
        }
        if (amount != null) {
            spec = spec.and(TransactionSpecifications.hasAmount(amount));
        }
        if (sender != null && !sender.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasSender(sender));
        }
        if (receiver != null && !receiver.isEmpty()) {
            spec = spec.and(TransactionSpecifications.hasReceiver(receiver));
        }
        if (search != null && !search.isEmpty()) {
            spec = spec.and(TransactionSpecifications.searchBy(search));
        }

        return repository.findAll(spec, pageable);
    }

    @Cacheable(value = "stats")
    public AdminStatsResponse getStats() {
        AdminStatsResponse stats = new AdminStatsResponse();

        long total = repository.count();
        long pending = repository.countByStatus("PENDING");
        long waiting = repository.countByStatus("WAITING_FOR_SYNC");
        long synced = repository.countByStatus("SYNCED");
        long failed = repository.countByStatus("FAILED");
        long rejected = repository.countByStatus("REJECTED");

        Double totalAmountVal = repository.sumAmountByStatus("SYNCED");
        double totalAmount = totalAmountVal != null ? totalAmountVal : 0.0;
        double successRate = total > 0 ? ((double) synced / total) * 100.0 : 0.0;

        List<Transaction> transactions = repository.findAll();

        Map<String, Long> daily = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate().toString(),
                        Collectors.counting()
                ));

        Map<String, Long> monthly = transactions.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().getYear() + "-" + String.format("%02d", t.getCreatedAt().getMonthValue()),
                        Collectors.counting()
                ));

        stats.setTotalTransactions(total);
        stats.setPendingCount(pending + waiting);
        stats.setSyncedCount(synced);
        stats.setFailedCount(failed + rejected);
        stats.setSuccessRate(successRate);
        stats.setTotalAmount(totalAmount);
        stats.setDailyTransactions(daily);
        stats.setMonthlyTransactions(monthly);

        return stats;
    }

    public void exportHistoryToCSV(List<Transaction> list, HttpServletResponse response) {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"payment_history.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("Transaction ID,Sender,Receiver,Amount,Status,Type,Category,Hop Count,Created At,Sync Time,Failure Reason");
            for (Transaction t : list) {
                writer.println(String.format("%s,%s,%s,%.2f,%s,%s,%s,%d,%s,%s,%s",
                        t.getTransactionId(), t.getSender(), t.getReceiver(), t.getAmount(),
                        t.getStatus(), t.getTransactionType(),
                        t.getCategory() != null ? t.getCategory() : "",
                        t.getHopCount(), t.getCreatedAt(),
                        t.getSyncTime() != null ? t.getSyncTime() : "",
                        t.getFailureReason() != null ? t.getFailureReason() : ""));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error exporting transaction history to CSV", e);
        }
    }

    public void exportHistoryToExcel(List<Transaction> list, HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"payment_history.xlsx\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Payment History");
            Row header = sheet.createRow(0);
            String[] cols = {"Transaction ID", "Sender", "Receiver", "Amount", "Status", "Type", "Category", "Hop Count", "Created At", "Sync Time", "Failure Reason"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            int rowNum = 1;
            for (Transaction t : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getTransactionId());
                row.createCell(1).setCellValue(t.getSender());
                row.createCell(2).setCellValue(t.getReceiver());
                row.createCell(3).setCellValue(t.getAmount());
                row.createCell(4).setCellValue(t.getStatus());
                row.createCell(5).setCellValue(t.getTransactionType());
                row.createCell(6).setCellValue(t.getCategory() != null ? t.getCategory() : "");
                row.createCell(7).setCellValue(t.getHopCount());
                row.createCell(8).setCellValue(t.getCreatedAt().toString());
                row.createCell(9).setCellValue(t.getSyncTime() != null ? t.getSyncTime().toString() : "");
                row.createCell(10).setCellValue(t.getFailureReason() != null ? t.getFailureReason() : "");
            }

            workbook.write(response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("Error exporting transaction history to Excel", e);
        }
    }

    public void exportHistoryToPDF(List<Transaction> list, HttpServletResponse response) {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"payment_history.pdf\"");

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
            font.setSize(18);
            Paragraph p = new Paragraph("Offline UPI Payment History", font);
            p.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(p);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100f);
            table.setSpacingBefore(15);

            table.addCell("Transaction ID");
            table.addCell("Sender");
            table.addCell("Receiver");
            table.addCell("Amount");
            table.addCell("Status");
            table.addCell("Created At");

            for (Transaction t : list) {
                table.addCell(t.getTransactionId().substring(0, Math.min(8, t.getTransactionId().length())) + "...");
                table.addCell(t.getSender());
                table.addCell(t.getReceiver());
                table.addCell("₹" + t.getAmount());
                table.addCell(t.getStatus());
                table.addCell(t.getCreatedAt().toLocalDate().toString());
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error exporting transaction history to PDF", e);
        }
    }
}
