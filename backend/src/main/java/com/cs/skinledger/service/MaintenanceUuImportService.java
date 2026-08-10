package com.cs.skinledger.service;

import com.cs.skinledger.domain.User;
import com.cs.skinledger.dto.UuFullJsonImportResult;
import com.cs.skinledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;

/** 服务器本地维护能力：在同一事务内替换指定用户的个人账本数据。 */
@Service
@RequiredArgsConstructor
public class MaintenanceUuImportService {

    private final UserRepository userRepository;
    private final UuFullJsonImportService uuFullJsonImportService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public UuFullJsonImportResult replaceUserData(String username, Path file) throws Exception {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + username));
        Long userId = user.getId();

        // 账号、密码、个人 Token/费率设置和全局饰品/行情数据均保留。
        jdbcTemplate.update("DELETE FROM alerts WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM other_cost_entries WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sync_logs WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM trades WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM lots WHERE user_id = ?", userId);

        UuFullJsonImportResult result = uuFullJsonImportService.importFileForUser(file, user);
        if (!result.errors().isEmpty()
                || result.holdingsImported() + result.salesImported() != result.remainingHoldings() + result.sellRecords()) {
            throw new IllegalStateException("导入不完整，事务已回滚: " + result.errors());
        }
        return result;
    }
}
