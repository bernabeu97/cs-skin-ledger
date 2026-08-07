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
 * 价格提醒：某物品某平台价格高于(gt)/低于(lt)阈值时触发一次（重置后可再次触发）。
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, length = 16)
    private String platform;

    /** gt=价格高于阈值，lt=价格低于阈值 */
    @Column(name = "`condition`", nullable = false, length = 8)
    private String condition;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;
}