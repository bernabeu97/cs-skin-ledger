package com.cs.skinledger.repository;

import com.cs.skinledger.domain.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {
    List<WatchlistEntry> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<WatchlistEntry> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndItemIdAndExterior(Long userId, Long itemId, String exterior);

    long countByUserId(Long userId);
}
