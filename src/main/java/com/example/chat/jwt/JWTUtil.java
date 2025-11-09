package com.example.chat.jwt;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 검증 전용 유틸리티
 * - 발행 기능 없음 (기존 백엔드에서만 발행)
 * - 검증만 수행
 */
@Component
public class JWTUtil {
    
    private final SecretKey secretKey;

    public JWTUtil(@Value("${spring.jwt.secret}") String secret) {
        this.secretKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    /**
     * 토큰에서 email 추출
     */
    public String getEmail(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("email", String.class);
    }

    /**
     * 토큰에서 role 추출
     */
    public String getRole(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("role", String.class);
    }

    /**
     * 토큰에서 category 추출
     */
    public String getCategory(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("category", String.class);
    }

    /**
     * 토큰에서 userId 추출 (핵심!)
     */
    public Long getUserId(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("userId", Long.class);
    }

    /**
     * 토큰 만료일 추출
     */
    public Date getExpirationDate(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getExpiration();
    }

    /**
     * 토큰 만료 여부 확인
     */
    public Boolean isExpired(String token) {
        return getExpirationDate(token).before(Date.from(Instant.now()));
    }
}
