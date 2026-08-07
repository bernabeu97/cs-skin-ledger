package com.cs.skinledger.service.price;

import com.cs.skinledger.config.AppPriceProperties;
import com.cs.skinledger.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时刷新持有批次行情（配置 app.price.refresh-interval-minutes > 0 时启用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.price.refresh-interval-minutes:0} > 0")
public class PriceRefreshScheduler {

    private final PriceService priceService;

    @Scheduled(fixedDelayString = "${app.price.refresh-interval-minutes:360}000",
            initialDelayString = "${app.price.refresh-initial-delay-seconds:300}000")
    public void refresh() {
        if (!priceService.hasAvailableSource()) {
            log.warn("定时刷新跳过：未配置任何可用行情数据源（如 CSQAQ ApiToken）");
            return;
        }
        var result = priceService.refresh(null);
        log.info("定时刷新行情完成：requested={}, ok={}, byPlatform={}",
                result.requested(), result.ok(), result.byPlatform());
    }
}