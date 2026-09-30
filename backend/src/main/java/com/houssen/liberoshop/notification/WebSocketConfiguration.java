package com.houssen.liberoshop.notification;

import com.houssen.liberoshop.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over a plain WebSocket at {@value #ENDPOINT}.
 *
 * <p>The broker is Spring's in-memory one: right for a shop running a single instance, and
 * replaceable by a relay to RabbitMQ or ActiveMQ ({@code enableStompBrokerRelay}) the day there
 * are several, without touching {@link NotificationService} or the client.
 *
 * <p>No SockJS fallback. Every browser this application supports speaks WebSocket, and the
 * fallback would add a second transport to secure for none of them.
 *
 * <p>Origins: the same ones the REST API accepts -- the application's own, the mobile app's web
 * view, and {@code ng serve} in development.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";

    /**
     * Server and client each promise a frame at least this often. Long enough to cost nothing,
     * short enough that a proxy with a one-minute idle timeout never closes a quiet connection,
     * and that a dead one is noticed within the minute.
     */
    private static final long HEARTBEAT_MS = 20_000;

    private final StompAuthenticationInterceptor authentication;
    private final SecurityProperties security;
    /**
     * The scheduler the messaging configuration already declares, reused for heartbeats rather
     * than a second one this class would have to shut down. Lazy because that bean is defined
     * by the very configuration this class contributes to.
     */
    private final TaskScheduler brokerScheduler;

    public WebSocketConfiguration(StompAuthenticationInterceptor authentication,
                                  SecurityProperties security,
                                  @Lazy @Qualifier("messageBrokerTaskScheduler")
                                  TaskScheduler brokerScheduler) {
        this.authentication = authentication;
        this.security = security;
        this.brokerScheduler = brokerScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(security.crossOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(NotificationService.TOPIC_PREFIX, "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(brokerScheduler);
        registry.setUserDestinationPrefix(NotificationService.USER_PREFIX);
        // Nothing is routed to application handlers: the interceptor refuses every SEND.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authentication);
    }
}
