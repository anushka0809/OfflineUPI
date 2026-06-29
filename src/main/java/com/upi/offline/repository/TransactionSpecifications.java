package com.upi.offline.repository;

import com.upi.offline.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class TransactionSpecifications {

    public static Specification<Transaction> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(cb.upper(root.get("status")), status.toUpperCase());
    }

    public static Specification<Transaction> hasSender(String sender) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("sender")), "%" + sender.toLowerCase() + "%");
    }

    public static Specification<Transaction> hasReceiver(String receiver) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("receiver")), "%" + receiver.toLowerCase() + "%");
    }

    public static Specification<Transaction> isSenderOrReceiver(String username) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("sender"), username),
                cb.equal(root.get("receiver"), username)
        );
    }

    public static Specification<Transaction> hasAmount(Double amount) {
        return (root, query, cb) -> cb.equal(root.get("amount"), amount);
    }

    public static Specification<Transaction> createdAfter(LocalDateTime date) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), date);
    }

    public static Specification<Transaction> searchBy(String keyword) {
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("transactionId")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("sender")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("receiver")), "%" + keyword.toLowerCase() + "%")
        );
    }
}
