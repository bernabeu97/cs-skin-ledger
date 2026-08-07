package com.cs.skinledger.repository;

import com.cs.skinledger.domain.MarketplaceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceIdRepository extends JpaRepository<MarketplaceId, String> {
}