package com.cs.skinledger.service;

import com.cs.skinledger.domain.TradeDirection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 盈亏引擎：移动平均成本法。
 * 买入：成本 += 数量*单价 + 手续费；卖出：已实现盈亏 += 净卖出额 - 数量*平均成本。
 */
public final class PnlEngine {

    public static final int SCALE = 4;
    private static final int AVG_SCALE = 8;

    private PnlEngine() {
    }

    public record Position(BigDecimal remainingQty, BigDecimal remainingCost,
                           BigDecimal realizedPnl, BigDecimal avgCost) {

        public static Position empty() {
            return new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record TradeInput(TradeDirection direction, BigDecimal quantity,
                             BigDecimal unitPrice, BigDecimal fee) {
    }

    public static Position apply(Position pos, TradeInput trade) {
        BigDecimal qty = trade.quantity();
        BigDecimal price = trade.unitPrice();
        BigDecimal fee = trade.fee() == null ? BigDecimal.ZERO : trade.fee();
        if (qty.signum() <= 0 || price.signum() < 0 || fee.signum() < 0) {
            throw new IllegalArgumentException("数量必须大于 0，价格和手续费不能为负");
        }
        if (trade.direction() == TradeDirection.BUY) {
            BigDecimal cost = qty.multiply(price).add(fee);
            BigDecimal newQty = pos.remainingQty().add(qty);
            BigDecimal newCost = pos.remainingCost().add(cost);
            return new Position(newQty, newCost, pos.realizedPnl(), avgCost(newQty, newCost));
        }
        if (qty.compareTo(pos.remainingQty()) > 0) {
            throw new IllegalArgumentException("卖出数量超过当前持仓");
        }
        BigDecimal avg = avgCost(pos.remainingQty(), pos.remainingCost());
        BigDecimal sellNet = qty.multiply(price).subtract(fee);
        BigDecimal realized = pos.realizedPnl().add(sellNet.subtract(qty.multiply(avg)));
        BigDecimal newQty = pos.remainingQty().subtract(qty);
        BigDecimal newCost = pos.remainingCost().subtract(qty.multiply(avg));
        if (newQty.signum() == 0) {
            newCost = BigDecimal.ZERO;
        }
        return new Position(newQty, newCost, realized, avgCost(newQty, newCost));
    }

    public static Position replay(List<TradeInput> trades) {
        Position pos = Position.empty();
        for (TradeInput trade : trades) {
            pos = apply(pos, trade);
        }
        return pos;
    }

    private static BigDecimal avgCost(BigDecimal qty, BigDecimal cost) {
        if (qty.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return cost.divide(qty, AVG_SCALE, RoundingMode.HALF_UP);
    }
}