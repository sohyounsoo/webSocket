package com.example.demo.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> CLIENTS
            = new ConcurrentHashMap<String, WebSocketSession>();

    private static StringTokenizer st;

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
        String[] sessionIdStrings = sessionId.split("-");

        // 대화창에 표시할 보낸 사람 아이디
        String senderId = sessionIdStrings[0];

        // 메시지 내용 파싱
        String textMessagePayload = textMessage.getPayload();

        // /귓속말:(sessionId 앞 부분) (내용) <- 개인 메시지 양식
        if (textMessagePayload.contains("/귓속말")) {
            st = new StringTokenizer(textMessagePayload, " ");
            String partialReceiverSessionId = st.nextToken().replaceAll("/귓속말:", "");

            // 세션 id의 일부분만을 가지고 전체 세션 id를 찾는 코드
            // 중복 가능성이 있지만 보다 짧은 세션 id 사용을 위해 적용
            String receiverSessionId = "";
            for (Map.Entry<String, WebSocketSession> entry : CLIENTS.entrySet()) {
                if (entry.getKey().contains(partialReceiverSessionId)) {
                    receiverSessionId = entry.getKey();
                }
            }

            // 특정 개인에게 메시지 전달
            WebSocketSession receiver = CLIENTS.get(receiverSessionId);

            if (receiver != null && receiver.isOpen()) {
                receiver.sendMessage(new TextMessage(senderId + " : " + st.nextToken()));
            }
        } else {
            // 대화방 전체에 메시지 전달
            CLIENTS.values().forEach(s -> {
                try {
                    // 자신을 제외한 참가자들에게 메시지 전달
                    if (!sessionId.equals(s.getId())) {
                        s.sendMessage(new TextMessage(senderId + " : " + textMessagePayload));
                    }

                } catch (IOException e) {
                    throw new RuntimeException("message 전송 실패!!!");
                }
            });
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        CLIENTS.remove(session.getId());
    }
}
