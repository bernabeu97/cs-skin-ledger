package com.cs.skinledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "items")
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_hash_name", nullable = false, unique = true, length = 255)
    private String marketHashName;

    @Column(name = "name_zh", length = 255)
    private String nameZh;

    @Column(length = 64)
    private String category;

    @Column(length = 64)
    private String weapon;

    @Column(length = 16)
    private String exterior;

    @Column(name = "min_float", precision = 8, scale = 4)
    private BigDecimal minFloat;

    @Column(name = "max_float", precision = 8, scale = 4)
    private BigDecimal maxFloat;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> wears;

    @Column(name = "stat_trak")
    private Boolean statTrak;

    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    @Column(length = 32)
    private String source;

    @Column(name = "external_id", length = 64)
    private String externalId;
}