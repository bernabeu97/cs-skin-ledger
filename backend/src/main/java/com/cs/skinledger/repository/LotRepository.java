package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.time.LocalDateTime;

public interface LotRepository extends JpaRepository<Lot, Long>, JpaSpecificationExecutor<Lot> {
    List<Lot> findByUserIdOrderByBuyTimeAsc(Long userId);

    List<Lot> findByUserIdAndDeletedAtIsNullOrderByBuyTimeAsc(Long userId);

    List<Lot> findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long userId);

    Optional<Lot> findByIdAndUserId(Long id, Long userId);

    Optional<Lot> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Lot> findByUserIdAndSourceRefIn(Long userId, Collection<String> sourceRefs);

    List<Lot> findByUserIdAndSourceRefStartingWith(Long userId, String prefix);

    boolean existsByUserIdAndSourceRef(Long userId, String sourceRef);

    long deleteByDeletedAtBefore(LocalDateTime cutoff);
}
