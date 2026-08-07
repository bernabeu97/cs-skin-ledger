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

    @Column(length = 64)
    private String category;

    @Column(length = 16)
    private String exterior;

    @Column(name = "stat_trak")
    private Boolean statTrak;

    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    @Column(length = 32)
    private String source;
}