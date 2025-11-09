package com.example.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String ONLINE_KEY_PREFIX = "user:online:";
    private static final long ONLINE_TIMEOUT_SECONDS = 60; // 60초

    /**
     * 사용자를 온라인으로 설정
     */
    public Mono<Boolean> setOnline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate
                .opsForValue()
                .set(key, "true", Duration.ofSeconds(ONLINE_TIMEOUT_SECONDS))
                .doOnSuccess(result -> log.debug("User {} set online: {}", userId, result))
                .doOnError(error -> log.error("Failed to set user {} online", userId, error));
    }

    /**
     * 사용자를 오프라인으로 설정
     */
    public Mono<Boolean> setOffline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate
                .delete(key)
                .map(count -> count > 0)
                .doOnSuccess(result -> log.debug("User {} set offline: {}", userId, result))
                .doOnError(error -> log.error("Failed to set user {} offline", userId, error));
    }

    /**
     * 사용자의 온라인 상태 확인
     */
    public Mono<Boolean> isOnline(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate
                .hasKey(key)
                .defaultIfEmpty(false);
    }

    /**
     * Heartbeat - TTL 갱신
     */
    public Mono<Boolean> heartbeat(String userId) {
        String key = ONLINE_KEY_PREFIX + userId;
        return redisTemplate
                .expire(key, Duration.ofSeconds(ONLINE_TIMEOUT_SECONDS))
                .doOnSuccess(result -> log.debug("Heartbeat for user {}: {}", userId, result))
                .onErrorReturn(false);
    }
}