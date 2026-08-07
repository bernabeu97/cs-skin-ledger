package com.cs.skinledger.service.price;

import com.cs.skinledger.dto.PriceQuote;
import com.cs.skinledger.dto.PriceTarget;

import java.util.List;

/**
 * 价格数据源适配器：steam / uu / buff。
 */
public interface PriceProvider {

    /** 唯一标识：steam / uu / buff */
    String name();

    /** 是否已配置可用（如 CSQAQ 需要 token，UU 直连需要 token 文件） */
    boolean available();

    /** 抓取一批目标的价格；失败目标不出现在返回列表，错误由调用方统计 */
    List<PriceQuote> fetch(List<PriceTarget> targets) throws Exception;
}