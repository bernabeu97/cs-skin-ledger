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
 * 其他收支：会员费、平台服务费、赔偿支出/收入、退款等非饰品资金项。
 */
@Entity
@Table(name = "other_cost_entries")
@Getter
@Setter
@NoArgsConstructor
public class OtherCostEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 分类：membership / platform_fee / compensation_expense / compensation_income / refund / other */
    @Column(nullable = false, length = 32)
    private String category;

    /** expense=支出 / income=收入 */
    @Column(nullable = false, length = 8)
    private String direction;

    /** 金额（正数） */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(length = 16)
    private String platform;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(length = 500)
    private String note;

    @Column(name = "source_ref", length = 128)
    private String sourceRef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}