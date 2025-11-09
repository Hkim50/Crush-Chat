package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    private MessageType type;

    // 공통
    private String userId;
    private String chatRoomId;

    // CHAT 타입
    private String id;
    private String senderId;
    private String senderName;
    private String content;
    private Instant timestamp;

    // PRESENCE 타입
    private Boolean online;

    // ERROR 타입
    private String message;

    // 간편 생성 메서드
//    public static WebSocketMessage pong() {
//        return WebSocketMessage.builder()
//                .type(MessageType.PONG)
//                .build();
//    }

    public static WebSocketMessage error(String message) {
        return WebSocketMessage.builder()
                .type(MessageType.ERROR)
                .message(message)
                .build();
    }

    public static WebSocketMessage presence(String userId, boolean online) {
        return WebSocketMessage.builder()
                .type(MessageType.PRESENCE)
                .userId(userId)
                .online(online)
                .build();
    }

    public static WebSocketMessage fromChatMessage(ChatMessage chatMessage) {
        return WebSocketMessage.builder()
                .type(MessageType.CHAT)
                .id(chatMessage.getId())
                .chatRoomId(chatMessage.getChatRoomId())
                .senderId(chatMessage.getSenderId())
                .senderName(chatMessage.getSenderName())
                .content(chatMessage.getContent())
                .timestamp(chatMessage.getTimestamp())
                .build();
    }
}