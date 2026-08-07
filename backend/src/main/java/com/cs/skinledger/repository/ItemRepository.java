package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByMarketHashName(String marketHashName);
}