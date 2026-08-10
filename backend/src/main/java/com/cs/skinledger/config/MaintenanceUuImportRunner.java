package com.cs.skinledger.config;

import com.cs.skinledger.dto.UuFullJsonImportResult;
import com.cs.skinledger.dto.PriceRefreshResult;
import com.cs.skinledger.service.MaintenanceUuImportService;
import com.cs.skinledger.service.PriceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 仅在服务器命令行显式传入维护参数时运行；没有 HTTP 管理入口。
 * 必须同时传 replace=true，避免误触发清理。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.maintenance.uu-import-file")
public class MaintenanceUuImportRunner implements ApplicationRunner {

    private final Environment environment;
    private final MaintenanceUuImportService maintenanceUuImportService;
    private final PriceService priceService;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String username = environment.getRequiredProperty("app.maintenance.username");
        String file = environment.getRequiredProperty("app.maintenance.uu-import-file");
        boolean replace = environment.getProperty("app.maintenance.replace", Boolean.class, false);
        if (!replace) {
            throw new IllegalArgumentException("维护导入必须显式设置 app.maintenance.replace=true");
        }

        UuFullJsonImportResult result = maintenanceUuImportService.replaceUserData(username, Path.of(file));
        System.out.println("MAINTENANCE_UU_IMPORT_RESULT=" + objectMapper.writeValueAsString(result));
        if (environment.getProperty("app.maintenance.refresh-prices", Boolean.class, false)) {
            SecurityContext security = SecurityContextHolder.createEmptyContext();
            security.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    username, "maintenance", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(security);
            try {
                PriceRefreshResult prices = priceService.refresh(List.of("uu"));
                System.out.println("MAINTENANCE_PRICE_REFRESH_RESULT=" + objectMapper.writeValueAsString(prices));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
        context.close();
    }
}
