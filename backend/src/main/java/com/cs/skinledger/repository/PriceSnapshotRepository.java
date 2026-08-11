package com.cs.skinledger.repository;

import com.cs.skinledger.domain.PriceSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    /** 指定物品集合内，每个 item+exterior+platform 取最近一条快照 */
    @Query("""
            select ps from PriceSnapshot ps
            where ps.item.id in :itemIds
              and ps.fetchedAt = (
                select max(ps2.fetchedAt) from PriceSnapshot ps2
                where ps2.item.id = ps.item.id
                  and ps2.platform = ps.platform
                  and (ps2.exterior = ps.exterior or (ps2.exterior is null and ps.exterior is null))
              )
            """)
    List<PriceSnapshot> findLatestByItemIds(@Param("itemIds") List<Long> itemIds);

    @Query("""
            select ps from PriceSnapshot ps
            where ps.item.id in :itemIds
              and ps.fetchedAt <= :cutoff
              and ps.fetchedAt = (
                select max(ps2.fetchedAt) from PriceSnapshot ps2
                where ps2.item.id = ps.item.id
                  and ps2.platform = ps.platform
                  and (ps2.exterior = ps.exterior or (ps2.exterior is null and ps.exterior is null))
                  and ps2.fetchedAt <= :cutoff
              )
            """)
    List<PriceSnapshot> findLatestAtOrBeforeByItemIds(
            @Param("itemIds") List<Long> itemIds,
            @Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select ps from PriceSnapshot ps
            where ps.item.id = :itemId
              and ps.platform = :platform
              and (ps.exterior = :exterior or (ps.exterior is null and :exterior is null))
              and ps.fetchedAt >= :from
            order by ps.fetchedAt asc
            """)
    List<PriceSnapshot> findHistory(
            @Param("itemId") Long itemId,
            @Param("exterior") String exterior,
            @Param("platform") String platform,
            @Param("from") LocalDateTime from);

    void deleteByFetchedAtBefore(LocalDateTime cutoff);

    /** 单物品单平台历史（分页） */
    List<PriceSnapshot> findByItemIdAndPlatformOrderByFetchedAtDesc(Long itemId, String platform, Pageable pageable);
}
