package com.cs.skinledger.repository;

import com.cs.skinledger.domain.OtherCostEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OtherCostRepository extends JpaRepository<OtherCostEntry, Long>, JpaSpecificationExecutor<OtherCostEntry> {

    List<OtherCostEntry> findByUserIdOrderByOccurredAtDesc(Long userId);

    Optional<OtherCostEntry> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndSourceRef(Long userId, String sourceRef);

    @Query("""
            select o.direction, coalesce(sum(o.amount), 0)
            from OtherCostEntry o
            where o.user.id = :userId
            group by o.direction
            """)
    List<Object[]> sumByDirection(@Param("userId") Long userId);

    @Query("""
            select o.category, o.direction, coalesce(sum(o.amount), 0)
            from OtherCostEntry o
            where o.user.id = :userId
            group by o.category, o.direction
            """)
    List<Object[]> sumByCategory(@Param("userId") Long userId);
}
