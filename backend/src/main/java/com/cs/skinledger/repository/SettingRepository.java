package com.cs.skinledger.repository;

import com.cs.skinledger.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettingRepository extends JpaRepository<Setting, Long> {
    Optional<Setting> findByUserIdAndKey(Long userId, String key);
}