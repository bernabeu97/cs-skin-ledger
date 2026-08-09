package com.cs.skinledger.service;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.LotStatus;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.UuImportRequest;
import com.cs.skinledger.dto.UuImportResult;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UU 库存/交易导入：
 * - holdings：库存中的饰品 -> HOLDING 批次（购入价缺失记为 0，备注提示补填）
 * - sales：已成交卖出 -> SOLD 批次（买入价未知记为 0，需用户补填买入价后盈亏才准确）
 * 通过 lots.source_ref 唯一约束幂等，重复导入自动跳过。
 */
@Service
@RequiredArgsConstructor
public class UuImportService {

    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ISO_DATE_TIME
    };

    private final LotRepository lotRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    @Transactional
    public UuImportResult importData(UuImportRequest req) {
        List<String> errors = new ArrayList<>();
        User user = localUser();
        int hReq = req.holdings() == null ? 0 : req.holdings().size();
        int hImported = 0;
        int hSkip = 0;
        for (UuImportRequest.HoldingImport h : req.holdings() == null ? List.<UuImportRequest.HoldingImport>of() : req.holdings()) {
            String sourceRef = "uu:inv:" + safe(h.itemName()) + ":" + safe(h.wear()) + ":" + (h.floatValue() == null ? "" : h.floatValue().toPlainString()) + ":" + h.quantity();
            if (lotRepository.existsBySourceRef(sourceRef)) {
                hSkip++;
                continue;
            }
            try {
                Item item = resolveItem(h.itemId(), h.itemName(), h.itemNameZh());
                Lot lot = new Lot();
                lot.setUser(user);
                lot.setItem(item);
                lot.setQuantity(h.quantity() == null ? BigDecimal.ONE : h.quantity());
                lot.setExterior(h.wear());
                lot.setFloatValue(h.floatValue());
                lot.setBuyPrice(h.buyPrice() == null ? BigDecimal.ZERO : h.buyPrice());
                lot.setBuyTime(parseTime(h.buyTime(), LocalDateTime.now()));
                lot.setBuyPlatform("uu");
                lot.setStatus(LotStatus.HOLDING);
                lot.setSourceRef(sourceRef);
                StringBuilder note = new StringBuilder("UU 库存导入");
                if (h.buyPrice() == null) {
                    note.append("；购入价未知，已记为 0，请编辑补填");
                }
                if (h.buyTime() == null) {
                    note.append("；买入时间未知，记为导入时间");
                }
                if (h.note() != null && !h.note().isBlank()) {
                    note.append("；").append(h.note());
                }
                lot.setNote(note.toString());
                lotRepository.save(lot);
                hImported++;
            } catch (Exception e) {
                errors.add("库存[" + h.itemName() + "]: " + e.getMessage());
            }
        }

        int sReq = req.sales() == null ? 0 : req.sales().size();
        int sImported = 0;
        int sSkip = 0;
        for (UuImportRequest.SaleImport s : req.sales() == null ? List.<UuImportRequest.SaleImport>of() : req.sales()) {
            String sourceRef = "uu:sale:" + safe(s.itemName()) + ":" + safe(s.sellTime());
            if (lotRepository.existsBySourceRef(sourceRef)) {
                sSkip++;
                continue;
            }
            try {
                Item item = resolveItem(s.itemId(), s.itemName(), s.itemNameZh());
                Lot lot = new Lot();
                lot.setUser(user);
                lot.setItem(item);
                lot.setQuantity(BigDecimal.ONE);
                lot.setExterior(s.wear());
                lot.setBuyPrice(BigDecimal.ZERO);
                lot.setBuyTime(parseTime(s.sellTime(), LocalDateTime.now()));
                lot.setBuyPlatform("uu");
                lot.setSellPrice(s.sellPrice());
                lot.setSellTime(parseTime(s.sellTime(), null));
                lot.setSellPlatform("uu");
                lot.setFee(s.fee() == null ? BigDecimal.ZERO : s.fee());
                BigDecimal income = lot.getQuantity().multiply(lot.getSellPrice()).subtract(lot.getFee());
                lot.setActualIncome(income);
                lot.setProfit(income.subtract(lot.getQuantity().multiply(lot.getBuyPrice())));
                lot.setStatus(LotStatus.SOLD);
                lot.setSourceRef(sourceRef);
                StringBuilder note = new StringBuilder("UU 交易导入（卖出）；买入价未知，记为 0，请编辑补填后盈亏才准确");
                if (s.note() != null && !s.note().isBlank()) {
                    note.append("；").append(s.note());
                }
                lot.setNote(note.toString());
                lotRepository.save(lot);
                sImported++;
            } catch (Exception e) {
                errors.add("卖出[" + s.itemName() + "]: " + e.getMessage());
            }
        }
        return new UuImportResult(hReq, hImported, hSkip, sReq, sImported, sSkip, errors);
    }

    private Item resolveItem(Long itemId, String itemName, String itemNameZh) {
        if (itemId != null) {
            return itemRepository.findById(itemId)
                    .orElseThrow(() -> new IllegalArgumentException("饰品不存在: " + itemId));
        }
        if (itemNameZh != null && !itemNameZh.isBlank()) {
            return itemRepository.findByNameZh(itemNameZh)
                    .orElseGet(() -> findOrCreateManual(itemName, itemNameZh));
        }
        if (itemName != null && !itemName.isBlank()) {
            return itemRepository.findByMarketHashName(itemName)
                    .orElseGet(() -> findOrCreateManual(itemName, itemNameZh));
        }
        throw new IllegalArgumentException("缺少饰品名称");
    }

    private Item findOrCreateManual(String marketHashName, String nameZh) {
        return itemRepository.findByMarketHashName(marketHashName)
                .orElseGet(() -> {
                    Item item = new Item();
                    item.setMarketHashName(marketHashName);
                    item.setNameZh(nameZh);
                    item.setSource("uu");
                    return itemRepository.save(item);
                });
    }

    private LocalDateTime parseTime(String s, LocalDateTime fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        String cleaned = s.trim().replace(' ', 'T');
        for (DateTimeFormatter f : TIME_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, f);
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return fallback;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private User localUser() {
        return userRepository.findByUsername("local")
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername("local");
                    return userRepository.save(user);
                });
    }
}