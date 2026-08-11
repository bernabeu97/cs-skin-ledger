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

@Entity
@Table(name = "market_index_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class MarketIndexSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(name = "index_value", nullable = false, precision = 18, scale = 6)
    private BigDecimal indexValue;

    @Column(name = "market_value", precision = 18, scale = 4)
    private BigDecimal marketValue;

    @Column(name = "composition_hash", nullable = false, length = 64)
    private String compositionHash;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
