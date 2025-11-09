package com.example.chat.config;

import com.example.chat.jwt.JWTWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security 설정 (Reactive)
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTWebSocketInterceptor jwtWebSocketInterceptor;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // CSRF 비활성화 (WebSocket, REST API)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            
            // HTTP Basic 비활성화
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            
            // Form Login 비활성화
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            
            // 경로별 인가
            .authorizeExchange(exchanges -> exchanges
                // 내부 API (기존 백엔드에서만 호출) - 인증 없음
                .pathMatchers("/api/chat/rooms").permitAll()
                .pathMatchers("/api/chat/rooms/{chatRoomId}/messages").permitAll()
                
                // Health check
                .pathMatchers("/actuator/health").permitAll()
                
                // WebSocket - JWT 검증 (인터셉터에서 처리)
                .pathMatchers("/ws").permitAll()
                
                // 나머지는 인증 필요
                .anyExchange().authenticated()
            )
            
            // JWT 인터셉터 추가
            .addFilterAt(jwtWebSocketInterceptor, SecurityWebFiltersOrder.AUTHENTICATION)
            
            .build();
    }
}
