package com.example.chat.service;

import com.example.chat.model.ChatMessage;
import com.example.chat.model.ChatRoom;
import com.example.chat.model.MessageType;
import com.example.chat.model.WebSocketMessage;
import com.example.chat.repository.ChatMessageRepository;
import com.example.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RedisMessagePublisher redisPublisher;

    /**
     * 채팅방 생성 (UUID 지정 - 메인 서버에서 전달)
     */
    public Mono<ChatRoom> createChatRoomWithId(String chatRoomId, String user1Id, String user2Id, Long matchId, String matchType) {
        // ID 정렬
        String sortedUser1 = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        String sortedUser2 = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

        // 중복 체크 (멱등성 보장)
        return chatRoomRepository.findById(chatRoomId)
                .switchIfEmpty(
                        Mono.defer(() -> {
                            ChatRoom newRoom = ChatRoom.builder()
                                    .id(chatRoomId)  // 메인 서버에서 생성한 UUID 사용
                                    .user1Id(sortedUser1)
                                    .user2Id(sortedUser2)
                                    .matchId(matchId)
                                    .matchType(matchType)
                                    .createdAt(Instant.now())
                                    .isActive(true)
                                    .build();
                            return chatRoomRepository.save(newRoom);
                        })
                )
                .doOnSuccess(room -> log.info("ChatRoom created/retrieved with UUID: id={}, matchId={}", chatRoomId, matchId));
    }

    /**
     * 채팅방 생성 또는 조회 (매칭 정보 포함)
     */
    public Mono<ChatRoom> getOrCreateChatRoom(String user1Id, String user2Id, Long matchId, String matchType) {
        // ID 정렬해서 일관된 조회
        String sortedUser1 = user1Id.compareTo(user2Id) < 0 ? user1Id : user2Id;
        String sortedUser2 = user1Id.compareTo(user2Id) < 0 ? user2Id : user1Id;

        return chatRoomRepository
                .findByUser1IdAndUser2Id(sortedUser1, sortedUser2)
                .switchIfEmpty(
                        Mono.defer(() -> {
                            ChatRoom newRoom = ChatRoom.builder()
                                    .id(ChatRoom.generateRoomId(matchId))  // matchId 기반 ID
                                    .user1Id(sortedUser1)
                                    .user2Id(sortedUser2)
                                    .matchId(matchId)
                                    .matchType(matchType)
                                    .createdAt(Instant.now())
                                    .isActive(true)
                                    .build();
                            return chatRoomRepository.save(newRoom);
                        })
                )
                .doOnSuccess(room -> log.info("ChatRoom retrieved/created: {} for match: {}", room.getId(), matchId));
    }



    /**
     * 채팅방 조회 (ID로)
     */
    public Mono<ChatRoom> getChatRoom(String chatRoomId) {
        return chatRoomRepository.findById(chatRoomId)
                .doOnSuccess(room -> log.debug("ChatRoom found: {}", chatRoomId))
                .doOnError(error -> log.error("ChatRoom not found: {}", chatRoomId, error));
    }

    /**
     * 메시지 저장
     */
    public Mono<ChatMessage> saveMessage(WebSocketMessage wsMessage) {
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoomId(wsMessage.getChatRoomId())
                .senderId(wsMessage.getSenderId())
                .senderName(wsMessage.getSenderName())
                .type(MessageType.CHAT)
                .content(wsMessage.getContent())
                .timestamp(Instant.now())
                .deleted(false)
                .build();

        return chatMessageRepository.save(chatMessage)
                .flatMap(saved ->
                        // ChatRoom의 마지막 메시지 업데이트
                        updateLastMessage(saved.getChatRoomId(), saved.getContent())
                                .thenReturn(saved)
                )
                .doOnSuccess(saved -> log.info("Message saved: {}", saved.getId()));
    }

    /**
     * ChatRoom의 마지막 메시지 업데이트
     */
    private Mono<ChatRoom> updateLastMessage(String chatRoomId, String content) {
        return chatRoomRepository.findById(chatRoomId)
                .flatMap(room -> {
                    room.setLastMessage(content);
                    room.setLastMessageAt(Instant.now());
                    return chatRoomRepository.save(room);
                });
    }

    /**
     * 채팅방의 메시지 조회
     */
    public Flux<ChatMessage> getMessages(String chatRoomId, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "timestamp"));
        return chatMessageRepository
                .findByChatRoomIdOrderByTimestampDesc(chatRoomId, pageRequest)
                .doOnComplete(() -> log.debug("Retrieved messages for room: {}", chatRoomId));
    }

    /**
     * 메시지를 Redis Pub/Sub으로 발행
     */
    public Mono<Long> publishMessage(String chatRoomId, WebSocketMessage message) {
        return redisPublisher.publishMessage(chatRoomId, message);
    }
}
