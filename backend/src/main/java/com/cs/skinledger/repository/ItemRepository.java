package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Item;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByMarketHashName(String marketHashName);

    Optional<Item> findByNameZh(String nameZh);

    @Query("""
            select i from Item i
            where i.nameZh like %:q% or i.marketHashName like %:q%
            order by i.nameZh
            """)
    List<Item> search(@Param("q") String q, Pageable pageable);
}