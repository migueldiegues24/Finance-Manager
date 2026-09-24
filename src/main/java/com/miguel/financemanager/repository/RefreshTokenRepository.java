package com.miguel.financemanager.repository;

import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByIdAndUser(Long id, User user);

    // Marca como revogados (não apaga: ver RefreshToken.revoked). Grava e
    // limpa o contexto de persistência, para ninguém ler o estado antigo.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user = :user AND t.revoked = false")
    int revokeAllByUser(@Param("user") User user);
}
