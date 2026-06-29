package com.upi.offline.service;

import com.upi.offline.encryption.AESEncryption;
import com.upi.offline.entity.Transaction;
import com.upi.offline.exception.InvalidTransactionException;
import com.upi.offline.exception.TransactionNotFoundException;
import com.upi.offline.repository.TransactionRepository;
import com.upi.offline.repository.TransactionSpecifications;
import com.upi.offline.simulator.NetworkSimulator;
import com.upi.offline.dto.AdminStatsResponse;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final TransactionRepository repository;
    private final NetworkSimulator simulator;
    private final AESEncryption aesEncryption;

    public PaymentService(TransactionRepository repository, NetworkSimulator simulator, AESEncryption aesEncryption) {
        this.repository = repository;
        this.simulator = simulator;
        this.aesEncryption = aesEncryption;
    }

    @CacheEvict(value = "stats", allEntries = true)
    public Transaction save(Transaction transaction) {
        log.info("Attempting to save transaction with ID: {}", transaction.getTransactionId());

        if (transaction.getAmount() == null || transaction.getAmount() <= 0) {
            log.error("Save failed: transaction amount must be greater than zero");
            throw new InvalidTransactionException("Transaction amount must be greater than zero");
        }
        if (transaction.getSender() == null || transaction.getSender().trim().isEmpty()) {
            log.error("Save failed: sender is empty");
            throw new InvalidTransactionException("Sender cannot be empty");
        }
        if (transaction.getReceiver() == null || transaction.getReceiver().trim().isEmpty()) {
            log.error("Save failed: receiver is empty");
            throw new InvalidTransactionException("Receiver cannot be empty");
        }

        if (repository.existsByTransactionId(transaction.getTransactionId())) {
            log.error("Save failed: duplicate transaction ID found: {}", transaction.getTransactionId());
            throw new InvalidTransactionException("Duplicate transaction ID detected");
        }

        String payload = transaction.getSender()
                + "|"
                + transaction.getReceiver()
                + "|"
                + transaction.getAmount();

        String encrypted = aesEncryption.encrypt(payload);
        transaction.setEncryptedPayload(encrypted);
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());

        simulator.simulate(transaction);
        Transaction saved = repository.save(transaction);
        
        log.info("Transaction saved successfully. ID: {}, Status: {}, Hops: {}", 
                saved.getId(), saved.getStatus(), saved.getHopCount());
        
        return saved;
    }

    public List<Transaction> getAllTransactions() {
        log.info("Fetching all transactions");
        List<Transaction> transactions = repository.findAll();
        log.info("Fetched {} transactions", transactions.size());
        return transactions;
    }

    @Cacheable(value = "transactions", key = "#id")
    public Transaction getTransaction(Long id) {
        log.info("Fetching transaction with database ID: {}", id);
        return repository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Transaction with ID {} not found", id);
                    return new TransactionNotFoundException("Transaction with ID " + id + " not found");
                });
    }

    public String getPaymentStatus(Long id) {
        log.info("Retrieving status for transaction ID: {}", id);
        Transaction transaction = getTransaction(id);
        return transaction.getStatus();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stats", allEntries = true),
        @CacheEvict(value = "transactions", key = "#id")
    })
    public Transaction retryPayment(Long id) {
        log.info("Request to retry failed transaction: {}", id);
        Transaction transaction = getTransaction(id);

        if (!"FAILED".equals(transaction.getStatus())) {
            log.warn("Retry rejected: Transaction {} status is {} (only FAILED transactions can be retried)", 
                    id, transaction.getStatus());
            throw new InvalidTransactionException("Only FAILED transactions can be retried.");
        }

        transaction.setStatus("WAITING_FOR_SYNC");
        transaction.setRetryCount(0);
        transaction.setFailureReason(null);
        transaction.setUpdatedAt(LocalDateTime.now());

        Transaction saved = repository.save(transaction);
        log.info("Transaction {} state reset to WAITING_FOR_SYNC for retry", id);
        return saved;
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stats", allEntries = true),
        @CacheEvict(value = "transactions", key = "#id")
    })
    public Transaction cancelPayment(Long id) {
        log.info("Request to cancel transaction: {}", id);
        Transaction transaction = getTransaction(id);

        if (!"PENDING".equals(transaction.getStatus()) && !"WAITING_FOR_SYNC".equals(transaction.getStatus())) {
            log.warn("Cancellation rejected: Transaction {} status is {} (only PENDING or WAITING_FOR_SYNC can be cancelled)", 
                    id, transaction.getStatus());
            throw new InvalidTransactionException("Only PENDING or WAITING_FOR_SYNC transactions can be cancelled.");
        }

        transaction.setStatus("REJECTED");
        transaction.setFailureReason("Cancelled by user");
        transaction.setUpdatedAt(LocalDateTime.now());

        Transaction saved = repository.save(transaction);
        log.info("Transaction {} successfully cancelled and marked as REJECTED", id);
        return saved;
    }

    @Transactional
    @CacheEvict(value = {"stats", "transactions"}, allEntries = true)
    public List<Transaction> syncPendingTransactions() {
        log.info("Background synchronization batch execution started");
        List<Transaction> pending = repository.findByStatus("WAITING_FOR_SYNC");
        log.info("Found {} pending transactions waiting for network sync", pending.size());

        for (Transaction transaction : pending) {
            try {
                // Simulate a 20% network synchronization failure rate
                if (Math.random() < 0.20) {
                    throw new RuntimeException("Simulated network timeout");
                }

                transaction.setStatus("SYNCED");
                transaction.setSyncTime(LocalDateTime.now());
                transaction.setUpdatedAt(LocalDateTime.now());
                log.info("Transaction {} synced successfully", transaction.getTransactionId());
            } catch (Exception e) {
                int currentRetry = transaction.getRetryCount() + 1;
                transaction.setRetryCount(currentRetry);
                transaction.setUpdatedAt(LocalDateTime.now());
                log.warn("Sync execution failed for transaction {}. Attempt: {}", 
                        transaction.getTransactionId(), currentRetry);

                if (currentRetry >= 3) {
                    transaction.setStatus("FAILED");
                    transaction.setFailureReason("Synchronization failed after 3 retries: " + e.getMessage());
                    log.error("Transaction {} marked as FAILED. Max retry limit exceeded", transaction.getTransactionId());
                } else {
                    transaction.setFailureReason("Synchronization failed: " + e.getMessage());
                }
            }
        }

        List<Transaction> saved = repository.saveAll(pending);
        log.info("Background sync batch complete");
        return saved;
    }

    public Page<Transaction> getHistory(String username, String status, Double amount, String sender, String receiver, String search, Pageable pageable) {
        log.info("Executing search/filter history query for user: {}", username);

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

    public Page<Transaction> getHistoryAdmin(String username, String status, Double amount, String sender, String receiver, String search, Pageable pageable) {
        log.info("Executing global admin history search query");

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
        log.info("Generating dashboard statistics");
        AdminStatsResponse stats = new AdminStatsResponse();

        long total = repository.count();
        long pending = repository.countByStatus("PENDING");
        long synced = repository.countByStatus("SYNCED");
        long failed = repository.countByStatus("FAILED");

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
        stats.setPendingCount(pending);
        stats.setSyncedCount(synced);
        stats.setFailedCount(failed);
        stats.setSuccessRate(successRate);
        stats.setTotalAmount(totalAmount);
        stats.setDailyTransactions(daily);
        stats.setMonthlyTransactions(monthly);

        log.info("Dashboard stats generated. Total: {}, Synced: {}, Success Rate: {}%", total, synced, successRate);
        return stats;
    }

    public void exportHistoryToCSV(List<Transaction> list, HttpServletResponse response) {
        log.info("Exporting {} transactions to CSV", list.size());
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"payment_history.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("Transaction ID,Sender,Receiver,Amount,Status,Hop Count,Created At,Sync Time,Failure Reason");
            for (Transaction t : list) {
                writer.println(String.format("%s,%s,%s,%.2f,%s,%d,%s,%s,%s",
                        t.getTransactionId(),
                        t.getSender(),
                        t.getReceiver(),
                        t.getAmount(),
                        t.getStatus(),
                        t.getHopCount(),
                        t.getCreatedAt(),
                        t.getSyncTime() != null ? t.getSyncTime() : "",
                        t.getFailureReason() != null ? t.getFailureReason() : ""));
            }
            log.info("CSV export completed successfully");
        } catch (Exception e) {
            log.error("Failed to export CSV: {}", e.getMessage(), e);
            throw new RuntimeException("Error exporting transaction history to CSV", e);
        }
    }

    public void exportHistoryToExcel(List<Transaction> list, HttpServletResponse response) {
        log.info("Exporting {} transactions to Excel", list.size());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"payment_history.xlsx\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Payment History");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Transaction ID");
            header.createCell(1).setCellValue("Sender");
            header.createCell(2).setCellValue("Receiver");
            header.createCell(3).setCellValue("Amount");
            header.createCell(4).setCellValue("Status");
            header.createCell(5).setCellValue("Hop Count");
            header.createCell(6).setCellValue("Created At");
            header.createCell(7).setCellValue("Sync Time");
            header.createCell(8).setCellValue("Failure Reason");

            int rowNum = 1;
            for (Transaction t : list) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(t.getTransactionId());
                row.createCell(1).setCellValue(t.getSender());
                row.createCell(2).setCellValue(t.getReceiver());
                row.createCell(3).setCellValue(t.getAmount());
                row.createCell(4).setCellValue(t.getStatus());
                row.createCell(5).setCellValue(t.getHopCount());
                row.createCell(6).setCellValue(t.getCreatedAt().toString());
                row.createCell(7).setCellValue(t.getSyncTime() != null ? t.getSyncTime().toString() : "");
                row.createCell(8).setCellValue(t.getFailureReason() != null ? t.getFailureReason() : "");
            }

            workbook.write(response.getOutputStream());
            log.info("Excel export completed successfully");
        } catch (Exception e) {
            log.error("Failed to export Excel: {}", e.getMessage(), e);
            throw new RuntimeException("Error exporting transaction history to Excel", e);
        }
    }

    public void exportHistoryToPDF(List<Transaction> list, HttpServletResponse response) {
        log.info("Exporting {} transactions to PDF", list.size());
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
                table.addCell(t.getTransactionId().substring(0, 8) + "...");
                table.addCell(t.getSender());
                table.addCell(t.getReceiver());
                table.addCell(String.valueOf(t.getAmount()));
                table.addCell(t.getStatus());
                table.addCell(t.getCreatedAt().toLocalDate().toString());
            }

            document.add(table);
            document.close();
            log.info("PDF export completed successfully");
        } catch (Exception e) {
            log.error("Failed to export PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Error exporting transaction history to PDF", e);
        }
    }
}