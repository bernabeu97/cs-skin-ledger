package com.cs.skinledger.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceTargetTest {

    @Test
    void trimsCatalogNameBeforeAppendingExterior() {
        PriceTarget target = new PriceTarget(1L, "Souvenir MAC-10 | Echoing Sands ", "略有磨损");

        assertThat(target.fullMarketHashName())
                .isEqualTo("Souvenir MAC-10 | Echoing Sands (Minimal Wear)");
    }
}
