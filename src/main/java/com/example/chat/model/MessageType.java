package com.example.chat.model;

public enum MessageType {
    CHAT,        // 일반 채팅 메시지
    SUBSCRIBE,   // 채팅방 구독
    PRESENCE,    // 온라인 상태 변경
    ERROR        // 에러
}