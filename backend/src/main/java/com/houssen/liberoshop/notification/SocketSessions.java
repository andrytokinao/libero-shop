package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.service.AccountAccessChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The WebSocket connections that are open, so that an account's can be closed.
 *
 * <p>A connection proves who it is once, on its {@code CONNECT} frame, and then stays open for
 * hours. Without this an account disabled at noon would go on receiving until its browser was
 * closed, and one whose roles changed would keep being addressed by the old ones. So when an
 * account's access changes -- {@link AccountAccessChangedEvent} -- every connection it has is
 * closed. The browser reconnects by itself and proves who it is again: a token that still
 * stands gets a new connection, with the account's roles of that moment; a revoked one, or a
 * disabled account, is refused by {@link StompAuthenticationInterceptor}.
 */
@Component
public class SocketSessions implements WebSocketHandlerDecoratorFactory {

    private static final Logger log = LoggerFactory.getLogger(SocketSessions.class);

    private final Map<String, WebSocketSession> open = new ConcurrentHashMap<>();
    private final SimpUserRegistry connected;

    /** Lazy: the registry is declared by the configuration this factory is handed to. */
    public SocketSessions(@Lazy SimpUserRegistry connected) {
        this.connected = connected;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                open.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                open.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    /** After the commit, so that the connection coming back finds the account as it now is. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccessChanged(AccountAccessChangedEvent event) {
        closeFor(event.userId());
    }

    /** Closes every connection of the account. Nothing happens if it has none. */
    public int closeFor(Long userId) {
        SimpUser user = connected.getUser(String.valueOf(userId));
        if (user == null) {
            return 0;
        }
        int closed = 0;
        for (SimpSession session : user.getSessions()) {
            WebSocketSession socket = open.get(session.getId());
            if (socket == null) {
                continue;
            }
            try {
                socket.close(CloseStatus.POLICY_VIOLATION.withReason("Acces modifie : reconnexion requise."));
                closed++;
            } catch (IOException e) {
                log.warn("Connexion {} du compte {} non fermee : {}", session.getId(), userId, e.getMessage());
            }
        }
        return closed;
    }
}
