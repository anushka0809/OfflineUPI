package com.upi.offline.repository;

import com.upi.offline.entity.BillReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillReminderRepository extends JpaRepository<BillReminder, Long> {
    List<BillReminder> findByUsernameAndActiveTrue(String username);
    Optional<BillReminder> findByIdAndUsername(Long id, String username);
}
