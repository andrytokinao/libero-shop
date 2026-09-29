package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one way the server pushes something to a screen.
 *
 * <p>Two addressing modes, and only two:
 * <ul>
 *   <li><b>to accounts</b> -- {@link #sendToUser}, {@link #sendToUsers}, {@link #sendToRoles}.
 *       Delivered on {@code /user/queue/notifications}, which the broker resolves per connection:
 *       each browser subscribes to that one path and receives only what was addressed to its
 *       own account, on every tab it has open.</li>
 *   <li><b>to a topic</b> -- {@link #publish}. Delivered on {@code /topic/<name>} to whoever
 *       subscribed. Any signed-in account may subscribe to any topic, so a topic is for what the
 *       whole shop may see; anything meant for one job goes through {@link #sendToRoles}.</li>
 * </ul>
 *
 * <p>Pushing is best effort, and says so. A screen that is closed misses the message -- nothing
 * is queued for it -- and that is acceptable because every notification is a hint to reload
 * something the REST API remains the source of. A failure to push is logged and swallowed: it
 * must never undo, or even delay, the sale that caused it.
 */
@Service
public class NotificationService {

    /** What a browser subscribes to for the messages addressed to its account. */
    public static final String USER_QUEUE = "/queue/notifications";
    public static final String USER_PREFIX = "/user";
    public static final String TOPIC_PREFIX = "/topic";

    /** Topic names are path segments: letters, digits, dots and dashes. No wildcard, no slash. */
    private static final Pattern TOPIC_NAME = Pattern.compile("[a-z0-9][a-z0-9.-]{0,63}");

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final SimpMessagingTemplate messaging;
    private final SimpUserRegistry connected;

    public NotificationService(SimpMessagingTemplate messaging, SimpUserRegistry connected) {
        this.messaging = messaging;
        this.connected = connected;
    }

    /** To one account, on every screen it has open. Nothing happens if it has none. */
    public void sendToUser(Long userId, Notification notification) {
        Objects.requireNonNull(userId, "userId");
        try {
            messaging.convertAndSendToUser(String.valueOf(userId), USER_QUEUE, notification);
        } catch (MessagingException e) {
            log.warn("Notification {} not pushed to user {}: {}",
                    notification.type(), userId, e.getMessage());
        }
    }

    /** To several accounts, each once however it appears in the collection. */
    public void sendToUsers(Collection<Long> userIds, Notification notification) {
        new LinkedHashSet<>(userIds).forEach(userId -> sendToUser(userId, notification));
    }

    /**
     * To every connected account holding at least one of these roles -- once each, even when it
     * holds several of them.
     *
     * <p>Only the connected accounts are looked at, from the broker's own registry: a message
     * nobody can receive is not worth a query to find whom it would have been for.
     *
     * @param except an account to leave out, typically the one whose action caused the message;
     *               null to leave out nobody
     * @return how many accounts it was addressed to
     */
    public int sendToRoles(Set<RoleApp> roles, Notification notification, Long except) {
        Set<Long> recipients = new LinkedHashSet<>();
        for (SimpUser user : connected.getUsers()) {
            if (user.getPrincipal() instanceof SocketPrincipal principal
                    && principal.holdsAny(roles)
                    && !principal.userId().equals(except)) {
                recipients.add(principal.userId());
            }
        }
        sendToUsers(recipients, notification);
        return recipients.size();
    }

    /**
     * To everyone subscribed to {@code /topic/<name>}.
     *
     * @param name a bare topic name such as {@code "stock"}; the prefix is added here so that
     *             no caller can publish into another part of the broker's address space
     */
    public void publish(String name, Object payload) {
        if (name == null || !TOPIC_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nom de topic invalide : " + name);
        }
        try {
            messaging.convertAndSend(TOPIC_PREFIX + "/" + name, payload);
        } catch (MessagingException e) {
            log.warn("Message not published on topic {}: {}", name, e.getMessage());
        }
    }
}
