package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.PriceSnapshot;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.MarketIndexView;
import com.cs.skinledger.dto.WatchlistCreateRequest;
import com.cs.skinledger.dto.WatchlistResponse;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.MarketIndexSnapshotRepository;
import com.cs.skinledger.repository.OtherCostRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.repository.WatchlistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "local")
class MarketFeaturesTest {
    @Autowired private WatchlistService watchlistService;
    @Autowired private MarketIndexService marketIndexService;
    @Autowired private PriceService priceService;
    @Autowired private WatchlistRepository watchlistRepository;
    @Autowired private MarketIndexSnapshotRepository indexRepository;
    @Autowired private PriceSnapshotRepository snapshotRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private OtherCostRepository otherCostRepository;
    @Autowired private SettingRepository settingRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private UserRepository userRepository;

    private Item item;
    private User user;

    @BeforeEach
    void setUp() {
        indexRepository.deleteAll();
        watchlistRepository.deleteAll();
        snapshotRepository.deleteAll();
        lotRepository.deleteAll();
        otherCostRepository.deleteAll();
        settingRepository.deleteAll();
        alertRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
        user = new User();
        user.setUsername("local");
        user = userRepository.save(user);
        item = new Item();
        item.setMarketHashName("AK-47 | Market Test");
        item.setNameZh("AK-47 | 行情测试");
        item.setSource("manual");
        item = itemRepository.save(item);
    }

    @AfterEach
    void tearDownNewRelations() {
        indexRepository.deleteAll();
        watchlistRepository.deleteAll();
    }

    @Test
    void watchlistUsesUuPriceAndRejectsDuplicateCombination() {
        snapshot("uu", "100", LocalDateTime.now().minusHours(25));
        snapshot("uu", "120", LocalDateTime.now());
        watchlistService.create(new WatchlistCreateRequest(item.getId(), null));

        List<WatchlistResponse> rows = watchlistService.list();

        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("120").compareTo(rows.getFirst().currentPrice()));
        assertEquals(0, new BigDecimal("20").compareTo(rows.getFirst().change24h()));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.create(new WatchlistCreateRequest(item.getId(), null)));
    }

    @Test
    void holdingsIndexCarriesForwardWhenQuantityChanges() {
        Lot lot = new Lot();
        lot.setUser(user);
        lot.setItem(item);
        lot.setQuantity(BigDecimal.ONE);
        lot.setBuyPrice(new BigDecimal("80"));
        lot.setBuyTime(LocalDateTime.now().minusDays(2));
        lot.setBuyPlatform("uu");
        lot = lotRepository.save(lot);

        snapshot("uu", "100", LocalDateTime.now().minusMinutes(15));
        marketIndexService.captureCurrentUser();
        snapshot("uu", "110", LocalDateTime.now().minusMinutes(10));
        marketIndexService.captureCurrentUser();

        lot.setQuantity(new BigDecimal("2"));
        lotRepository.save(lot);
        snapshot("uu", "120", LocalDateTime.now().minusMinutes(5));
        marketIndexService.captureCurrentUser();
        MarketIndexView afterFlow = marketIndexService.history("holdings", "24h");
        assertEquals(0, new BigDecimal("110.000000").compareTo(afterFlow.currentValue()));

        snapshot("uu", "130", LocalDateTime.now());
        marketIndexService.captureCurrentUser();
        MarketIndexView afterMove = marketIndexService.history("holdings", "24h");
        assertEquals(0, new BigDecimal("119.166667").compareTo(afterMove.currentValue()));
        assertEquals(4, afterMove.points().size());
    }

    @Test
    void itemHistoryReturnsUuOnly() {
        snapshot("steam", "90", LocalDateTime.now().minusMinutes(5));
        snapshot("uu", "100", LocalDateTime.now().minusMinutes(4));
        snapshot("uu", "105", LocalDateTime.now());

        var history = priceService.history(item.getId(), null, "24h");

        assertEquals("uu", history.platform());
        assertEquals(2, history.points().size());
        assertEquals(0, new BigDecimal("105").compareTo(history.points().getLast().value()));
    }

    private void snapshot(String platform, String price, LocalDateTime at) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.setItem(item);
        snapshot.setPlatform(platform);
        snapshot.setPrice(new BigDecimal(price));
        snapshot.setCurrency("CNY");
        snapshot.setFetchedAt(at);
        snapshotRepository.save(snapshot);
    }
}
