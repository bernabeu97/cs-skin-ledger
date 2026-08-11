package com.cs.skinledger.service;

import com.cs.skinledger.domain.Alert;
import com.cs.skinledger.domain.Item;
import com.cs.skinledger.dto.AlertCreateRequest;
import com.cs.skinledger.dto.AlertResponse;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UU 价格提醒：增删改查 + 行情刷新后检查触发（gt=高于阈值，lt=低于阈值）。
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final ItemRepository itemRepository;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public List<AlertResponse> list() {
        return alertRepository.findByUserId(currentUser.id()).stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse create(AlertCreateRequest req) {
        Item item = itemRepository.findById(req.itemId())
                .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + req.itemId()));
        Alert alert = new Alert();
        alert.setUser(currentUser.get());
        alert.setItem(item);
        alert.setExterior(normalize(req.exterior()));
        alert.setPlatform(req.platform().trim().toLowerCase());
        alert.setCondition(req.condition());
        alert.setThreshold(req.threshold());
        alert.setEnabled(true);
        return AlertResponse.from(alertRepository.save(alert));
    }

    @Transactional
    public void delete(Long id) {
        Alert alert = ownedAlert(id);
        alertRepository.delete(alert);
    }

    /** 重置触发状态，允许再次提醒 */
    @Transactional
    public AlertResponse reset(Long id) {
        Alert alert = ownedAlert(id);
        alert.setTriggeredAt(null);
        return AlertResponse.from(alertRepository.save(alert));
    }

    /** 根据最新行情检查提醒；返回本次触发的提醒列表 */
    @Transactional
    public List<AlertResponse> check(List<PriceQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            return List.of();
        }
        Map<String, PriceQuote> latest = new HashMap<>();
        for (PriceQuote q : quotes) {
            latest.putIfAbsent(key(q.itemId(), q.exterior(), q.platform()), q);
        }
        List<AlertResponse> triggered = new ArrayList<>();
        List<Alert> alerts = alertRepository.findByUserId(currentUser.id());
        LocalDateTime now = LocalDateTime.now();
        for (Alert alert : alerts) {
            if (!Boolean.TRUE.equals(alert.getEnabled())) {
                continue;
            }
            PriceQuote q = latest.get(key(alert.getItem().getId(), alert.getExterior(), alert.getPlatform()));
            if (q == null || q.price() == null) {
                continue;
            }
            int cmp = q.price().compareTo(alert.getThreshold());
            boolean hit = "gt".equals(alert.getCondition()) ? cmp > 0 : cmp < 0;
            if (alert.getTriggeredAt() != null) {
                if (!hit) {
                    // 回到阈值另一侧后自动重新布防，避免用户手动重置。
                    alert.setTriggeredAt(null);
                    alertRepository.save(alert);
                }
                continue;
            }
            if (hit) {
                alert.setTriggeredAt(now);
                triggered.add(AlertResponse.from(alertRepository.save(alert)));
            }
        }
        return triggered;
    }

    private Alert ownedAlert(Long id) {
        return alertRepository.findByIdAndUserId(id, currentUser.id())
                .orElseThrow(() -> new IllegalArgumentException("提醒不存在: " + id));
    }

    private String key(Long itemId, String exterior, String platform) {
        return itemId + "|" + (normalize(exterior) == null ? "" : normalize(exterior))
                + "|" + platform.trim().toLowerCase();
    }

    private String normalize(String exterior) {
        if (exterior == null || exterior.isBlank()) {
            return null;
        }
        return exterior.trim();
    }
}
