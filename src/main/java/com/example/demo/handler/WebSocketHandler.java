package com.example.demo.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> CLIENTS
            = new ConcurrentHashMap<String, WebSocketSession>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 세션의 ID는 서버가 클라이언트와의 WebSocket 연결을 수립할 때 자동으로 생성
        String sessionId = session.getId();

        // 세션 저장
        CLIENTS.put(sessionId, session);

        CLIENTS.values().forEach(s -> {
            try {
                s.sendMessage(new TextMessage(sessionId + "님이 대화방에 들어오셨습니다."));

            } catch (IOException e) {
                throw new RuntimeException("message 전송 실패!!!");
            }
        });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String sessionId = session.getId();
        String senderId = sessionId.split("-")[0]; // 보낸 사람 ID

        String textMessagePayload = textMessage.getPayload();
        String[] parts = textMessagePayload.split(" ", 2);

        if (textMessagePayload.startsWith("/귓속말")) {
            if (parts.length < 2) {
                // 귓속말 형식 안내
                session.sendMessage(new TextMessage("귓속말 형식이 잘못되었습니다. 올바른 형식: /귓속말:상대방ID 메시지"));
                return;
            }

            // 귓속말 대상 ID와 메시지 추출
            String partialReceiverSessionId = parts[0].replace("/귓속말:", "");
            String message = parts[1];

            // 대상 세션 ID 찾기
            String receiverSessionId = findReceiverSessionId(partialReceiverSessionId);

            WebSocketSession receiver = CLIENTS.get(receiverSessionId);
            if (receiver != null && receiver.isOpen()) {
                // 귓속말 전달
                receiver.sendMessage(new TextMessage(senderId + "님의 귓속말: " + message));
            } else {
                // 대상이 접속 중이 아닐 경우
                session.sendMessage(new TextMessage("귓속말 전송 실패: 상대방이 접속 중이지 않습니다."));
            }
        } else {
            // 전체 메시지 전달
            broadcastMessage(sessionId, senderId, textMessagePayload);
        }
    }

    private String findReceiverSessionId(String partialId) {
        for (Map.Entry<String, WebSocketSession> entry : CLIENTS.entrySet()) {
            if (entry.getKey().contains(partialId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void broadcastMessage(String senderSessionId, String senderId, String message) {
        CLIENTS.values().forEach(client -> {
            try {
                if (!client.getId().equals(senderSessionId)) {
                    client.sendMessage(new TextMessage(senderId + " : " + message));
                }
            } catch (IOException e) {
                throw new RuntimeException("message 전송 실패!!!");
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();

        CLIENTS.remove(sessionId);

        CLIENTS.values().forEach(s -> {
            try {
                s.sendMessage(new TextMessage(sessionId + "님이 대화방을 나가셨습니다."));
            } catch (IOException e) {
                throw new RuntimeException("message 전송 실패!!!");
            }
        });
    }
}
