package com.example.chat.config;

import com.example.chat.jwt.JWTAuthenticationConverter;
import com.example.chat.jwt.JWTAuthenticationManager;
import com.example.chat.jwt.JWTWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import reactor.core.publisher.Mono;

/**
 * Spring Security 설정 (Reactive)
 * 
 * 인증 구조:
 * 1. REST API (/api/chat/my-rooms, /api/chat/rooms/{id}/messages)
 *    → JWT 인증 필요 (JWTAuthenticationManager)
 * 
 * 2. WebSocket (/ws)
 *    → JWT 인증 필요 (JWTWebSocketInterceptor)
 * 
 * 3. 내부 API (/api/chat/rooms)
 *    → 인증 불필요 (기존 백엔드 내부 호출)
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTWebSocketInterceptor jwtWebSocketInterceptor;
    private final JWTAuthenticationManager jwtAuthenticationManager;
    private final JWTAuthenticationConverter jwtAuthenticationConverter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // JWT 인증 필터 생성
        AuthenticationWebFilter jwtAuthenticationFilter = new AuthenticationWebFilter(jwtAuthenticationManager);
        jwtAuthenticationFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);
        
        // 인증 실패 핸들러
        jwtAuthenticationFilter.setAuthenticationFailureHandler(authenticationFailureHandler());
        
        return http
            // CSRF 비활성화 (REST API + WebSocket)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            
            // HTTP Basic 비활성화
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            
            // Form Login 비활성화
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            
            // 경로별 인가 설정
            .authorizeExchange(exchanges -> exchanges
                // === 인증 불필요 (Public) ===
                
                // 내부 API (기존 백엔드 → 채팅 서비스)
                .pathMatchers("/api/chat/rooms").permitAll()
                
                // Health check
                .pathMatchers("/actuator/health").permitAll()
                
                // 테스트 페이지
                .pathMatchers("/test-websocket.html").permitAll()
                .pathMatchers("/static/**").permitAll()
                
                // WebSocket (인터셉터에서 별도 검증)
                .pathMatchers("/ws").permitAll()
                
                // === 인증 필요 (Authenticated) ===
                
                // 채팅방 목록 조회
                .pathMatchers("/api/chat/my-rooms").authenticated()
                
                // 메시지 조회
                .pathMatchers("/api/chat/rooms/*/messages").authenticated()
                
                // 그 외 모든 요청
                .anyExchange().authenticated()
            )
            
            // 예외 처리
            .exceptionHandling(spec -> spec
                .authenticationEntryPoint((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })
                .accessDeniedHandler((exchange, ex) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
            )
            
            // JWT 인증 필터 추가 (WebSocket 인터셉터보다 먼저)
            .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            
            // WebSocket 인터셉터 추가
            .addFilterAt(jwtWebSocketInterceptor, SecurityWebFiltersOrder.AUTHORIZATION)
            
            .build();
    }
    
    /**
     * JWT 인증 실패 핸들러
     */
    private ServerAuthenticationFailureHandler authenticationFailureHandler() {
        return (webFilterExchange, exception) -> {
            webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            
            String json = String.format(
                "{\"error\": \"Authentication failed\", \"message\": \"%s\", \"timestamp\": \"%s\"}",
                exception.getMessage(),
                java.time.Instant.now().toString()
            );
            
            return webFilterExchange.getExchange().getResponse()
                    .writeWith(Mono.just(
                            webFilterExchange.getExchange().getResponse()
                                    .bufferFactory()
                                    .wrap(json.getBytes())
                    ));
        };
    }
}
