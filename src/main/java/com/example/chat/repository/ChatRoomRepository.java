package com.example.chat.repository;

import com.example.chat.model.ChatRoom;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ChatRoomRepository extends ReactiveMongoRepository<ChatRoom, String> {

    Mono<ChatRoom> findByUser1IdAndUser2Id(String user1Id, String user2Id);
}