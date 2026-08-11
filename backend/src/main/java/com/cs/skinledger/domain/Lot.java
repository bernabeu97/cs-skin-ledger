package com.cs.skinledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(length = 16)
    private String exterior;

    @Column(name = "float_value", precision = 21, scale = 19)
    private BigDecimal floatValue;

    @Column(name = "buy_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal buyPrice;

    @Column(name = "buy_time", nullable = false)
    private LocalDateTime buyTime;

    @Column(name = "buy_platform", nullable = false, length = 16)
    private String buyPlatform;

    @Column(name = "sell_price", precision = 18, scale = 4)
    private BigDecimal sellPrice;

    @Column(name = "sell_time")
    private LocalDateTime sellTime;

    @Column(name = "sell_platform", length = 16)
    private String sellPlatform;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "actual_income", precision = 18, scale = 4)
    private BigDecimal actualIncome;

    @Column(precision = 18, scale = 4)
    private BigDecimal profit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LotStatus status = LotStatus.HOLDING;

    @Column(length = 500)
    private String note;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
