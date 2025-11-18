package com.example.chat.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 검증 전용 유틸리티 (개선 버전)
 * - 발행 기능 없음 (기존 백엔드에서만 발행)
 * - 검증 및 Claims 추출
 * - 예외 처리 통합
 */
@Component
@Slf4j
public class JWTUtil {
    
    private final SecretKey secretKey;

    public JWTUtil(@Value("${spring.jwt.secret}") String secret) {
        this.secretKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    /**
     * 토큰에서 Claims 추출 (검증 포함)
     * 
     * @param token JWT 토큰
     * @return Claims (검증 성공 시)
     * @throws JwtException 토큰이 유효하지 않은 경우
     */
    private Claims getClaims(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }
        
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw e;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 토큰 검증 (만료 + category 확인)
     * 
     * @param token JWT 토큰
     * @return 검증 결과
     */
    public TokenValidationResult validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            
            // 1. 만료 확인
            Date expiration = claims.getExpiration();
            if (expiration.before(Date.from(Instant.now()))) {
                return TokenValidationResult.expired();
            }
            
            // 2. category 확인
            String category = claims.get("category", String.class);
            if (!"accessToken".equals(category)) {
                return TokenValidationResult.invalidCategory(category);
            }
            
            // 3. userId 필수 확인
            Object userIdObj = claims.get("userId");
            if (userIdObj == null) {
                return TokenValidationResult.missingUserId();
            }
            
            return TokenValidationResult.valid(claims);
            
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.expired();
        } catch (JwtException e) {
            return TokenValidationResult.invalid(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during token validation", e);
            return TokenValidationResult.invalid("Unexpected error");
        }
    }

    /**
     * 토큰에서 email 추출
     */
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    /**
     * 토큰에서 role 추출
     */
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * 토큰에서 category 추출
     */
    public String getCategory(String token) {
        return getClaims(token).get("category", String.class);
    }

    /**
     * 토큰에서 userId 추출 (안전)
     * 
     * @return userId (없으면 Exception)
     */
    public Long getUserId(String token) {
        Claims claims = getClaims(token);
        Object userIdObj = claims.get("userId");
        
        if (userIdObj == null) {
            throw new JwtException("userId not found in token");
        }
        
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        } else if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else {
            throw new JwtException("Invalid userId type: " + userIdObj.getClass().getName());
        }
    }

    /**
     * 토큰 만료 여부 확인
     */
    public Boolean isExpired(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            return expiration.before(Date.from(Instant.now()));
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * 토큰 검증 결과 객체
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Claims claims;
        
        private TokenValidationResult(boolean valid, String errorMessage, Claims claims) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.claims = claims;
        }
        
        public static TokenValidationResult valid(Claims claims) {
            return new TokenValidationResult(true, null, claims);
        }
        
        public static TokenValidationResult expired() {
            return new TokenValidationResult(false, "Token expired", null);
        }
        
        public static TokenValidationResult invalidCategory(String category) {
            return new TokenValidationResult(false, "Invalid token category: " + category, null);
        }
        
        public static TokenValidationResult missingUserId() {
            return new TokenValidationResult(false, "userId not found in token", null);
        }
        
        public static TokenValidationResult invalid(String message) {
            return new TokenValidationResult(false, message, null);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public Optional<Claims> getClaims() {
            return Optional.ofNullable(claims);
        }
    }
}
