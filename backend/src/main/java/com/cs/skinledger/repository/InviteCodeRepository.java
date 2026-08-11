package com.cs.skinledger.repository;

import com.cs.skinledger.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InviteCode i where i.codeHash = :hash")
    Optional<InviteCode> findForUse(@Param("hash") String hash);
    List<InviteCode> findTop100ByOrderByCreatedAtDesc();
}
