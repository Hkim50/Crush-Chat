package com.example.chat.handler;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.MessageType;
import com.example.chat.model.WebSocketMessage;
import com.example.chat.service.ChatService;
import com.example.chat.service.PresenceService;
import com.example.chat.service.RedisMessageSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final PresenceService presenceService;
    private final RedisMessageSubscriber redisSubscriber;
    private final ObjectMapper objectMapper;

    // 세션 관리: userId -> WebSocketSession
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 채팅방 구독 관리: chatRoomId -> Set<userId>
    private final Map<String, Set<String>> roomSubscriptions = new ConcurrentHashMap<>();

    // 각 세션의 메시지 Sink
    private final Map<String, Sinks.Many<WebSocketMessage>> sessionSinks = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            ChatService chatService,
            PresenceService presenceService,
            RedisMessageSubscriber redisSubscriber,
            ObjectMapper objectMapper
    ) {
        this.chatService = chatService;
        this.presenceService = presenceService;
        this.redisSubscriber = redisSubscriber;
        this.objectMapper = objectMapper;
    }

    /**
     *  Redis 메시지 핸들러 등록
     */
    @PostConstruct
    public void init() {
        redisSubscriber.setMessageHandler(this::handleRedisMessage);
        log.info("Redis message handler registered");
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket connected: sessionId={}", sessionId);

        // 세션별 Sink 생성 (메시지 전송용)
        Sinks.Many<WebSocketMessage> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);

        // 메시지 수신 처리
        Mono<Void> input = session.receive()
                .flatMap(message -> handleIncomingMessage(session, message.getPayloadAsText()))
                .doOnError(error -> log.error("Error receiving message: sessionId={}", sessionId, error))
                .then();

        // 메시지 송신 처리
        Mono<Void> output = session.send(
                sink.asFlux()
                        .map(msg -> {
                            try {
                                String json = objectMapper.writeValueAsString(msg);
                                return session.textMessage(json);
                            } catch (Exception e) {
                                log.error("Failed to serialize message", e);
                                return session.textMessage("{\"type\":\"ERROR\",\"message\":\"Serialization error\"}");
                            }
                        })
        );

        // 연결 종료 처리
        return Mono.zip(input, output)
                .doFinally(signalType -> {
                    log.info("WebSocket disconnected: sessionId={}, signal={}", sessionId, signalType);
                    handleDisconnect(sessionId);
                })
                .then();
    }

    /**
     * 수신 메시지 처리
     */
    private Mono<Void> handleIncomingMessage(WebSocketSession session, String payload) {
        return Mono.fromCallable(() -> objectMapper.readValue(payload, WebSocketMessage.class))
                .flatMap(wsMessage -> {
                    log.debug("Received message: type={}, chatRoomId={}, userId={}",
                            wsMessage.getType(), wsMessage.getChatRoomId(), wsMessage.getUserId());

                    switch (wsMessage.getType()) {
                        case SUBSCRIBE:
                            return handleSubscribe(session, wsMessage);
                        case CHAT:
                            return handleChatMessage(session, wsMessage);
                        default:
                            return sendToSession(session.getId(),
                                    WebSocketMessage.error("Unknown message type"));
                    }
                })
                .onErrorResume(error -> {
                    log.error("Failed to process message: sessionId={}", session.getId(), error);
                    return sendToSession(session.getId(),
                            WebSocketMessage.error("Invalid message format: " + error.getMessage()));
                })
                .then();
    }

    /**
     * 채팅방 구독 처리
     */
    private Mono<Void> handleSubscribe(WebSocketSession session, WebSocketMessage wsMessage) {
        String userId = wsMessage.getUserId();
        String chatRoomId = wsMessage.getChatRoomId();
        String sessionId = session.getId();

        // 세션 저장 (온라인 판단 기준)
        sessions.put(userId, session);

        // 채팅방 구독 저장
        roomSubscriptions.computeIfAbsent(chatRoomId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);

        log.info("User subscribed: userId={}, chatRoomId={}, sessionId={}", userId, chatRoomId, sessionId);

        // 1. 채팅방 존재 확인 (메인 백엔드에서 이미 생성되어 있어야 함)
        return chatService.getChatRoom(chatRoomId)
                .switchIfEmpty(
                    Mono.error(new RuntimeException("ChatRoom not found: " + chatRoomId + ". ChatRoom must be created by main backend first."))
                )
                .then(Mono.defer(() -> {
                    // 2. Redis 채널 구독 (다중 서버 대비)
                    return redisSubscriber.subscribe(chatRoomId);
                }))
                .then(Mono.defer(() -> {
                    // 3. 채팅방의 다른 사용자에게 온라인 알림
                    WebSocketMessage presenceMsg = WebSocketMessage.presence(userId, true);
                    return broadcastToChatRoom(chatRoomId, presenceMsg, userId);
                }))
                .then();
    }

    /**
     * 채팅 메시지 처리
     */
    private Mono<Void> handleChatMessage(WebSocketSession session, WebSocketMessage wsMessage) {
        String chatRoomId = wsMessage.getChatRoomId();
        String senderId = wsMessage.getSenderId();

        // 1단계: MongoDB에 저장
        return chatService.saveMessage(wsMessage)
                .flatMap(savedMessage -> {
                    // WebSocketMessage로 변환
                    WebSocketMessage response = WebSocketMessage.fromChatMessage(savedMessage);

                    // 🔥 발신자에게 즉시 echo (Optimistic UI 확인용)
                    sendToSession(session.getId(), response).subscribe();
                    log.debug("Message echoed to sender: {}", senderId);

                    // 2단계: 채팅방 정보 조회하여 수신자 확인
                    return chatService.getChatRoom(chatRoomId)
                            .flatMap(chatRoom -> {
                                String receiverId = chatRoom.getOtherUserId(senderId);
                                
                                // 3단계: 수신자 온라인 확인
                                WebSocketSession receiverSession = sessions.get(receiverId);
                                
                                if (receiverSession == null || !receiverSession.isOpen()) {
                                    // 🔔 오프라인 → 푸시 알림 전송
                                    log.info("Receiver {} is offline, sending push notification", receiverId);
                                    sendPushNotification(receiverId, response);
                                }
                                
                                // 4단계: Redis Pub/Sub으로 발행 (온라인 수신자 & 다른 서버 대비)
                                return chatService.publishMessage(chatRoomId, response)
                                        .doOnSuccess(count -> 
                                            log.debug("Message published to Redis: room={}", chatRoomId)
                                        );
                            });
                })
                .then();
    }

    /**
     * Redis에서 메시지 수신 시 처리
     * 
     * 단일 서버 환경:
     * - 발신자: Optimistic UI (서버 응답 불필요)
     * - 수신자: 이 메서드에서 메시지 수신
     * 
     * 다중 서버 환경 (미래):
     * - 다른 서버의 메시지도 Redis를 통해 수신
     */
    private void handleRedisMessage(String chatRoomId, WebSocketMessage message) {
        log.debug("Handling Redis message for room {}: type={}", chatRoomId, message.getType());

        // CHAT 메시지 처리
        if (message.getType() == MessageType.CHAT) {
            String senderId = message.getSenderId();
            
            // 채팅방 정보 조회하여 수신자에게만 전송
            chatService.getChatRoom(chatRoomId)
                .subscribe(chatRoom -> {
                    String receiverId = chatRoom.getOtherUserId(senderId);
                    
                    // 수신자에게만 메시지 포워딩 (발신자는 Optimistic UI로 이미 봄)
                    WebSocketSession receiverSession = sessions.get(receiverId);
                    
                    if (receiverSession != null && receiverSession.isOpen()) {
                        sendToSession(receiverSession.getId(), message).subscribe();
                        log.debug("Message forwarded to receiver: {}", receiverId);
                    } else {
                        log.debug("Receiver {} not connected (offline or other server)", receiverId);
                    }
                }, error -> {
                    log.error("Failed to find chat room: {}", chatRoomId, error);
                });
            return;
        }
        
        // PRESENCE 메시지는 온라인 구독자에게만 전송
        if (message.getType() == MessageType.PRESENCE) {
            Set<String> subscribers = roomSubscriptions.get(chatRoomId);
            if (subscribers != null && !subscribers.isEmpty()) {
                subscribers.forEach(userId -> {
                    // 본인은 제외
                    if (message.getUserId() != null && userId.equals(message.getUserId())) {
                        return;
                    }
                    
                    WebSocketSession session = sessions.get(userId);
                    if (session != null && session.isOpen()) {
                        sendToSession(session.getId(), message).subscribe();
                    }
                });
            }
        }
    }
    
    /**
     * 푸시 알림 전송 (구현 필요)
     */
    private void sendPushNotification(String userId, WebSocketMessage message) {
        // TODO: 메인 백엔드의 푸시 알림 API 호출
        // 또는 FCM/APNs 직접 호출
        log.info("TODO: Send push notification to userId={}, content={}", 
                userId, message.getContent());
    }

