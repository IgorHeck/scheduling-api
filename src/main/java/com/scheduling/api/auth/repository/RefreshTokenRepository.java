package com.scheduling.api.auth.repository;

import com.scheduling.api.auth.model.RefreshToken;
import com.scheduling.api.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void  deleteByUser(User user);
}
