package com.cs.skinledger.repository;

import com.cs.skinledger.domain.MarketIndexSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketIndexSnapshotRepository extends JpaRepository<MarketIndexSnapshot, Long> {
    Optional<MarketIndexSnapshot> findTopByUserIdAndKindOrderByFetchedAtDesc(Long userId, String kind);

    List<MarketIndexSnapshot> findByUserIdAndKindAndFetchedAtGreaterThanEqualOrderByFetchedAtAsc(
            Long userId, String kind, LocalDateTime from);

    void deleteByFetchedAtBefore(LocalDateTime cutoff);
}
