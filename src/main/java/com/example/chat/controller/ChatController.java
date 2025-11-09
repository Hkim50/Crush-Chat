package com.example.chat.controller;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatRoom;
import com.example.chat.service.ChatService;
import com.example.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    /**
     * 채팅방 생성
     * 매칭 성공 시 기존 백엔드에서 호출
     */
    @PostMapping("/rooms")
    public Mono<ResponseEntity<ChatRoom>> createOrGetChatRoom(
            @RequestBody CreateChatRoomRequest request
    ) {
        log.info("Creating chat room: chatRoomId={}, users: {} and {}, matchId: {}", 
                 request.getChatRoomId(), request.getUser1Id(), request.getUser2Id(), request.getMatchId());

        // chatRoomId가 지정된 경우 (메인 서버에서 UUID 전달)
        if (request.getChatRoomId() != null && !request.getChatRoomId().isEmpty()) {
            return chatService.createChatRoomWithId(
                    request.getChatRoomId(),
                    request.getUser1Id(), 
                    request.getUser2Id(),
                    request.getMatchId(),
                    request.getMatchType()
                )
                .map(ResponseEntity::ok)
                .doOnSuccess(room -> log.info("Chat room created with specified ID: {}", room.getBody().getId()))
                .onErrorResume(e -> {
                    log.error("Failed to create chat room", e);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
        }
        
        // chatRoomId가 없는 경우 (하위 호환성 - 자동 생성)
        return chatService.getOrCreateChatRoom(
                request.getUser1Id(), 
                request.getUser2Id(),
                request.getMatchId(),
                request.getMatchType()
            )
            .map(ResponseEntity::ok)
            .doOnSuccess(room -> log.info("Chat room created/retrieved: {}", room.getBody().getId()))
            .onErrorResume(e -> {
                log.error("Failed to create chat room", e);
                return Mono.just(ResponseEntity.internalServerError().build());
            });
    }

    // DTO for chat room creation
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateChatRoomRequest {
        private String chatRoomId;  // 메인 서버에서 생성한 UUID
        private String user1Id;
        private String user2Id;
        private Long matchId;  // 기존 백엔드의 매칭 ID
        private String matchType; // "SWIPE" or "RANDOM_AI"
    }

    /**
     * 채팅방의 메시지 조회
     */
    @GetMapping("/rooms/{chatRoomId}/messages")
    public Flux<ChatMessage> getMessages(
            @PathVariable String chatRoomId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return chatService.getMessages(chatRoomId, limit);
    }

    /**
     * 사용자 온라인 상태 확인
     */
    @GetMapping("/users/{userId}/online")
    public Mono<ResponseEntity<Map<String, Boolean>>> checkOnlineStatus(
            @PathVariable String userId
    ) {
        return presenceService.isOnline(userId)
                .map(online -> ResponseEntity.ok(Map.of("online", online)));
    }
}