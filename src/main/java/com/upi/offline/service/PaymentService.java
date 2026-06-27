package com.upi.offline.service;

import com.upi.offline.encryption.AESEncryption;
import com.upi.offline.entity.Transaction;
import com.upi.offline.repository.TransactionRepository;
import com.upi.offline.simulator.NetworkSimulator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentService {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private NetworkSimulator simulator;

    public Transaction save(Transaction transaction) {

        String payload = transaction.getSender()
                + "|"
                + transaction.getReceiver()
                + "|"
                + transaction.getAmount();

        String encrypted = AESEncryption.encrypt(payload);

        transaction.setEncryptedPayload(encrypted);

        simulator.simulate(transaction);

        return repository.save(transaction);
    }

    public List<Transaction> getAllTransactions() {
        return repository.findAll();
    }

    public Transaction getTransaction(Long id) {
        return repository.findById(id).orElse(null);
    }

    public List<Transaction> syncPendingTransactions() {

        List<Transaction> pending =
                repository.findByStatus("WAITING_FOR_SYNC");

        for (Transaction transaction : pending) {
            transaction.setStatus("SYNCED");
        }

        repository.saveAll(pending);

        return pending;
    }
}