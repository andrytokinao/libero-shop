package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import com.houssen.liberoshop.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The door of the WebSocket: nothing reaches the broker without a valid token. */
class StompAuthenticationInterceptorTest {

    private JwtService jwtService;
    private UserAppRepository users;
    private StompAuthenticationInterceptor interceptor;
    private final MessageChannel channel = Mockito.mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        users = Mockito.mock(UserAppRepository.class);
        interceptor = new StompAuthenticationInterceptor(jwtService, users);

        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256").claim("uid", 7).subject("joseph").build();
        Mockito.when(jwtService.authenticationOf("good")).thenReturn(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_DEPOT_AGENT"),
                        new SimpleGrantedAuthority("ROLE_CASHIER"))));
        Mockito.when(jwtService.authenticationOf("forged")).thenThrow(new BadJwtException("bad signature"));
        Mockito.when(users.findById(7L)).thenReturn(Optional.of(
                UserApp.builder().id(7L).fullName("Joseph").username("joseph").enabled(true).build()));
    }

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination,
                                         SocketPrincipal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("a valid token on CONNECT names the connection by account id, with its roles")
    void connects() {
        Message<?> out = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        SocketPrincipal principal = assertInstanceOf(SocketPrincipal.class, accessor.getUser());
        assertEquals("7", principal.getName());
        assertEquals(Set.of(RoleApp.DEPOT_AGENT, RoleApp.CASHIER), principal.roles());
    }

    @Test
    @DisplayName("refuses a CONNECT without a token, with a forged one, or for a disabled account")
    void refusesStrangers() {
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer forged", null, null), channel));

        Mockito.when(users.findById(7L)).thenReturn(Optional.of(
                UserApp.builder().id(7L).fullName("Joseph").username("joseph").enabled(false).build()));
        assertThrows(MessageDeliveryException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel));
    }

    @Test
    @DisplayName("lets a connection listen to its own queue and to topics, and to nothing else")
    void subscriptions() {
        SocketPrincipal joseph = new SocketPrincipal(7L, Set.of(RoleApp.DEPOT_AGENT));

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", joseph), channel);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/topic/stock", joseph), channel);

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/queue/notifications-user3", joseph), channel));
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/topic/stock", null), channel));
    }

    @Test
    @DisplayName("refuses anything a browser tries to send: screens act through the API")
    void readOnly() {
        SocketPrincipal joseph = new SocketPrincipal(7L, Set.of(RoleApp.DEPOT_AGENT));

        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(
                frame(StompCommand.SEND, null, "/app/anything", joseph), channel));
    }
}
