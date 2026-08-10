package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {
    List<Trade> findByUserIdOrderByTradedAtAsc(Long userId);

    List<Trade> findByUserIdOrderByTradedAtDesc(Long userId);

    Optional<Trade> findByIdAndUserId(Long id, Long userId);
}
