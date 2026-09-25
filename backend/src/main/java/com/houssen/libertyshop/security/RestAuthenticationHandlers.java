package com.houssen.libertyshop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Turns "not logged in" and "not allowed" into JSON instead of a redirect to a login page.
 *
 * <p>An Angular client that receives a 302 to an HTML page cannot tell a session timeout
 * from a successful call; a 401 with a stable {@code code} gives the interceptor exactly
 * one thing to branch on.
 */
@Component
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /**
     * Boot 4 ships Jackson 3, whose JSON mapper bean is a {@code JsonMapper}. Asking for
     * that exact type rather than the {@code ObjectMapper} supertype keeps the injection
     * unambiguous if a CBOR or XML mapper is ever added to the classpath.
     */
    private final JsonMapper jsonMapper;

    public RestAuthenticationHandlers(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Votre session a expire. Merci de vous reconnecter.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Votre role ne vous autorise pas a effectuer cette action.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(),
                Map.of("code", code, "message", message, "timestamp", Instant.now().toString()));
    }
}
