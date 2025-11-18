package com.example.chat.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 기반 인증 매니저 (Reactive)
 * REST API 요청에 대한 JWT 검증
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JWTAuthenticationManager implements ReactiveAuthenticationManager {
    
    private final JWTUtil jwtUtil;
    
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();
        
        return Mono.fromCallable(() -> {
            // 통합 검증 메서드 사용
            JWTUtil.TokenValidationResult result = jwtUtil.validateToken(token);
            
            if (!result.isValid()) {
                log.warn("JWT authentication failed: {}", result.getErrorMessage());
                return null;
            }
            
            // Claims에서 정보 추출
            Long userId = jwtUtil.getUserId(token);
            String email = jwtUtil.getEmail(token);
            String role = jwtUtil.getRole(token);
            
            log.debug("JWT authentication success: userId={}, email={}, role={}", userId, email, role);
            
            // Authentication 객체 생성
            // principal = userId (String으로 변환)
            // authorities = ROLE_{role}
            return new UsernamePasswordAuthenticationToken(
                    userId.toString(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
        })
        .onErrorResume(e -> {
            log.error("JWT authentication error: {}", e.getMessage(), e);
            return Mono.empty();
        })
        .flatMap(auth -> auth != null ? Mono.just(auth) : Mono.empty());
    }
}
