package com.houssen.liberoshop.repository;

import com.houssen.liberoshop.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** The rows whose token has expired by itself: nothing left for them to refuse. */
    long deleteByExpiresAtBefore(Instant moment);
}