/**
     * 채팅방의 모든 구독자에게 메시지 전송 (로컬 서버만)
     */
    private Mono<Void> broadcastToChatRoom(String chatRoomId,
                                           WebSocketMessage message,
                                           String excludeUserId) {
        Set<String> subscribers = roomSubscriptions.get(chatRoomId);

        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for chatRoomId={}", chatRoomId);
            return Mono.empty();
        }

        return Flux.fromIterable(subscribers)
                .filter(userId -> excludeUserId == null || !userId.equals(excludeUserId))
                .flatMap(userId -> {
                    WebSocketSession session = sessions.get(userId);
                    if (session != null && session.isOpen()) {
                        return sendToSession(session.getId(), message);
                    }
                    return Mono.empty();
                })
                .then();
    }

    /**
     * 특정 세션에 메시지 전송
     */
    private Mono<Void> sendToSession(String sessionId, WebSocketMessage message) {
        Sinks.Many<WebSocketMessage> sink = sessionSinks.get(sessionId);

        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            if (result.isFailure()) {
                log.warn("Failed to emit message to session {}: {}", sessionId, result);
            }
            return Mono.empty();
        } else {
            log.warn("Sink not found for sessionId={}", sessionId);
            return Mono.empty();
        }
    }

    /**
     * 연결 해제 처리
     */
    private void handleDisconnect(String sessionId) {
        // 해당 세션의 userId 찾기
        String disconnectedUserId = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().getId().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (disconnectedUserId != null) {
            // 세션 제거 (오프라인 처리)
            sessions.remove(disconnectedUserId);

            // 모든 채팅방 구독에서 제거
            roomSubscriptions.values().forEach(subscribers ->
                    subscribers.remove(disconnectedUserId)
            );

            log.info("User disconnected: userId={}", disconnectedUserId);

            // 구독 중이던 채팅방들에 오프라인 알림
            roomSubscriptions.forEach((chatRoomId, subscribers) -> {
                if (!subscribers.isEmpty()) {
                    WebSocketMessage presenceMsg = WebSocketMessage.presence(disconnectedUserId, false);
                    chatService.publishMessage(chatRoomId, presenceMsg).subscribe();
                }
            });
        }

        // Sink 정리
        Sinks.Many<WebSocketMessage> sink = sessionSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }

        log.info("Session cleaned up: sessionId={}", sessionId);
    }
}
