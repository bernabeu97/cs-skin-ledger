package com.cs.skinledger.repository;

import com.cs.skinledger.domain.PriceSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    /** 指定物品集合内，每个 item+platform 取最近一条快照 */
    @Query("""
            select ps from PriceSnapshot ps
            where ps.item.id in :itemIds
              and ps.fetchedAt = (
                select max(ps2.fetchedAt) from PriceSnapshot ps2
                where ps2.item.id = ps.item.id and ps2.platform = ps.platform
              )
            """)
    List<PriceSnapshot> findLatestByItemIds(@Param("itemIds") List<Long> itemIds);

    /** 单物品单平台历史（分页） */
    List<PriceSnapshot> findByItemIdAndPlatformOrderByFetchedAtDesc(Long itemId, String platform, Pageable pageable);
}