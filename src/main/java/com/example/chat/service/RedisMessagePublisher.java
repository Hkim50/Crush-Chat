package com.example.chat.service;

import com.example.chat.model.WebSocketMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMessagePublisher {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;


    private static final String CHANNEL_PREFIX = "chat:";

    /**
     * 채팅방에 메시지 발행
     */
    public Mono<Long> publishMessage(String chatRoomId, WebSocketMessage message) {
        String channel = CHANNEL_PREFIX + chatRoomId;

        return Mono.fromCallable(() -> objectMapper.writeValueAsString(message))
                .flatMap(json -> redisTemplate.convertAndSend(channel, json))
                .doOnSuccess(count -> log.debug("Published message to channel {}: {} subscribers", channel, count))
                .doOnError(error -> log.error("Failed to publish message to channel {}", channel, error))
                .onErrorReturn(0L);
    }
}