package com.upi.offline.simulator;

import com.upi.offline.entity.Transaction;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class NetworkSimulator {

    private final Random random = new Random();

    public Transaction simulate(Transaction transaction) {

        int hops = random.nextInt(5) + 1;

        transaction.setHopCount(hops);

        if (hops <= 2) {
            transaction.setStatus("PENDING");
        } else {
            transaction.setStatus("WAITING_FOR_SYNC");
        }

        return transaction;
    }
}