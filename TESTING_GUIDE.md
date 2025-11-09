# 채팅 시스템 테스트 가이드

## 🚀 빠른 시작

### 1. 애플리케이션 실행
```bash
# Gradle로 실행
./gradlew bootRun

# 또는 IDE에서 DemoApplication.java 실행
```

### 2. 브라우저에서 테스트
```
http://localhost:8080
```

---

## 📋 테스트 시나리오

### 시나리오 1: 정상 메시지 전송 (Optimistic UI)

#### 준비
1. 브라우저 탭 2개 열기
2. 탭 1: `user1` / `홍길동` / `room123`
3. 탭 2: `user2` / `김철수` / `room123`

#### 테스트
1. **탭 1에서 메시지 전송: "안녕하세요"**
   - ✅ 즉시 화면에 표시됨 (0ms)
   - ✅ 0.5초 후에도 그대로 유지
   - ✅ 에러 표시 없음

2. **탭 2 확인**
   - ✅ "홍길동: 안녕하세요" 수신
   - ✅ 타이밍: ~100ms

3. **연속 메시지 테스트**
   - 탭 1에서 빠르게 3개 메시지 전송
   - ✅ 모두 즉시 표시
   - ✅ 탭 2에서 순서대로 수신

**예상 결과:**
```
[탭 1 - 홍길동]
나: 안녕하세요          (즉시)
나: 반갑습니다          (즉시)
나: 날씨가 좋네요       (즉시)

[탭 2 - 김철수]
홍길동: 안녕하세요      (~100ms)
홍길동: 반갑습니다      (~100ms)
홍길동: 날씨가 좋네요   (~100ms)
```

---

### 시나리오 2: 실패 메시지 & 재전송

#### 테스트 A: 서버 중단
1. 탭 1에서 채팅 시작
2. **서버 중단** (IntelliJ에서 Stop 버튼)
3. 탭 1에서 메시지 전송: "테스트"

**예상 결과:**
```
[5초 후]
나: 테스트 ⚠️
    전송 실패
    탭하여 재전송
```

4. **서버 재시작**
5. 탭 1 새로고침 → 다시 입장
6. **⚠️ 아이콘 클릭** → 재전송

**예상 결과:**
```
나: 테스트              (성공!)
```

#### 테스트 B: 네트워크 끊김 시뮬레이션
1. Chrome DevTools 열기 (F12)
2. Network 탭 → Offline 선택
3. 메시지 전송: "네트워크 테스트"

**예상 결과:**
```
나: 네트워크 테스트      (즉시 표시)
↓ 5초 후
나: 네트워크 테스트 ⚠️
    전송 실패
```

4. Network → Online 선택
5. ⚠️ 아이콘 클릭 → 재전송
6. ✅ 성공!

---

### 시나리오 3: 타이핑 인디케이터

#### 테스트
1. 탭 1, 2 모두 열기
2. **탭 1에서 입력 시작** (전송하지 않음)
3. **탭 2 확인**

**예상 결과:**
```
[탭 2 하단]
● ● ●  (점 3개 애니메이션)
```

4. 3초 대기
5. ✅ 타이핑 표시 사라짐

---

### 시나리오 4: 온라인/오프라인 상태

#### 테스트
1. 탭 1, 2 모두 열기
2. **탭 2 닫기**

**예상 결과 (탭 1):**
```
[시스템 메시지]
상대방이 오프라인입니다
```

3. **탭 2 다시 열기** → 같은 채팅방 입장

**예상 결과 (탭 1):**
```
[시스템 메시지]
상대방이 온라인입니다
```

---

### 시나리오 5: 여러 채팅방 (독립성 테스트)

#### 테스트
1. 탭 1: `user1` / `홍길동` / `room123`
2. 탭 2: `user2` / `김철수` / `room123`
3. 탭 3: `user1` / `홍길동` / `room456`
4. 탭 4: `user3` / `이영희` / `room456`

**메시지 전송:**
- 탭 1 → "room123 메시지"
- 탭 3 → "room456 메시지"

**예상 결과:**
```
탭 1, 2 (room123):
✅ "room123 메시지" 표시
❌ "room456 메시지" 표시 안 됨

탭 3, 4 (room456):
✅ "room456 메시지" 표시
❌ "room123 메시지" 표시 안 됨
```

---

## 🐛 디버깅 가이드

### 콘솔 로그 확인

#### 브라우저 콘솔 (F12)
```javascript
// 정상 연결
Connecting to WebSocket: ws://localhost:8080/ws
WebSocket connected
Message received: {"type":"SUBSCRIBE",...}

// 메시지 전송
Sending message: {"type":"CHAT",...}
Message confirmed: temp-123456

// 실패
Message send timeout: temp-123456
```

#### 서버 로그 (IntelliJ)
```
INFO  WebSocket connected: sessionId=abc123
INFO  User subscribed: userId=user1, chatRoomId=room123
DEBUG Message published to Redis: room=room123
DEBUG Message forwarded to receiver: user2
```

---

### 일반적인 문제 & 해결

