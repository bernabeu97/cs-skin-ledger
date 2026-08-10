package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    List<Alert> findByUserId(Long userId);

    Optional<Alert> findByIdAndUserId(Long id, Long userId);
}
