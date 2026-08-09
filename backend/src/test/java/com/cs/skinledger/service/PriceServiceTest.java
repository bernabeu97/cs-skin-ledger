package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.PriceSnapshot;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.PortfolioValuation;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class PriceServiceTest {

    @Autowired
    private PriceService priceService;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private com.cs.skinledger.repository.AlertRepository alertRepository;

    @Autowired
    private com.cs.skinledger.repository.OtherCostRepository otherCostRepository;

    @Autowired
    private com.cs.skinledger.repository.SettingRepository settingRepository;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;
    @Autowired
    private UserRepository userRepository;

    private Item item;

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        settingRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("local");
        userRepository.save(user);
        item = new Item();
        item.setMarketHashName("AK-47 | Test Camo (Field-Tested)");
        item.setSource("manual");
        item = itemRepository.save(item);
    }

    private Lot holdingLot(BigDecimal qty, BigDecimal buyPrice) {
        Lot lot = new Lot();
        lot.setItem(item);
        lot.setUser(userRepository.findByUsername("local").orElseThrow());
        lot.setQuantity(qty);
        lot.setBuyPrice(buyPrice);
        lot.setBuyTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        lot.setBuyPlatform("uu");
        return lotRepository.save(lot);
    }

    private void snapshot(String platform, String price) {
        PriceSnapshot ps = new PriceSnapshot();
        ps.setItem(item);
        ps.setPlatform(platform);
        ps.setPrice(new BigDecimal(price));
        ps.setCurrency("CNY");
        ps.setFetchedAt(LocalDateTime.now());
        snapshotRepository.save(ps);
    }

    @Test
    void valuationPrefersUuPriceAndComputesUnrealized() {
        holdingLot(new BigDecimal("2"), new BigDecimal("100"));
        snapshot("steam", "95");
        snapshot("buff", "98");
        snapshot("uu", "110");

        PortfolioValuation v = priceService.valuation();

        assertEquals(0, new BigDecimal("200").compareTo(v.holdingCost()));
        assertEquals(0, new BigDecimal("220").compareTo(v.marketValue()));
        assertEquals(0, new BigDecimal("20").compareTo(v.unrealizedPnl()));
        assertEquals(1, v.rows().size());
        assertEquals("uu", v.rows().get(0).pricePlatform());
        assertEquals(0, new BigDecimal("110").compareTo(v.rows().get(0).currentPrice()));
        assertEquals(0, new BigDecimal("20").compareTo(v.rows().get(0).unrealizedPnl()));
    }

    @Test
    void valuationWithoutSnapshotReturnsNullPrice() {
        holdingLot(new BigDecimal("1"), new BigDecimal("50"));

        PortfolioValuation v = priceService.valuation();

        assertEquals(0, new BigDecimal("50").compareTo(v.holdingCost()));
        assertNull(v.rows().get(0).currentPrice());
        assertNull(v.rows().get(0).unrealizedPnl());
        assertEquals(BigDecimal.ZERO, v.marketValue());
    }

    @Test
    void valuationUsesUuOnlyAndIgnoresSteamBuffFallback() {
        holdingLot(new BigDecimal("1"), new BigDecimal("100"));
        snapshot("steam", "95");
        snapshot("buff", "98");
        // 没有 uu 快照时，估值不回落 steam/buff
        PortfolioValuation v = priceService.valuation();
        assertNull(v.rows().get(0).currentPrice());
        assertNull(v.rows().get(0).unrealizedPnl());
        assertEquals(BigDecimal.ZERO, v.marketValue());
    }
}