#### 1. WebSocket 연결 안 됨
**증상:** "연결 끊김" 계속 표시

**원인:**
- Redis가 안 떠있음
- MongoDB가 안 떠있음
- 포트 8080 이미 사용 중

**해결:**
```bash
# Redis 실행 (Docker)
docker run -d -p 6379:6379 redis:7-alpine

# 또는 로컬 Redis
redis-server

# MongoDB Atlas 연결 확인
# application.properties에서 URI 확인
```

#### 2. 메시지가 상대방에게 안 감
**증상:** 내 화면에만 표시, 상대방은 안 받음

**체크리스트:**
- [ ] 같은 `chatRoomId` 사용?
- [ ] Redis 실행 중?
- [ ] 서버 로그에 에러?

**디버깅:**
```
브라우저 콘솔:
Message received: {"type":"CHAT", "senderId":"user1",...}

서버 로그:
DEBUG Message forwarded to receiver: user2
```

#### 3. 실패 메시지가 5초가 아니라 즉시 뜸
**증상:** 전송하자마자 ⚠️ 표시

**원인:** 서버가 ERROR 응답 보냄

**확인:**
```
브라우저 콘솔:
Message received: {"type":"ERROR", "message":"..."}

서버 로그:
ERROR Failed to process message: ...
```

#### 4. Optimistic UI가 작동 안 함
**증상:** 메시지가 즉시 안 뜸

**확인:**
```javascript
// chat.html의 sendMessage() 함수 확인
// addChatMessage(optimisticMessage, true, tempId) 호출되는지 확인
```

---

## 📊 성능 테스트

### 지연 시간 측정

#### 테스트 방법
1. 브라우저 콘솔 열기
2. 아래 코드 붙여넣기:

```javascript
// 전송 시간 기록
let sendTimes = [];

// 원래 sendMessage 함수 백업
let originalSend = sendMessage;

// sendMessage 오버라이드
sendMessage = function(content) {
    const startTime = Date.now();
    sendTimes.push(startTime);
    console.log('⏱️ Message sent at:', startTime);
    
    originalSend(content);
};

// 수신 시간 기록
let originalHandle = handleMessage;
handleMessage = function(message) {
    if (message.type === 'CHAT' && message.senderId === USER_ID) {
        const endTime = Date.now();
        const sendTime = sendTimes.shift();
        const latency = endTime - sendTime;
        console.log('✅ Server confirmed in:', latency, 'ms');
    }
    
    originalHandle(message);
};

// 사용법:
// 메시지 전송 후 콘솔 확인
```

**목표 지연시간:**
- Optimistic UI: < 10ms ⚡
- 서버 저장: < 100ms
- 상대방 수신: < 200ms

---

## ✅ 테스트 체크리스트

### 기본 기능
- [ ] WebSocket 연결 성공
- [ ] 채팅방 구독 성공
- [ ] 메시지 전송 (발신자 즉시 표시)
- [ ] 메시지 수신 (수신자에게 전달)
- [ ] 여러 메시지 연속 전송

### Optimistic UI
- [ ] 전송 버튼 클릭 → 즉시 화면 표시 (< 10ms)
- [ ] 서버 확인 → 그대로 유지
- [ ] 실패 → ⚠️ 표시 (5초 타임아웃)
- [ ] 재전송 버튼 클릭 → 다시 전송

### 상태 관리
- [ ] 타이핑 인디케이터 표시
- [ ] 온라인 상태 표시
- [ ] 오프라인 상태 표시
- [ ] 재연결 자동 시도

### 예외 처리
- [ ] 서버 중단 → 재연결
- [ ] 네트워크 끊김 → 실패 표시
- [ ] 잘못된 메시지 → 에러 표시
- [ ] 빈 메시지 → 전송 안 됨

### 다중 사용자
- [ ] 2명 동시 채팅
- [ ] 여러 채팅방 독립 작동
- [ ] 메시지 순서 보장

---

## 🎓 고급 테스트

### 부하 테스트 (선택)

#### 도구: Artillery
```bash
npm install -g artillery

# test-load.yml 생성
cat > test-load.yml << EOF
config:
  target: "ws://localhost:8080"
  phases:
    - duration: 60
      arrivalRate: 10
  
scenarios:
  - engine: "ws"
    flow:
      - send:
          type: "SUBSCRIBE"
          userId: "user{{ $randomNumber() }}"
          chatRoomId: "room123"
      - think: 1
      - loop:
        - send:
            type: "CHAT"
            chatRoomId: "room123"
            senderId: "user{{ $randomNumber() }}"
            content: "Load test message"
        - think: 2
        count: 10
EOF

# 실행
artillery run test-load.yml
```

---

## 🎯 성공 기준

### ✅ 모든 테스트 통과 시:
1. 메시지가 즉시 표시됨 (< 10ms)
2. 서버 저장 완료 (< 100ms)
3. 상대방 수신 (< 200ms)
4. 실패 시 재전송 가능
5. 여러 채팅방 독립 작동

### 🎉 완벽합니다!

이제 프론트엔드 (iOS/Android)를 구현하면 실제 데이팅 앱으로 완성!
