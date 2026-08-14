package com.cs.skinledger.web;

import com.cs.skinledger.dto.MarketplaceIdImportResult;
import com.cs.skinledger.dto.PortfolioValuation;
import com.cs.skinledger.dto.PriceConfigView;
import com.cs.skinledger.dto.PriceRefreshResult;
import com.cs.skinledger.dto.PriceHistoryView;
import com.cs.skinledger.dto.MarketIndexView;
import com.cs.skinledger.dto.CsqaqIndexKlineView;
import com.cs.skinledger.dto.CsqaqIndexView;
import com.cs.skinledger.dto.LotHealth;
import com.cs.skinledger.service.CsqaqIndexService;
import com.cs.skinledger.service.MarketplaceIdImportService;
import com.cs.skinledger.service.MarketIndexService;
import com.cs.skinledger.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * 行情模块接口：配置状态 / 刷新 / 估值 / ID 映射导入。
 */
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;
    private final MarketplaceIdImportService marketplaceIdImportService;
    private final MarketIndexService marketIndexService;
    private final CsqaqIndexService csqaqIndexService;

    /** 行情配置状态（前端用于提示是否已配置 CSQAQ 等） */
    @GetMapping("/config")
    public PriceConfigView config() {
        return priceService.config();
    }

    /** 账本数据健康指标(无行情持仓、估值覆盖率、待补填买入价)。 */
    @GetMapping("/health")
    public LotHealth health() {
        return priceService.health();
    }

    /** 刷新持有批次的市场价，platforms 逗号分隔：uu,steam,buff */
    @PostMapping("/refresh")
    public PriceRefreshResult refresh(@RequestParam(required = false) String platforms) {
        List<String> list = platforms == null || platforms.isBlank()
                ? List.of()
                : java.util.Arrays.stream(platforms.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return priceService.refresh(list);
    }

    /** 持仓估值：成本 / 当前市值 / 浮动盈亏 */
    @GetMapping("/valuation")
    public PortfolioValuation valuation() {
        return priceService.valuation();
    }

    @GetMapping("/history")
    public PriceHistoryView history(@RequestParam Long itemId,
                                    @RequestParam(required = false) String exterior,
                                    @RequestParam(defaultValue = "24h") String period) {
        return priceService.history(itemId, exterior, period);
    }

    @GetMapping("/index")
    public MarketIndexView index(@RequestParam(defaultValue = "holdings") String kind,
                                 @RequestParam(defaultValue = "24h") String period) {
        return marketIndexService.history(kind, period);
    }

    /** CSQAQ 全市场与分类指数概览。 */
    @GetMapping("/csqaq/indices")
    public List<CsqaqIndexView> csqaqIndices() {
        return csqaqIndexService.indices();
    }

    /** CSQAQ 指数 K 线；period: 1hour/4hour/1day/7day。 */
    @GetMapping("/csqaq/index-kline")
    public CsqaqIndexKlineView csqaqIndexKline(@RequestParam long id,
                                               @RequestParam(defaultValue = "1day") String period) {
        return csqaqIndexService.kline(id, period);
    }

    /** 导入平台商品 ID 映射（work/cs2_marketplaceids.json） */
    @PostMapping("/import-market-ids")
    public MarketplaceIdImportResult importMarketIds(@RequestParam(defaultValue = "work") String dir) throws Exception {
        return marketplaceIdImportService.importFromFile(Path.of(dir, "cs2_marketplaceids.json"));
    }
}
