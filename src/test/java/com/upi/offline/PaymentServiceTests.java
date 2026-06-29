package com.upi.offline;

import com.upi.offline.encryption.AESEncryption;
import com.upi.offline.entity.Transaction;
import com.upi.offline.exception.InvalidTransactionException;
import com.upi.offline.repository.TransactionRepository;
import com.upi.offline.service.PaymentService;
import com.upi.offline.service.WalletService;
import com.upi.offline.simulator.NetworkSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTests {

    @Mock
    private TransactionRepository repository;

    @Mock
    private NetworkSimulator simulator;

    @Mock
    private AESEncryption aesEncryption;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private PaymentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSaveSuccessfulPayment() {
        Transaction tx = new Transaction();
        tx.setTransactionId("uuid-1234");
        tx.setReceiver("Bob");
        tx.setAmount(100.0);

        when(repository.existsByTransactionId("uuid-1234")).thenReturn(false);
        when(aesEncryption.encrypt(anyString())).thenReturn("mocked-encrypted-payload");
        when(simulator.simulate(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setStatus("PENDING");
            t.setHopCount(2);
            return t;
        });
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction saved = service.save(tx, "Alice");

        assertNotNull(saved);
        assertEquals("Alice", saved.getSender());
        assertEquals("PENDING", saved.getStatus());
        verify(walletService, times(1)).debit("Alice", 100.0);
        verify(repository, times(1)).save(any(Transaction.class));
    }

    @Test
    void testSaveAmountLessThanZeroThrowsException() {
        Transaction tx = new Transaction();
        tx.setTransactionId("uuid-1234");
        tx.setReceiver("Bob");
        tx.setAmount(-50.0);

        assertThrows(InvalidTransactionException.class, () -> service.save(tx, "Alice"));
        verify(repository, never()).save(any(Transaction.class));
    }

    @Test
    void testRetryPaymentSuccess() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setSender("Alice");
        tx.setReceiver("Bob");
        tx.setStatus("FAILED");
        tx.setRetryCount(3);

        when(repository.findById(1L)).thenReturn(Optional.of(tx));
        when(repository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction retried = service.retryPayment(1L, "Alice", false);

        assertEquals("WAITING_FOR_SYNC", retried.getStatus());
        assertEquals(0, retried.getRetryCount());
    }
}
