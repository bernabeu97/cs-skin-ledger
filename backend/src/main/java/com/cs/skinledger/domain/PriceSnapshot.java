package com.cs.skinledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 平台价格快照（steam/uu/buff），只增不改，用于历史行情与最新价计算。
 */
@Entity
@Table(name = "price_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** 平台：steam / uu / buff */
    @Column(nullable = false, length = 16)
    private String platform;

    /** 当前最低出售价（CNY） */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "buy_price", precision = 18, scale = 4)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", precision = 18, scale = 4)
    private BigDecimal sellPrice;

    @Column
    private Integer volume;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}