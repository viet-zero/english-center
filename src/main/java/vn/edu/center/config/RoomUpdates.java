package vn.edu.center.config;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class RoomUpdates implements WebSocketConfigurer {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessions.add(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                sessions.remove(session);
            }
        }, "/api/training/room-updates");
    }

    @TransactionalEventListener
    public void publish(BookingSaved event) {
        TextMessage message = new TextMessage(event.day().toString());
        for (WebSocketSession session : sessions) {
            synchronized (session) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    }
                } catch (IOException | IllegalStateException failure) {
                    sessions.remove(session);
                    try {
                        session.close(CloseStatus.SERVER_ERROR);
                    } catch (IOException ignored) {
                        // The connection is already unusable.
                    }
                }
            }
        }
    }

    public record BookingSaved(LocalDate day) {}
}
