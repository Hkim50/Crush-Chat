package com.example.chat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "chat_rooms")
@CompoundIndex(name = "user1_user2_idx", def = "{'user1Id': 1, 'user2Id': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    private String id;

    private String user1Id;
    private String user2Id;

    // 기존 백엔드의 매칭 정보
    private Long matchId;  // MySQL의 matches 테이블 ID
    private String matchType;  // "SWIPE" or "RANDOM_AI"

    private String lastMessage;
    private Instant lastMessageAt;

    private Instant createdAt;
    private boolean isActive;  // 채팅방 활성화 상태

    /**
     * matchId 기반 채팅방 ID 생성 (하위 호환성)
     * @deprecated UUID 사용 권장 (createChatRoomWithId)
     */
    @Deprecated
    public static String generateRoomId(Long matchId) {
        return "match_" + matchId;
    }

    // 상대방 ID 가져오기
    public String getOtherUserId(String myUserId) {
        return user1Id.equals(myUserId) ? user2Id : user1Id;
    }

    // 참가자인지 확인
    public boolean isParticipant(String userId) {
        return user1Id.equals(userId) || user2Id.equals(userId);
    }
}