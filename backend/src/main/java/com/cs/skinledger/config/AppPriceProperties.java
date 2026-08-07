package com.cs.skinledger.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 行情模块配置（application.yml 的 app.price.*），全部带默认值。
 */
@Data
@ConfigurationProperties(prefix = "app.price")
public class AppPriceProperties {

    private Csqaq csqaq = new Csqaq();
    private Steam steam = new Steam();
    private Youpin youpin = new Youpin();
    /** 启动时是否自动刷新一次持有批次的行情（默认关闭，避免被限流） */
    private boolean refreshOnStartup = false;
    /** 默认刷新平台：uu,steam,buff */
    private String defaultSources = "uu,steam,buff";

    @Data
    public static class Csqaq {
        /** 免费注册 https://csqaq.com 获取，或设置环境变量 CSQAQ_TOKEN */
        private String apiToken = "";
        private String baseUrl = "https://api.csqaq.com";
        private int timeoutSeconds = 15;
    }

    @Data
    public static class Steam {
        private boolean enabled = true;
        private long delayMs = 1100;
        private int timeoutSeconds = 12;
    }

    @Data
    public static class Youpin {
        private boolean enabled = false;
        private String tokenFile = "work/uu_token.txt";
        private int timeoutSeconds = 12;
    }
}