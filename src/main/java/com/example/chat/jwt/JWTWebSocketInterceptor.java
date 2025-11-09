package com.example.chat.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

        try {
            // 1. 토큰 만료 확인
            if (jwtUtil.isExpired(accessToken)) {
                log.warn("WebSocket handshake failed: token expired");
                return handleUnauthorized(exchange, "Token expired");
            }

            // 2. category 확인
            String category = jwtUtil.getCategory(accessToken);
            if (!"accessToken".equals(category)) {
                log.warn("WebSocket handshake failed: invalid token category: {}", category);
                return handleUnauthorized(exchange, "Invalid token category");
            }

            // 3. userId 추출
            Long userId = jwtUtil.getUserId(accessToken);
            String email = jwtUtil.getEmail(accessToken);

            log.info("WebSocket handshake authorized: userId={}, email={}", userId, email);

            // 4. userId를 attributes에 저장 (나중에 사용 가능)
            exchange.getAttributes().put("userId", userId);
            exchange.getAttributes().put("email", email);

            return chain.filter(exchange);

        } catch (ExpiredJwtException e) {
            log.warn("WebSocket handshake failed: token expired", e);
            return handleUnauthorized(exchange, "Token expired");
        } catch (JwtException e) {
            log.error("WebSocket handshake failed: invalid token", e);
            return handleUnauthorized(exchange, "Invalid token");
        } catch (Exception e) {
            log.error("WebSocket handshake failed: unexpected error", e);
            return handleUnauthorized(exchange, "Authentication failed");
        }
    }

    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        
        String json = String.format("{\"error\": \"%s\"}", message);
        return response.writeWith(
            Mono.just(response.bufferFactory().wrap(json.getBytes()))
        );
    }
}
