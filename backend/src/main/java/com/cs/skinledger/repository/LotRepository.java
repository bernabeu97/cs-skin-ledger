package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface LotRepository extends JpaRepository<Lot, Long>, JpaSpecificationExecutor<Lot> {
    List<Lot> findByUserIdOrderByBuyTimeAsc(Long userId);

    boolean existsBySourceRef(String sourceRef);
}