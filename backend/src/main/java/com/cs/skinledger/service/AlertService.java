package com.cs.skinledger.service;

import com.cs.skinledger.domain.Alert;
import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.AlertCreateRequest;
import com.cs.skinledger.dto.AlertResponse;
import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.UserRepository;
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
 * 价格提醒：增删改查 + 行情刷新后检查触发（gt=高于阈值，lt=低于阈值，触发一次后需手动重置）。
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlertResponse> list() {
        return alertRepository.findByUserId(localUserId()).stream().map(AlertResponse::from).toList();
    }

    @Transactional
    public AlertResponse create(AlertCreateRequest req) {
        Item item = itemRepository.findById(req.itemId())
                .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + req.itemId()));
        Alert alert = new Alert();
        alert.setUser(localUser());
        alert.setItem(item);
        alert.setPlatform(req.platform());
        alert.setCondition(req.condition());
        alert.setThreshold(req.threshold());
        alert.setEnabled(true);
        return AlertResponse.from(alertRepository.save(alert));
    }

    @Transactional
    public void delete(Long id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("提醒不存在: " + id));
        alertRepository.delete(alert);
    }

    /** 重置触发状态，允许再次提醒 */
    @Transactional
    public AlertResponse reset(Long id) {
        Alert alert = alertRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("提醒不存在: " + id));
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
            latest.putIfAbsent(q.itemId() + "|" + q.platform(), q);
        }
        List<AlertResponse> triggered = new ArrayList<>();
        List<Alert> alerts = alertRepository.findByUserId(localUserId());
        LocalDateTime now = LocalDateTime.now();
        for (Alert alert : alerts) {
            if (!Boolean.TRUE.equals(alert.getEnabled()) || alert.getTriggeredAt() != null) {
                continue;
            }
            PriceQuote q = latest.get(alert.getItem().getId() + "|" + alert.getPlatform());
            if (q == null || q.price() == null) {
                continue;
            }
            int cmp = q.price().compareTo(alert.getThreshold());
            boolean hit = "gt".equals(alert.getCondition()) ? cmp > 0 : cmp < 0;
            if (hit) {
                alert.setTriggeredAt(now);
                triggered.add(AlertResponse.from(alertRepository.save(alert)));
            }
        }
        return triggered;
    }

    private Long localUserId() {
        return localUser().getId();
    }

    private User localUser() {
        return userRepository.findByUsername("local")
                .orElseThrow(() -> new IllegalStateException("本地用户不存在，请先初始化数据库"));
    }
}