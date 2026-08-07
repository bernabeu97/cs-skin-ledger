package com.cs.skinledger.service.price;

import com.cs.skinledger.util.WearNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SteamPriceProviderTest {

    @Test
    void parsesSteamPriceText() {
        assertEquals(new BigDecimal("1234.56"), SteamPriceProvider.parseLowestPrice("¥ 1,234.56"));
        assertEquals(new BigDecimal("10.00"), SteamPriceProvider.parseLowestPrice("¥10.00"));
        assertEquals(new BigDecimal("0.11"), SteamPriceProvider.parseLowestPrice("￥ 0.11"));
        assertNull(SteamPriceProvider.parseLowestPrice(""));
        assertNull(SteamPriceProvider.parseLowestPrice(null));
    }

    @Test
    void wearNamesTranslateChineseToEnglish() {
        assertEquals("Factory New", WearNames.toEnglish("崭新出厂"));
        assertEquals("Field-Tested", WearNames.toEnglish("久经沙场"));
        assertEquals("Battle-Scarred", WearNames.toEnglish("战痕累累"));
        assertEquals("Minimal Wear", WearNames.toEnglish("略有磨损"));
        assertEquals("Well-Worn", WearNames.toEnglish("破损不堪"));
        assertEquals("Factory New", WearNames.toEnglish("Factory New"));
        assertEquals("AK-47 | Redline (Field-Tested)", "AK-47 | Redline (" + WearNames.toEnglish("久经沙场") + ")");
    }
}