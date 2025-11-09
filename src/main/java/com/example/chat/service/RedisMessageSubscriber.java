package com.example.chat.service;

import com.example.chat.model.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RedisMessageSubscriber {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 🔥 수정: Disposable로 변경 (구독 취소용)
    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();

    // 메시지 핸들러 (ChatWebSocketHandler가 등록)
    private MessageHandler messageHandler;

    public RedisMessageSubscriber(
            ReactiveRedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 메시지 핸들러 등록
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    /**
     * 채팅방 구독
     * 
     * 단일 서버: 같은 채팅방에 여러 user 입장 시, Redis는 1번만 구독
     * 다중 서버: 각 서버가 독립적으로 구독
     */
    public Mono<Void> subscribe(String chatRoomId) {
        String channel = "chat:" + chatRoomId;

        // 이미 구독 중이면 스킵 (같은 채팅방의 다른 유저가 입장한 경우)
        if (subscriptions.containsKey(chatRoomId)) {
            log.debug("Already subscribed to channel: {} (another user in same room)", channel);
            return Mono.empty();
        }

        log.info("Starting new Redis subscription for channel: {}", channel);

        // 🔥 수정: subscribe() 호출하고 Disposable 저장
        Disposable disposable = redisTemplate
                .listenTo(ChannelTopic.of(channel))
                .doOnNext(message -> {
                    String payload = message.getMessage();
                    log.debug("Received message from Redis channel {}: {}", channel, payload);

                    try {
                        WebSocketMessage wsMessage = objectMapper.readValue(payload, WebSocketMessage.class);

                        // 메시지 핸들러에게 전달
                        if (messageHandler != null) {
                            messageHandler.handleRedisMessage(chatRoomId, wsMessage);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse Redis message", e);
                    }
                })
                .doOnError(error -> {
                    log.error("Error in Redis subscription for channel: {}", channel, error);
                    // 에러 발생 시 구독 제거
                    subscriptions.remove(chatRoomId);
                })
                .doOnComplete(() -> {
                    log.info("Redis subscription completed for channel: {}", channel);
                    subscriptions.remove(chatRoomId);
                })
                .subscribe();  // ← 여기서 subscribe() 호출!

        // Disposable 저장
        subscriptions.put(chatRoomId, disposable);
        log.info("Subscribed to Redis channel: {} (subscribers in this server can receive messages)", channel);

        return Mono.empty();
    }

    /**
     * 채팅방 구독 해제
     */
    public Mono<Void> unsubscribe(String chatRoomId) {
        Disposable disposable = subscriptions.remove(chatRoomId);

        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            log.info("Unsubscribed from Redis channel: chat:{}", chatRoomId);
        }

        return Mono.empty();
    }

    /**
     * 모든 구독 해제 (서버 종료 시)
     */
    @PreDestroy
    public void destroy() {
        subscriptions.forEach((chatRoomId, disposable) -> {
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        });
        subscriptions.clear();
        log.info("All Redis subscriptions cancelled");
    }

    /**
     * 메시지 핸들러 인터페이스
     */
    @FunctionalInterface
    public interface MessageHandler {
        void handleRedisMessage(String chatRoomId, WebSocketMessage message);
    }
}