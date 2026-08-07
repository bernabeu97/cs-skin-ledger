package com.cs.skinledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台商品 ID 映射：完整市场名（含磨损后缀）对应 UU/BUFF/CS.Money 的商品 ID。
 * 数据源：https://github.com/chinap/buff163-ids (cs2_marketplaceids.json)
 */
@Entity
@Table(name = "marketplace_ids")
@Getter
@Setter
@NoArgsConstructor
public class MarketplaceId {

    @Id
    @Column(name = "market_hash_name", nullable = false, length = 255)
    private String marketHashName;

    @Column(name = "youpin_id")
    private Long youpinId;

    @Column(name = "buff_goods_id")
    private Long buffGoodsId;

    @Column(name = "buffmarket_goods_id")
    private Long buffmarketGoodsId;

    @Column(name = "csmoney_nameid")
    private Long csmoneyNameid;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}