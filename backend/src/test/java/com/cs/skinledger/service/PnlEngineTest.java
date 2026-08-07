package com.cs.skinledger.service;

import com.cs.skinledger.domain.TradeDirection;
import com.cs.skinledger.service.PnlEngine.Position;
import com.cs.skinledger.service.PnlEngine.TradeInput;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PnlEngineTest {

    private static TradeInput buy(String qty, String price, String fee) {
        return new TradeInput(TradeDirection.BUY, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee));
    }

    private static TradeInput sell(String qty, String price, String fee) {
        return new TradeInput(TradeDirection.SELL, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(fee));
    }

    @Test
    void buyThenFullSellWithFee() {
        Position pos = PnlEngine.replay(List.of(
                buy("2", "100", "10"),
                sell("1", "120", "3")
        ));
        assertEquals(0, new BigDecimal("1").compareTo(pos.remainingQty()));
        assertEquals(0, new BigDecimal("105").compareTo(pos.avgCost()));
        assertEquals(0, new BigDecimal("12").compareTo(pos.realizedPnl()));
    }

    @Test
    void fullLiquidationRealizesTotalProfit() {
        Position pos = PnlEngine.replay(List.of(
                buy("1", "100", "0"),
                buy("1", "100", "0"),
                sell("1", "150", "0"),
                sell("1", "150", "0")
        ));
        assertEquals(0, BigDecimal.ZERO.compareTo(pos.remainingQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(pos.remainingCost()));
        assertEquals(0, new BigDecimal("100").compareTo(pos.realizedPnl()));
    }

    @Test
    void movingAverageAcrossTwoBuys() {
        Position pos = PnlEngine.replay(List.of(
                buy("1", "100", "0"),
                buy("1", "200", "0")
        ));
        assertEquals(0, new BigDecimal("150").compareTo(pos.avgCost()));
        Position afterSell = PnlEngine.apply(pos, sell("1", "180", "0"));
        assertEquals(0, new BigDecimal("30").compareTo(afterSell.realizedPnl()));
        assertEquals(0, new BigDecimal("150").compareTo(afterSell.remainingCost()));
    }

    @Test
    void buyWithFeeAddsToCostBasis() {
        Position pos = PnlEngine.replay(List.of(buy("1", "100", "5")));
        assertEquals(0, new BigDecimal("105").compareTo(pos.remainingCost()));
        assertEquals(0, new BigDecimal("105").compareTo(pos.avgCost()));
    }

    @Test
    void sellMoreThanHeldThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PnlEngine.replay(List.of(buy("1", "100", "0"), sell("2", "100", "0"))));
    }

    @Test
    void sellFromEmptyPositionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PnlEngine.apply(Position.empty(), sell("1", "100", "0")));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                PnlEngine.apply(Position.empty(), buy("-1", "100", "0")));
    }
}