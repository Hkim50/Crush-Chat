package com.example.chat.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebSocket 핸드셰이크 시 JWT 검증 인터셉터
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JWTWebSocketInterceptor implements WebFilter {

    private final JWTUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // WebSocket 경로만 검증
        if (!path.equals("/ws")) {
            return chain.filter(exchange);
        }

        log.debug("WebSocket handshake intercepted: {}", path);

        // 쿼리 파라미터에서 accessToken 추출
        String accessToken = request.getQueryParams().getFirst("accessToken");

        if (accessToken == null || accessToken.isEmpty()) {
            log.warn("WebSocket handshake failed: missing accessToken");
            return handleUnauthorized(exchange, "Missing access token");
        }

        // 통합 검증 메서드 사용
        JWTUtil.TokenValidationResult result = jwtUtil.validateToken(accessToken);
        
        if (!result.isValid()) {
            log.warn("WebSocket handshake failed: {}", result.getErrorMessage());
            return handleUnauthorized(exchange, result.getErrorMessage());
        }

        try {
            // userId, email 추출 및 저장
            Long userId = jwtUtil.getUserId(accessToken);
            String email = jwtUtil.getEmail(accessToken);
            String role = jwtUtil.getRole(accessToken);

            log.info("WebSocket handshake authorized: userId={}, email={}, role={}", userId, email, role);

            // attributes에 저장 (WebSocketHandler에서 사용 가능)
            exchange.getAttributes().put("userId", userId);
            exchange.getAttributes().put("email", email);
            exchange.getAttributes().put("role", role);

            return chain.filter(exchange);

        } catch (Exception e) {
            log.error("WebSocket handshake failed: unexpected error", e);
            return handleUnauthorized(exchange, "Authentication failed");
        }
    }

    /**
     * 인증 실패 응답
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        String json = String.format("{\"error\": \"%s\", \"timestamp\": \"%s\"}", 
                message, 
                java.time.Instant.now().toString());
        
        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(json.getBytes()))
        );
    }
}
