package com.upi.offline.service;

import com.upi.offline.dto.MonthlySummaryResponse;
import com.upi.offline.dto.WalletResponse;
import com.upi.offline.entity.BillReminder;
import com.upi.offline.entity.Transaction;
import com.upi.offline.entity.User;
import com.upi.offline.exception.InsufficientBalanceException;
import com.upi.offline.exception.InvalidTransactionException;
import com.upi.offline.repository.BillReminderRepository;
import com.upi.offline.repository.TransactionRepository;
import com.upi.offline.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BillReminderRepository billReminderRepository;

    public WalletService(UserRepository userRepository,
                         TransactionRepository transactionRepository,
                         BillReminderRepository billReminderRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.billReminderRepository = billReminderRepository;
    }

    public WalletResponse getWallet(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidTransactionException("User not found: " + username));

        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> monthTx = transactionRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null
                        && !t.getCreatedAt().isBefore(monthStart)
                        && !t.getCreatedAt().isAfter(monthEnd))
                .toList();

        double spent = monthTx.stream()
                .filter(t -> username.equals(t.getSender()) && "SYNCED".equals(t.getStatus()))
                .mapToDouble(Transaction::getAmount)
                .sum();

        double received = monthTx.stream()
                .filter(t -> username.equals(t.getReceiver()) && "SYNCED".equals(t.getStatus()))
                .mapToDouble(Transaction::getAmount)
                .sum();

        return new WalletResponse(
                user.getUsername(),
                user.getUpiId(),
                user.getBalance(),
                spent,
                received
        );
    }

    @Transactional
    public void debit(String username, double amount) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidTransactionException("User not found: " + username));

        if (user.getBalance() == null || user.getBalance() < amount) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Available: ₹%.2f, Required: ₹%.2f",
                            user.getBalance() != null ? user.getBalance() : 0.0, amount));
        }

        user.setBalance(Math.round((user.getBalance() - amount) * 100.0) / 100.0);
        userRepository.save(user);
        log.info("Debited ₹{} from user {}", amount, username);
    }

    @Transactional
    public void credit(String username, double amount) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidTransactionException("User not found: " + username));

        double current = user.getBalance() != null ? user.getBalance() : 0.0;
        user.setBalance(Math.round((current + amount) * 100.0) / 100.0);
        userRepository.save(user);
        log.info("Credited ₹{} to user {}", amount, username);
    }

    public void validateReceiver(String receiver, String sender) {
        if (receiver == null || receiver.trim().isEmpty()) {
            throw new InvalidTransactionException("Receiver cannot be empty");
        }
        if (receiver.equalsIgnoreCase(sender)) {
            throw new InvalidTransactionException("Cannot send money to yourself");
        }
        if (!userRepository.existsByUsername(receiver)) {
            throw new InvalidTransactionException("Receiver '" + receiver + "' does not exist");
        }
    }

    public List<String> listUsernames() {
        return userRepository.findAll().stream()
                .map(User::getUsername)
                .sorted()
                .collect(Collectors.toList());
    }

    public MonthlySummaryResponse getMonthlySummary(String username) {
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime monthStart = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> userTx = transactionRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null
                        && !t.getCreatedAt().isBefore(monthStart)
                        && !t.getCreatedAt().isAfter(monthEnd)
                        && (username.equals(t.getSender()) || username.equals(t.getReceiver())))
                .toList();

        double spent = userTx.stream()
                .filter(t -> username.equals(t.getSender()) && "SYNCED".equals(t.getStatus()))
                .mapToDouble(Transaction::getAmount)
                .sum();

        double received = userTx.stream()
                .filter(t -> username.equals(t.getReceiver()) && "SYNCED".equals(t.getStatus()))
                .mapToDouble(Transaction::getAmount)
                .sum();

        Map<String, Double> categoryBreakdown = new HashMap<>();
        userTx.stream()
                .filter(t -> username.equals(t.getSender()) && "SYNCED".equals(t.getStatus()))
                .forEach(t -> {
                    String cat = t.getCategory() != null ? t.getCategory() : "OTHER";
                    categoryBreakdown.merge(cat, t.getAmount(), Double::sum);
                });

        Map<String, Long> dailyActivity = userTx.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        Collectors.counting()
                ));

        MonthlySummaryResponse summary = new MonthlySummaryResponse();
        summary.setMonth(currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        summary.setTotalSpent(spent);
        summary.setTotalReceived(received);
        summary.setTransactionCount(userTx.size());
        summary.setCategoryBreakdown(categoryBreakdown);
        summary.setDailyActivity(dailyActivity);
        return summary;
    }

    @Transactional
    public BillReminder createBillReminder(String username, String title, Double amount,
                                           String category, int dueDay) {
        BillReminder bill = new BillReminder();
        bill.setUsername(username);
        bill.setTitle(title);
        bill.setAmount(amount);
        bill.setCategory(category);
        bill.setDueDay(dueDay);
        return billReminderRepository.save(bill);
    }

    public List<BillReminder> getBillReminders(String username) {
        return billReminderRepository.findByUsernameAndActiveTrue(username);
    }

    @Transactional
    public void deleteBillReminder(Long id, String username) {
        BillReminder bill = billReminderRepository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new InvalidTransactionException("Bill reminder not found"));
        bill.setActive(false);
        billReminderRepository.save(bill);
    }

    @Transactional
    public User initializeNewUser(User user) {
        if (user.getBalance() == null) {
            user.setBalance(10000.0);
        }
        if (user.getUpiId() == null) {
            user.setUpiId(user.getUsername() + "@offlineupi");
        }
        return user;
    }
}
