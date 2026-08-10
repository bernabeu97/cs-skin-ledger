package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long>, JpaSpecificationExecutor<Lot> {
    List<Lot> findByUserIdOrderByBuyTimeAsc(Long userId);

    Optional<Lot> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSourceRef(Long userId, String sourceRef);
}
