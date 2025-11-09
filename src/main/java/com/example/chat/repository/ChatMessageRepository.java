package com.example.chat.repository;

import com.example.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {

    // 채팅방의 메시지 조회 (최신순)
    Flux<ChatMessage> findByChatRoomIdOrderByTimestampDesc(String chatRoomId, Pageable pageable);

    // 채팅방의 메시지 개수
    Mono<Long> countByChatRoomId(String chatRoomId);

    // 특정 시간 이후의 메시지 조회
    Flux<ChatMessage> findByChatRoomIdAndTimestampAfterOrderByTimestampAsc(
            String chatRoomId,
            java.time.Instant timestamp
    );
}