package com.example.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    private String id;

    @Indexed
    private String chatRoomId;

    private String senderId;
    private String senderName;  // 테스트용 (실제론 User 서비스에서 가져와야 함)

    private MessageType type;

    private String content;

    @Indexed
    private Instant timestamp;

    private Instant readAt;

    private Boolean deleted;
}