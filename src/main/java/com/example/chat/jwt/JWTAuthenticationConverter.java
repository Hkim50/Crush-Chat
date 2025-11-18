package com.example.chat.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * HTTP 요청에서 JWT 토큰 추출
 * 
 * 지원하는 형식:
 * 1. accessToken 헤더 (우선순위 1)
 * 2. Authorization: Bearer {token} 헤더 (우선순위 2)
 */
@Component
@Slf4j
public class JWTAuthenticationConverter implements ServerAuthenticationConverter {
    
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_HEADER = "accessToken";
    
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        // 1. accessToken 헤더에서 추출 시도
        return extractFromAccessTokenHeader(exchange)
                .switchIfEmpty(
                        // 2. Authorization: Bearer 헤더에서 추출 시도
                        extractFromAuthorizationHeader(exchange)
                )
                .doOnNext(token -> log.debug("JWT token extracted from request"))
                .map(token -> (Authentication) new UsernamePasswordAuthenticationToken(token, token));
    }
    
    /**
     * accessToken 헤더에서 토큰 추출
     */
    private Mono<String> extractFromAccessTokenHeader(ServerWebExchange exchange) {
        return Mono.justOrEmpty(
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(ACCESS_TOKEN_HEADER)
        )
        .filter(token -> !token.isBlank())
        .doOnNext(token -> log.trace("Token found in {} header", ACCESS_TOKEN_HEADER));
    }
    
    /**
     * Authorization: Bearer 헤더에서 토큰 추출
     */
    private Mono<String> extractFromAuthorizationHeader(ServerWebExchange exchange) {
        return Mono.justOrEmpty(
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION)
        )
        .filter(authHeader -> authHeader.startsWith(BEARER_PREFIX))
        .map(authHeader -> authHeader.substring(BEARER_PREFIX.length()))
        .filter(token -> !token.isBlank())
        .doOnNext(token -> log.trace("Token found in Authorization header"));
    }
}
