package com.upi.offline.scheduler;

import com.upi.offline.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final PaymentService paymentService;

    public SyncScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedRate = 30000)
    public void scheduleSync() {
        log.info("Background synchronization job started by scheduler");
        try {
            paymentService.syncPendingTransactions();
        } catch (Exception e) {
            log.error("Error occurred during background transaction synchronization: {}", e.getMessage(), e);
        }
    }
}
