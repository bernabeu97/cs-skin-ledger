package com.cs.skinledger.web;

import com.cs.skinledger.dto.PnlGroupBy;
import com.cs.skinledger.dto.PnlRow;
import com.cs.skinledger.dto.PortfolioView;
import com.cs.skinledger.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final TradeService tradeService;

    @GetMapping("/pnl")
    public List<PnlRow> pnl(@RequestParam(value = "group_by", defaultValue = "month") PnlGroupBy groupBy) {
        return tradeService.realizedPnl(groupBy);
    }

    @GetMapping("/portfolio")
    public PortfolioView portfolio() {
        return tradeService.portfolio();
    }
}