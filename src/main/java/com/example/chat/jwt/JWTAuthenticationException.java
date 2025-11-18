package com.example.chat.jwt;

import org.springframework.security.core.AuthenticationException;

/**
 * JWT 인증 실패 시 발생하는 예외
 */
public class JWTAuthenticationException extends AuthenticationException {
    
    public JWTAuthenticationException(String message) {
        super(message);
    }
    
    public JWTAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 토큰 만료
     */
    public static JWTAuthenticationException expired() {
        return new JWTAuthenticationException("JWT token has expired");
    }
    
    /**
     * 토큰 유효하지 않음
     */
    public static JWTAuthenticationException invalid() {
        return new JWTAuthenticationException("JWT token is invalid");
    }
    
    /**
     * 토큰 카테고리 불일치
     */
    public static JWTAuthenticationException invalidCategory(String category) {
        return new JWTAuthenticationException("Invalid token category: " + category);
    }
    
    /**
     * userId 누락
     */
    public static JWTAuthenticationException missingUserId() {
        return new JWTAuthenticationException("userId not found in token");
    }
}
