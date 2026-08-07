package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {
    List<Trade> findByUserIdOrderByTradedAtAsc(Long userId);

    List<Trade> findByUserIdOrderByTradedAtDesc(Long userId);
}