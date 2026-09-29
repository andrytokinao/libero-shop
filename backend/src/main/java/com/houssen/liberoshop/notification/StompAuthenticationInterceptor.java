package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.security.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks every frame a browser sends on the WebSocket, and decides who it is.
 *
 * <p>A browser cannot put an {@code Authorization} header on a WebSocket handshake, so the
 * handshake itself is open and the identity is proven one step later, on the STOMP
 * {@code CONNECT} frame, which does carry headers. The token is the same bearer token the REST
 * calls use, checked by the same decoder -- signature, issuer, expiry -- and the account is read
 * once to refuse a disabled one, as {@code CurrentUser} does for every write.
 *
 * <p>After that the connection is read-only from the client's side: it may subscribe to its own
 * queue and to the public topics, and send nothing. Screens act through the REST API, where the
 * per-role rules live; a second way in would need a second copy of them.
 */
@Component
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserAppRepository users;

    public StompAuthenticationInterceptor(JwtService jwtService, UserAppRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            // Heartbeats and other frames without a command carry nothing to check.
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor));
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> throw new MessageDeliveryException(
                    "Les notifications sont en lecture seule : passez par l'API.");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK: nothing to guard.
            }
        }
        return message;
    }

    private SocketPrincipal authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Jeton d'acces manquant.");
        }
        Authentication authentication;
        try {
            authentication = jwtService.authenticationOf(header.substring(BEARER.length()).trim());
        } catch (JwtException e) {
            throw new MessageDeliveryException("Jeton d'acces invalide ou expire.");
        }
        if (!(authentication.getPrincipal() instanceof Jwt token)) {
            throw new MessageDeliveryException("Jeton d'acces invalide.");
        }
        long userId = JwtService.userIdOf(token);
        boolean active = users.findById(userId).map(UserApp::isEnabled).orElse(false);
        if (!active) {
            throw new MessageDeliveryException("Ce compte n'existe plus ou a ete desactive.");
        }
        return new SocketPrincipal(userId, rolesOf(authentication));
    }

    /**
     * A connection may listen to its own queue -- the user prefix is resolved against its
     * principal by the broker, so nobody can read another account's -- and to the topics every
     * signed-in account shares. Anything else is refused.
     */
    private static void checkSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof SocketPrincipal)) {
            throw new MessageDeliveryException("Connexion non authentifiee.");
        }
        String destination = accessor.getDestination();
        boolean allowed = destination != null
                && (destination.startsWith(NotificationService.USER_PREFIX + "/")
                || destination.startsWith(NotificationService.TOPIC_PREFIX + "/"));
        if (!allowed) {
            throw new MessageDeliveryException("Abonnement refuse : " + destination);
        }
    }

    private static Set<RoleApp> rolesOf(Authentication authentication) {
        Set<String> names = EnumSet.allOf(RoleApp.class).stream()
                .map(Enum::name).collect(Collectors.toSet());
        Set<RoleApp> roles = EnumSet.noneOf(RoleApp.class);
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && name.startsWith("ROLE_") && names.contains(name.substring(5))) {
                roles.add(RoleApp.valueOf(name.substring(5)));
            }
        }
        return roles;
    }
}
